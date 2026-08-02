package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.MediaSearchItemResponse;
import com.ge.bo.dto.MediaSearchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FO 통합 미디어 검색 서비스 — 4개 소스를 "단일" 네이티브 쿼리(CTE + UNION ALL)로 합쳐 키워드/소스필터/페이징한다.
 *  ① Tech Hub 영상(contents_master + LATERAL contents_version) — 게이트는 {@link TechHubService#MASTER_GATE} 그대로 재사용.
 *     contents_master 에는 site_id 컬럼이 없으므로 사이트 필터 미적용(전역 노출).
 *  ② Blog / Press / Article 게시글 — 오직 integration_contents 테이블만 참조(type 'B'=BLOG, 'P'=PRESS, 'A'=ARTICLE).
 *     노출조건 is_visible = true, 사이트 스코프는 (site_id = :siteId OR site_id IS NULL).
 *
 * 쿼리 1회로 "현재 페이지 목록 + 소스별 건수(sourceCounts)"를 동시에 얻는다.
 *  - base : 키워드(q)만 적용, 소스 필터는 적용하지 않은 전체 후보 집합
 *  - agg  : base 위에서 소스별 count(*) FILTER 집계 → sourceCounts 가 소스 선택과 무관하게 유지되어야 FE 필터 UI 가 정상 동작
 *  - pg   : base 에 소스 필터 + 정렬 + LIMIT/OFFSET 적용(페이징은 반드시 이 CTE 안에서만)
 *  - 최종 SELECT 는 agg LEFT JOIN pg ON true — 결과가 0행이어도 counts 1행이 남는다(COUNT(*) OVER() 로 대체 불가).
 *
 * 이미지/링크/표시날짜는 SQL 이 아니라 Java 매핑단에서 조립한다.
 * (TECH_HUB=YouTube 썸네일, 그 외=page_file 프록시 URL / link=sourceType+id 로 FE 실라우트 조립)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SiteTimeZoneResolver siteTimeZoneResolver;

    // YouTube video id(11자) 추출 패턴 — watch?v= / youtu.be/ / embed/ / shorts/ 형태 모두 대응. 매칭 안 되면 썸네일 null.
    private static final Pattern YOUTUBE_ID =
        Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    // 본문 스니펫 최대 길이 — HTML 태그를 제거한 "평문" 기준 글자수(FE 카드가 2줄 말줄임이라 200자면 충분).
    // 0 이하이면 캡만 해제되어 평문 본문 전체가 내려간다(태그 제거는 캡과 무관하게 항상 적용).
    private static final int SNIPPET_MAX_CHARS = 200;

    // sourceCounts 키 순서 고정 — 0건 소스도 0으로 항상 포함해야 FE 필터에 "Article (0)" 표시가 가능하다.
    private static final List<String> SOURCE_KEYS = List.of("TECH_HUB", "BLOG", "PRESS", "ARTICLE");

    // 표시용 날짜 포맷(사이트 timezone 기준으로 Java 에서 포맷)
    private static final DateTimeFormatter SORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 통합 미디어 검색.
     *
     * @param q       키워드(원본 사용자 입력). null/blank 이면 LIKE 절 미적용(전체 노출).
     * @param sources 소스 필터 CSV(TECH_HUB,BLOG,PRESS,ARTICLE). null/blank 이면 4개 전체.
     * @param page    0-based 페이지
     * @param size    페이지 크기(기본 20)
     * @param siteId  사이트 스코프(X-Site-Id, null 허용) — integration_contents 블록에만 (site_id=:siteId OR site_id IS NULL) 적용.
     */
    @Transactional(readOnly = true)
    public MediaSearchResponse search(String q, String sources, int page, int size, Long siteId) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();
        // 소스 필터 해석
        Set<String> tokens = parseSources(sources);

        // 아무 소스도 매칭 안 되면 즉시 빈 결과(빈 IN 방지) — 이때 counts 는 전부 0
        if (tokens.isEmpty()) {
            return new MediaSearchResponse(Collections.emptyList(), 0L, 0, safePage, safeSize, emptyCounts());
        }

        // LIKE 와일드카드 이스케이프 / HTML 엔티티 인코딩은 공용 규칙(SearchSqlSupport)을 사용한다.
        // kw     : 평문 컬럼(title 계열)용 — LIKE 이스케이프만
        // kwHtml : HTML 원문 컬럼(ic.content)용 — 엔티티 인코딩 후 LIKE 이스케이프
        String kw = null;
        String kwHtml = null;
        String kwExact = null;
        String kwPrefix = null;
        String kwRegex = null;
        if (hasKeyword) {
            String trimmed = q.trim();
            kw = SearchSqlSupport.toLikePattern(trimmed);
            kwHtml = SearchSqlSupport.toHtmlLikePattern(trimmed);
            kwExact = SearchSqlSupport.toLikeExactPattern(trimmed);
            kwPrefix = SearchSqlSupport.toLikePrefixPattern(trimmed);
            kwRegex = SearchSqlSupport.toWordStartRegex(trimmed);
        }

        boolean hasSite = siteId != null;

        // ── base : q 만 적용한 전체 후보(소스 필터 미적용) ──
        String base = buildTechHubBlock(hasKeyword)
                + " UNION ALL "
                + buildIntegrationBlock(hasKeyword, hasSite);

        // ── 단일 쿼리: agg(소스별 건수) LEFT JOIN pg(현재 페이지) ──
        String sql = "WITH base AS (" + base + "),"
            + " agg AS ("
            + "   SELECT count(*) FILTER (WHERE source_type='TECH_HUB') AS c_tech_hub,"
            + "          count(*) FILTER (WHERE source_type='BLOG')     AS c_blog,"
            + "          count(*) FILTER (WHERE source_type='PRESS')    AS c_press,"
            + "          count(*) FILTER (WHERE source_type='ARTICLE')  AS c_article"
            + "   FROM base"
            + " ),"
            + " pg AS ("
            + "   SELECT b.* FROM base b"
            + "   WHERE b.source_type IN (:sources)"
            + "   ORDER BY b.score DESC, b.sort_ts DESC NULLS LAST, b.source_type, b.row_id DESC"
            + "   LIMIT :size OFFSET :offset"
            + " )"
            + " SELECT a.c_tech_hub, a.c_blog, a.c_press, a.c_article,"
            + "        p.source_type, p.id, p.title, p.snippet, p.video_url, p.file_id, p.sort_ts"
            + " FROM agg a"
            + " LEFT JOIN pg p ON true"
            // 바깥 JOIN 이 순서를 재배열하므로 최종 SELECT 에서 ORDER BY 를 반드시 재기술한다.
            + " ORDER BY p.score DESC, p.sort_ts DESC NULLS LAST, p.source_type, p.row_id DESC";

        Query query = entityManager.createNativeQuery(sql);
        bindCommon(query, hasKeyword, kw, kwHtml, kwExact, kwPrefix, kwRegex,
                hasSite, siteId, tokens, safeSize, safePage);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // ① 소스별 건수 — agg 는 1행이므로 어느 행에서 읽어도 동일(0행 결과여도 LEFT JOIN 으로 1행은 남는다)
        Map<String, Long> sourceCounts = emptyCounts();
        if (!rows.isEmpty()) {
            Object[] head = rows.get(0);
            sourceCounts.put("TECH_HUB", toLong(head[0]));
            sourceCounts.put("BLOG", toLong(head[1]));
            sourceCounts.put("PRESS", toLong(head[2]));
            sourceCounts.put("ARTICLE", toLong(head[3]));
        }

        // ② 현재 페이지 목록
        ZoneId zone = siteTimeZoneResolver.resolve(siteId);
        List<MediaSearchItemResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            // 결과 0행 케이스: counts 만 담긴 행이 오므로 source_type 이 null 이면 목록에서 제외
            if (r[4] == null) continue;

            String sourceType = r[4].toString();
            Long id = r[5] != null ? ((Number) r[5]).longValue() : null;
            String title = r[6] != null ? r[6].toString() : null;
            String snippet = r[7] != null ? r[7].toString() : null;
            String videoUrl = r[8] != null ? r[8].toString() : null;
            Long fileId = r[9] != null ? ((Number) r[9]).longValue() : null;
            String sortDate = formatSortDate(r[10], zone);

            String imageUrl = "TECH_HUB".equals(sourceType)
                    ? youtubeThumbnail(videoUrl)        // TECH_HUB: video_url → YouTube hqdefault
                    : resolveMediaProxyUrl(fileId);     // 그 외: page_file id → 프록시 URL

            content.add(new MediaSearchItemResponse(
                sourceType, id, title, snippet, imageUrl, sortDate, buildLink(sourceType, id)));
        }

        // ③ totalElements = 선택된 소스들의 건수 합(별도 count 쿼리 없음)
        long total = 0L;
        for (String token : tokens) {
            total += sourceCounts.getOrDefault(token, 0L);
        }
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new MediaSearchResponse(content, total, totalPages, safePage, safeSize, sourceCounts);
    }

    // ── 블록1: Tech Hub 영상(TechHubService.MASTER_GATE 그대로) ──
    // 소스 필터는 여기서 적용하지 않는다(base 는 항상 4개 소스 전체를 담아야 agg 가 올바르다).
    private String buildTechHubBlock(boolean hasKeyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT 'TECH_HUB'::text AS source_type,")
          .append("  m.id AS row_id,")
          .append("  m.id AS id,")
          .append("  COALESCE(m.nahp_title, m.doc_title)::text AS title,")
          .append("  NULL::text AS snippet,")
          .append("  rep.video_url::text AS video_url,")
          .append("  NULL::bigint AS file_id,")
          // timestamptz 원본 그대로 — 문자열 변환 시 TZ 에 따라 정렬이 틀어진다.
          .append("  m.source_updated_at AS sort_ts,");
        if (hasKeyword) {
            sb.append("  (CASE")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qExact  ESCAPE '\\' THEN 100")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qPrefix ESCAPE '\\' THEN 80")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ~* :qRegex THEN 60")
              .append("    ELSE 40 END)::int AS score");
        } else {
            sb.append("  0::int AS score");
        }
        sb.append(" FROM contents_master m")
          // 대표 버전(Chapter 1 = sort_key 최대)의 video_url — Java 에서 썸네일 조립용
          .append(" JOIN LATERAL (")
          .append("   SELECT v.video_url FROM contents_version v")
          .append("   WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false")
          .append("     AND v.video_url IS NOT NULL AND v.video_url <> ''")
          .append("   ORDER BY v.sort_key DESC LIMIT 1")
          .append(" ) rep ON true")
          .append(" WHERE").append(TechHubService.MASTER_GATE);
        if (hasKeyword) {
            // contents_master 제목은 평문(실데이터 확인: "Motion Controller & EtherCAT ..." 처럼 & 가 그대로) → raw q 사용.
            sb.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'");
        }
        return sb.toString();
    }

    // ── 블록2: integration_contents 의 BLOG / PRESS / ARTICLE ──
    // id 는 content_id(원본 page_data.id 문자열)에서 숫자만 추출해 bigint 로 변환 — FE 상세 라우트 키.
    private String buildIntegrationBlock(boolean hasKeyword, boolean hasSite) {
        // 스니펫: 평문화 → 캡 순서(캡을 먼저 하면 태그 중간이 잘려 FE 에 태그 조각이 그대로 노출된다).
        // 캡 토글 — SNIPPET_MAX_CHARS 가 0 이하이면 캡만 해제되고 평문화는 그대로 적용된다.
        // 캡 지점(200번째 글자)이 단어 사이 공백에 걸리면 끝에 공백이 남으므로 자른 뒤 한 번 더 btrim 한다.
        String plainExpr = SearchSqlSupport.buildPlainTextExpr("ic.content");
        String snippetExpr = SNIPPET_MAX_CHARS > 0
                ? "btrim(left(" + plainExpr + ", :snippetCap))::text"
                : plainExpr + "::text";

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT CASE ic.type WHEN 'B' THEN 'BLOG' WHEN 'P' THEN 'PRESS'")
          .append("                    WHEN 'A' THEN 'ARTICLE' END::text AS source_type,")
          .append("  ic.id AS row_id,")
          .append("  NULLIF(regexp_replace(ic.content_id,'[^0-9]','','g'),'')::bigint AS id,")
          .append("  ic.title::text AS title,")
          .append("  ").append(snippetExpr).append(" AS snippet,")
          .append("  NULL::text AS video_url,")
          .append("  ic.file_id AS file_id,")
          .append("  ic.updated_at AS sort_ts,");
        if (hasKeyword) {
            sb.append("  (CASE")
              .append("    WHEN ic.title ILIKE :qExact  ESCAPE '\\' THEN 100")
              .append("    WHEN ic.title ILIKE :qPrefix ESCAPE '\\' THEN 80")
              .append("    WHEN ic.title ~* :qRegex THEN 60")
              .append("    WHEN ic.title ILIKE :q ESCAPE '\\' THEN 40")
              .append("    ELSE 10 END)::int AS score");
        } else {
            sb.append("  0::int AS score");
        }
        sb.append(" FROM integration_contents ic")
          .append(" WHERE ic.is_visible = true AND ic.type IN ('B','P','A')");
        if (hasSite) {
            sb.append(" AND (ic.site_id = :siteId OR ic.site_id IS NULL)");
        }
        if (hasKeyword) {
            // ic.title 은 평문(실데이터 확인: "Powering Smart & Efficient..." 처럼 & 가 그대로 저장) → raw q 사용.
            // ic.content 는 HTML 원문(& 가 &amp; 로 이스케이프되어 저장) → 엔티티 인코딩된 q 사용.
            sb.append(" AND (ic.title ILIKE :q ESCAPE '\\' OR ic.content ILIKE :qHtml ESCAPE '\\')");
        }
        return sb.toString();
    }

    // 공통 파라미터 바인딩
    private void bindCommon(Query query, boolean hasKeyword, String kw, String kwHtml,
                            String kwExact, String kwPrefix, String kwRegex,
                            boolean hasSite, Long siteId, Set<String> tokens,
                            int safeSize, int safePage) {
        if (hasKeyword) {
            query.setParameter("q", kw);          // 평문 컬럼용(title 계열)
            query.setParameter("qHtml", kwHtml);  // HTML 원문 컬럼용(ic.content)
            query.setParameter("qExact", kwExact);
            query.setParameter("qPrefix", kwPrefix);
            query.setParameter("qRegex", kwRegex);
        }
        if (hasSite) query.setParameter("siteId", siteId);
        if (SNIPPET_MAX_CHARS > 0) query.setParameter("snippetCap", SNIPPET_MAX_CHARS);
        query.setParameter("sources", new ArrayList<>(tokens));
        query.setParameter("size", safeSize);
        query.setParameter("offset", safePage * safeSize);
    }

    /** sources CSV → 대문자 토큰 집합. null/blank 이면 4개 전체. 미인식 토큰은 무시. */
    private Set<String> parseSources(String sources) {
        Set<String> all = new LinkedHashSet<>(List.of("TECH_HUB", "BLOG", "PRESS", "ARTICLE"));
        if (sources == null || sources.isBlank()) {
            return all;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : sources.split(",")) {
            String tok = raw.trim().toUpperCase();
            if (all.contains(tok)) result.add(tok);
        }
        return result;
    }

    /** video_url 에서 YouTube video id 추출 → hqdefault 썸네일. YouTube 아니면 null. */
    private String youtubeThumbnail(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return null;
        Matcher m = YOUTUBE_ID.matcher(videoUrl);
        if (m.find()) {
            return "https://img.youtube.com/vi/" + m.group(1) + "/hqdefault.jpg";
        }
        return null;
    }

    /** page_file.id → FO 범용 파일 서빙 프록시 URL. 기존 /api/v1/fo/page-files/{id} 재사용. */
    private String resolveMediaProxyUrl(Long fileId) {
        if (fileId == null) return null;
        return "/api/v1/fo/page-files/" + fileId;
    }

    /** sourceType+id → FE 실라우트 상세 링크(목업의 tech-hub/detail 아님). */
    private String buildLink(String sourceType, Long id) {
        if (sourceType == null || id == null) return null;
        return switch (sourceType) {
            case "TECH_HUB" -> "/support/tech-hub/view/" + id;
            case "BLOG" -> "/company/blog/detail/" + id;
            case "PRESS" -> "/company/press/detail/" + id;
            case "ARTICLE" -> "/company/articles/detail/" + id;
            default -> null;
        };
    }

    /** sourceCounts 기본 맵 — 4개 키를 항상 0으로 채워 순서까지 고정한다. */
    private Map<String, Long> emptyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : SOURCE_KEYS) {
            counts.put(key, 0L);
        }
        return counts;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * sort_ts(timestamptz) → 사이트 timezone 기준 yyyy-MM-dd 문자열.
     * SQL to_char 는 DB 세션 TimeZone 에 의존해 사이트별 날짜가 틀어지므로 Java 에서 포맷한다.
     */
    private String formatSortDate(Object value, ZoneId zone) {
        if (value == null) return null;
        Instant instant;
        if (value instanceof java.sql.Timestamp ts) {
            instant = ts.toInstant();
        } else if (value instanceof OffsetDateTime odt) {
            instant = odt.toInstant();
        } else if (value instanceof Instant i) {
            instant = i;
        } else if (value instanceof java.util.Date d) {
            instant = d.toInstant();
        } else if (value instanceof LocalDateTime ldt) {
            // TZ 정보가 없는 값은 변환 없이 날짜 부분만 사용
            return ldt.toLocalDate().format(SORT_DATE_FMT);
        } else {
            return value.toString();
        }
        return SORT_DATE_FMT.format(instant.atZone(zone));
    }
}

package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.dto.MediaSearchItemResponse;
import com.ge.bo.dto.MediaSearchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FO 통합 미디어 검색 서비스 — 4개 소스를 단일 네이티브 UNION ALL 쿼리로 합쳐 키워드/소스필터/페이징한다.
 *  ① Tech Hub 영상(contents_master, doc_type='V') — 게이트는 {@link TechHubService#MASTER_GATE} 그대로 재사용.
 *  ② page_data blog/press/articles 게시글 — 컨테이너 접근/게시상태(is_visible=001)/예약글 차단(publish_dttm<=today)/
 *     사이트 스코프/미디어 프록시 URL/LIKE 이스케이프 규칙은 {@link PageDataService}(findProductInsights·searchProducts) 패턴을 그대로 따른다.
 *
 * 정렬은 바깥에서 sort_date DESC NULLS LAST, id DESC. 이미지/링크는 SQL 이 아니라 Java 매핑단에서 조립한다.
 * (TECH_HUB=YouTube 썸네일, page_data=프록시 URL / link=sourceType+id 로 FE 실라우트 조립)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SiteTimeZoneResolver siteTimeZoneResolver;

    // page_data 컨테이너 접근 — 섹션명 = data_slug 에서 '-data' 제거(blog/press/articles). PageDataService.findProductInsights 패턴 재사용.
    private static final String SECTION = "data_json->(replace(data_slug,'-data',''))";

    // YouTube video id(11자) 추출 패턴 — watch?v= / youtu.be/ / embed/ / shorts/ 형태 모두 대응. 매칭 안 되면 썸네일 null.
    private static final Pattern YOUTUBE_ID =
        Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    // sources 토큰 → page_data data_slug 매핑(TECH_HUB 은 slug 아님 — 별도 플래그로 처리)
    private static final String SLUG_BLOG = "blog-data";
    private static final String SLUG_PRESS = "press-data";
    private static final String SLUG_ARTICLE = "articles-data";

    /**
     * 통합 미디어 검색.
     *
     * @param q       키워드(원본 사용자 입력). null/blank 이면 LIKE 절 미적용(전체 노출).
     * @param sources 소스 필터 CSV(TECH_HUB,BLOG,PRESS,ARTICLE). null/blank 이면 4개 전체.
     * @param page    0-based 페이지
     * @param size    페이지 크기(기본 20)
     * @param siteId  사이트 스코프(X-Site-Id, null 허용) — page_data 블록에만 (site_id=:siteId OR site_id IS NULL) 적용.
     */
    @Transactional(readOnly = true)
    public MediaSearchResponse search(String q, String sources, int page, int size, Long siteId) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();
        // 소스 필터 해석
        Set<String> tokens = parseSources(sources);
        boolean hasTechHub = tokens.contains("TECH_HUB");
        List<String> selectedSlugs = new ArrayList<>();
        if (tokens.contains("BLOG")) selectedSlugs.add(SLUG_BLOG);
        if (tokens.contains("PRESS")) selectedSlugs.add(SLUG_PRESS);
        if (tokens.contains("ARTICLE")) selectedSlugs.add(SLUG_ARTICLE);

        // 아무 소스도 매칭 안 되면 즉시 빈 결과(빈 IN 방지)
        if (!hasTechHub && selectedSlugs.isEmpty()) {
            return new MediaSearchResponse(Collections.emptyList(), 0L, 0, safePage, safeSize);
        }

        // LIKE 와일드카드 이스케이프 — searchProducts 와 동일 규칙(\ 먼저, 그다음 % _)
        String kw = null;
        if (hasKeyword) {
            String escaped = q.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            kw = "%" + escaped + "%";
        }

        // ── UNION ALL 동적 조립 ──
        List<String> blocks = new ArrayList<>();
        if (hasTechHub) blocks.add(buildTechHubBlock(hasKeyword));
        if (!selectedSlugs.isEmpty()) blocks.add(buildPageDataBlock(hasKeyword, siteId != null));
        String union = String.join(" UNION ALL ", blocks);

        boolean needToday = !selectedSlugs.isEmpty();          // page_data 블록만 :today 사용
        boolean needSite = !selectedSlugs.isEmpty() && siteId != null;

        // ① 전체 건수
        String countSql = "SELECT COUNT(*) FROM (" + union + ") t";
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindCommon(countQuery, hasKeyword, kw, needToday, siteId, needSite, !selectedSlugs.isEmpty(), selectedSlugs);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        if (total == 0) {
            return new MediaSearchResponse(Collections.emptyList(), 0L, 0, safePage, safeSize);
        }

        // ② 현재 페이지 — sort_date DESC NULLS LAST, id DESC
        String dataSql = "SELECT t.source_type, t.id, t.title, t.snippet, t.video_url, t.image_media_id, t.sort_date"
            + " FROM (" + union + ") t"
            + " ORDER BY t.sort_date DESC NULLS LAST, t.id DESC"
            + " LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindCommon(dataQuery, hasKeyword, kw, needToday, siteId, needSite, !selectedSlugs.isEmpty(), selectedSlugs);
        dataQuery.setParameter("size", safeSize);
        dataQuery.setParameter("offset", safePage * safeSize);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<MediaSearchItemResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            String sourceType = r[0] != null ? r[0].toString() : null;
            Long id = r[1] != null ? ((Number) r[1]).longValue() : null;
            String title = r[2] != null ? r[2].toString() : null;
            String snippet = r[3] != null ? r[3].toString() : null;
            String videoUrl = r[4] != null ? r[4].toString() : null;
            Object imageMediaId = r[5];
            String sortDate = r[6] != null ? r[6].toString() : null;

            String imageUrl = "TECH_HUB".equals(sourceType)
                    ? youtubeThumbnail(videoUrl)      // TECH_HUB: video_url → YouTube hqdefault
                    : resolveMediaProxyUrl(imageMediaId); // page_data: 미디어 ID → 프록시 URL

            content.add(new MediaSearchItemResponse(
                sourceType, id, title, snippet, imageUrl, sortDate, buildLink(sourceType, id)));
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new MediaSearchResponse(content, total, totalPages, safePage, safeSize);
    }

    // ── 블록1: Tech Hub 영상(TechHubService.MASTER_GATE 그대로) ──
    private String buildTechHubBlock(boolean hasKeyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT 'TECH_HUB' AS source_type,")
          .append("  m.id AS id,")
          .append("  COALESCE(m.nahp_title, m.doc_title) AS title,")
          .append("  NULL::text AS snippet,")
          .append("  rep.video_url AS video_url,")
          .append("  NULL::text AS image_media_id,")
          .append("  to_char(m.source_updated_at, 'YYYY-MM-DD') AS sort_date")
          .append(" FROM contents_master m")
          // 대표 버전(Chapter 1 = sort_key 최대)의 video_url — Java 에서 썸네일 조립용
          .append(" JOIN LATERAL (")
          .append("   SELECT v.video_url FROM contents_version v")
          .append("   WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false")
          .append("     AND v.video_url IS NOT NULL AND v.video_url <> ''")
          .append("   ORDER BY v.sort_key DESC LIMIT 1")
          .append(" ) rep ON true")
          .append(" WHERE").append(TechHubService.MASTER_GATE);
        if (hasKeyword) {
            sb.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'");
        }
        return sb.toString();
    }

    // ── 블록2: page_data blog/press/articles ──
    private String buildPageDataBlock(boolean hasKeyword, boolean hasSite) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT")
          .append("  CASE data_slug WHEN 'blog-data' THEN 'BLOG' WHEN 'press-data' THEN 'PRESS'")
          .append("       WHEN 'articles-data' THEN 'ARTICLE' END AS source_type,")
          .append("  id AS id,")
          .append("  ").append(SECTION).append("->>'title' AS title,")
          // 본문(HTML raw)은 크므로 앞 500자만 캡해서 내림(FE 가 stripHtml)
          .append("  left(").append(SECTION).append("->>'content', 500) AS snippet,")
          .append("  NULL::text AS video_url,")
          .append("  ").append(SECTION).append("->'image'->>0 AS image_media_id,")
          .append("  ").append(SECTION).append("->>'publish_dttm' AS sort_date")
          .append(" FROM page_data")
          .append(" WHERE data_slug IN (:selectedSlugs)")
          .append("  AND ").append(SECTION).append("->>'is_visible' = '001'")
          // 예약글 차단 — publish_dttm 숫자만 뽑아 앞 8자리(YYYYMMDD) <= today
          .append("  AND substring(regexp_replace(").append(SECTION)
          .append("->>'publish_dttm', '[^0-9]', '', 'g'), 1, 8) <= :today");
        if (hasSite) {
            sb.append("  AND (site_id = :siteId OR site_id IS NULL)");
        }
        if (hasKeyword) {
            sb.append("  AND (").append(SECTION).append("->>'title' ILIKE :q ESCAPE '\\'")
              .append("    OR ").append(SECTION).append("->>'content' ILIKE :q ESCAPE '\\')");
        }
        return sb.toString();
    }

    // 공통 파라미터 바인딩(count/data 동일 union 이라 동일 바인딩)
    private void bindCommon(Query query, boolean hasKeyword, String kw,
                            boolean needToday, Long siteId, boolean needSite,
                            boolean hasPageData, List<String> selectedSlugs) {
        if (hasKeyword) query.setParameter("q", kw);
        if (hasPageData) query.setParameter("selectedSlugs", selectedSlugs);
        if (needToday) query.setParameter("today", resolveTodayParam(siteId));
        if (needSite) query.setParameter("siteId", siteId);
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

    /** 미디어 ID 스칼라 → FO 프록시 URL(숫자면 /api/v1/fo/page-files/{id}, 아니면 null). PageDataService.resolveMediaProxyUrl 규칙. */
    private String resolveMediaProxyUrl(Object mediaIdValue) {
        if (mediaIdValue == null) return null;
        try {
            long mediaId = Long.parseLong(mediaIdValue.toString().trim());
            return "/api/v1/fo/page-files/" + mediaId;
        } catch (NumberFormatException e) {
            return null;
        }
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

    /** "오늘"(YYYYMMDD) — 사이트(X-Site-Id) timezone 우선, 없으면 서버 기본 zone. PageDataService.resolveTodayParam 규칙. */
    private String resolveTodayParam(Long siteId) {
        ZoneId zone = siteTimeZoneResolver.resolve(siteId);
        return LocalDate.now(zone).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}

package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.PageSearchItemResponse;
import com.ge.bo.dto.PageSearchResponse;
import com.ge.bo.entity.CodeDetail;
import com.ge.bo.repository.CodeDetailRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FO 통합검색 Pages 탭 서비스 — 관리자 기능 "검색관리"(search_manage / search_manage_text)를 원천으로 한다.
 *
 * <p>데이터 성격
 *  <ul>
 *    <li>search_manage = 검색결과에 노출할 페이지 1건(url + 사용여부). search_manage_text = 그 페이지를 찾게 해 줄
 *        검색용 텍스트(선택 제목 title + 필수 본문 text) N건. 즉 1:N 이다.</li>
 *    <li>text/title 은 BO 관리화면의 일반 input/textarea 로 입력되는 <b>평문</b>이다(HTML 아님).
 *        그래서 Media 처럼 HTML 엔티티 인코딩({@code qHtml})이나 태그 제거를 하지 않는다.
 *        실데이터로도 확인함(text 값: "1", "2", "213123213", "222").</li>
 *    <li>site 스코프 컬럼이 없어 X-Site-Id 는 사용하지 않는다.</li>
 *  </ul>
 * </p>
 *
 * <p>쿼리 구조
 *  <ul>
 *    <li>Media 의 CTE 3단(base/agg/pg)은 sourceCounts 를 0행일 때도 남기기 위한 장치였고 Pages 엔 불필요하다.
 *        대신 1:N 을 URL 1행으로 줄이는 <b>중복 제거</b>가 핵심이라 서브쿼리 1단 + ROW_NUMBER 를 쓴다.</li>
 *    <li>중복 제거: {@code ROW_NUMBER() OVER (PARTITION BY m.url ORDER BY (t.title IS NULL), t.created_at DESC, t.id DESC)}
 *        → 같은 URL 의 매칭 텍스트 중 <b>title 이 있는 것 우선</b>, 그다음 최신 등록 순으로 대표 1건을 고른다.
 *        PARTITION 키를 m.id 가 아니라 m.url 로 둬야 서로 다른 관리행이 같은 url 을 가리켜도 1건으로 합쳐진다.</li>
 *    <li>전체 건수는 중복 제거가 끝난 바깥 쿼리에서 {@code count(*) OVER ()} 로 센다.
 *        안쪽에서 세면 "텍스트 건수"가 잡혀 totalElements 가 부풀려진다.</li>
 *    <li>INNER JOIN 이라 검색텍스트가 0건인 페이지는 노출되지 않는다.
 *        어떤 키워드로도 찾을 수 없는 페이지이므로 q 유무와 무관하게 결과 집합을 일관되게 유지한 것.</li>
 *    <li>분류(page_section) — search_manage.page_section 은 code_detail(group_code='PAGE_SECTION')을
 *        FK 없이 얕은 참조하는 코드값 문자열이다(DownloadCenterService 의 doc_type 패턴과 동일).
 *        목록에는 LEFT JOIN 으로 표시명(sectionName)을 붙이고, sections 파라미터로 필터링하며,
 *        sectionCounts 로 분류별 건수(키워드만 적용, sections 필터는 미적용)를 함께 내려준다.</li>
 *  </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CodeDetailRepository codeDetailRepository;

    // 스니펫(=검색텍스트 본문) 최대 길이. 평문이라 태그 제거 없이 단순 캡만 한다. Media 와 동일 값.
    private static final int SNIPPET_MAX_CHARS = 200;

    // 분류 공통코드 그룹 — code_group.group_code. sectionCounts 의 키 목록/순서는 여기 속한
    // 활성(is_active=true) code_detail 을 sort_order 순으로 조회해 만든다(하드코딩 금지).
    private static final String PAGE_SECTION_GROUP_CODE = "PAGE_SECTION";

    /**
     * Pages 검색.
     *
     * @param q        키워드(원본 사용자 입력). null/blank 이면 LIKE 절 미적용(활성 페이지 전체 노출).
     * @param sections 분류 필터 CSV(예 "MARKETS,SERVICE"). null/blank 이면 필터 미적용(전체).
     *                 Media 의 sources 파라미터와 동일한 CSV 스타일.
     * @param page     0-based 페이지(음수면 0으로 보정)
     * @param size     페이지 크기(0 이하이면 10으로 보정)
     */
    @Transactional(readOnly = true)
    public PageSearchResponse search(String q, String sections, int page, int size) {
        int safeSize = size <= 0 ? 10 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();

        // 키워드 패턴 — 공용 규칙(SearchSqlSupport.toLikePattern) 재사용.
        // 대상 컬럼(title/text)이 모두 평문이므로 raw q 만 쓴다(HTML 인코딩 변형 미사용).
        String kw = hasKeyword ? SearchSqlSupport.toLikePattern(q.trim()) : null;
        String kwExact = hasKeyword ? SearchSqlSupport.toLikeExactPattern(q.trim()) : null;
        String kwPrefix = hasKeyword ? SearchSqlSupport.toLikePrefixPattern(q.trim()) : null;
        String kwRegex = hasKeyword ? SearchSqlSupport.toWordStartRegex(q.trim()) : null;

        // 분류 필터 파싱 — 미지정/빈 값이면 필터 없이 전체 노출.
        Set<String> sectionTokens = parseSections(sections);
        boolean hasSections = !sectionTokens.isEmpty();

        // ① 목록 + 전체건수(sections 필터 반영) — 단일 네이티브 쿼리
        Query query = entityManager.createNativeQuery(buildSql(hasKeyword, hasSections));
        if (hasKeyword) {
            query.setParameter("q", kw);
            query.setParameter("qExact", kwExact);
            query.setParameter("qPrefix", kwPrefix);
            query.setParameter("qRegex", kwRegex);
        }
        if (hasSections) {
            query.setParameter("sections", new ArrayList<>(sectionTokens));
        }
        if (SNIPPET_MAX_CHARS > 0) {
            query.setParameter("snippetCap", SNIPPET_MAX_CHARS);
        }
        query.setParameter("size", safeSize);
        query.setParameter("offset", safePage * safeSize);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<PageSearchItemResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = r[0] != null ? ((Number) r[0]).longValue() : null;
            String url = r[1] != null ? r[1].toString() : null;
            String title = r[2] != null ? r[2].toString() : null;
            String snippet = r[3] != null ? r[3].toString() : null;
            String section = r[4] != null ? r[4].toString() : null;
            String sectionName = r[5] != null ? r[5].toString() : null;

            // [임시 폴백] title 컬럼이 뒤늦게 추가돼 기존 데이터는 전부 NULL 이다.
            // 그대로 내리면 FO 카드 제목이 빈칸이 되므로 url 로 대체한다.
            // → 운영 정책 확정 시(예: title 없는 행은 아예 제외) 이 한 줄만 바꾸면 된다.
            if (title == null || title.isBlank()) {
                title = url;
            }

            content.add(new PageSearchItemResponse(id, url, title, snippet, section, sectionName));
        }

        // totalElements = 중복 제거(+sections 필터) 후 바깥 쿼리의 count(*) OVER (). 결과가 0행이면 전체도 0건이다.
        long total = rows.isEmpty() ? 0L : toLong(rows.get(0)[6]);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        // ② 분류별 건수(sectionCounts) — 키워드만 적용(sections 필터 미적용), URL 중복 제거 후 GROUP BY
        Map<String, Long> sectionCounts = buildSectionCounts(hasKeyword, kw);

        return new PageSearchResponse(content, total, totalPages, safePage, safeSize, sectionCounts);
    }

    /** URL 중복 제거 + 분류 표시명 조인 + sections 필터 + 목록 + 전체건수 단일 네이티브 쿼리. */
    private String buildSql(boolean hasKeyword, boolean hasSections) {
        // 스니펫: 평문이라 태그 제거 불필요. 길이 캡만 적용하고 잘린 끝의 공백을 정리한다.
        String snippetExpr = SNIPPET_MAX_CHARS > 0
                ? "btrim(left(t.text, :snippetCap))::text"
                : "t.text::text";

        StringBuilder inner = new StringBuilder();
        inner.append("SELECT m.id AS id,")
             .append("  m.url::text AS url,")
             .append("  t.title::text AS title,")
             .append("  ").append(snippetExpr).append(" AS snippet,")
             .append("  m.updated_at AS sort_at,")
             .append("  m.page_section::text AS page_section,")
             .append("  cd.name::text AS section_name,")
             // URL 단위 대표행 선정: title 보유 행 우선((t.title IS NULL) 이 false=0 → 앞으로), 그다음 최신 등록순.
             .append("  ROW_NUMBER() OVER (PARTITION BY m.url")
             .append("    ORDER BY (t.title IS NULL), t.created_at DESC, t.id DESC) AS rn,");
        if (hasKeyword) {
            inner.append("  (CASE")
                 .append("    WHEN t.title ILIKE :qExact  ESCAPE '\\' THEN 100")
                 .append("    WHEN t.title ILIKE :qPrefix ESCAPE '\\' THEN 80")
                 .append("    WHEN t.title ~* :qRegex THEN 60")
                 .append("    WHEN t.title ILIKE :q ESCAPE '\\' THEN 40")
                 .append("    ELSE 10 END)::int AS score");
        } else {
            inner.append("  0::int AS score");
        }
        inner.append(" FROM search_manage m")
             .append(" JOIN search_manage_text t ON t.search_manage_id = m.id")
             // 분류 표시명 — FK 없는 얕은 참조(DownloadCenterService doc_type 패턴과 동일). 매칭 실패해도 NULL 로 방어.
             .append(" LEFT JOIN code_detail cd")
             .append("        ON cd.code = m.page_section")
             .append("       AND cd.is_active = true")
             .append("       AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '")
             .append(PAGE_SECTION_GROUP_CODE).append("')")
             .append(" WHERE m.is_active = true");
        if (hasKeyword) {
            // title/text 모두 평문 → raw q 하나로 매칭. ILIKE 로 대소문자 무시.
            inner.append(" AND (t.title ILIKE :q ESCAPE '\\' OR t.text ILIKE :q ESCAPE '\\')");
        }
        if (hasSections) {
            inner.append(" AND m.page_section IN (:sections)");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT x.id, x.url, x.title, x.snippet, x.page_section, x.section_name,")
          // 중복 제거(rn=1) + sections 필터 이후에 세야 목록과 일치하는 건수가 된다.
          .append("  count(*) OVER () AS total_count")
          .append(" FROM (").append(inner).append(") x")
          .append(" WHERE x.rn = 1")
          .append(" ORDER BY x.score DESC, x.sort_at DESC, x.id DESC")
          .append(" LIMIT :size OFFSET :offset");
        return sb.toString();
    }

    /**
     * 분류별 건수(sectionCounts) 조회.
     * - 키워드(q)만 적용하고 sections 필터는 적용하지 않는다(선택 필터를 바꿔도 값이 흔들리지 않아야 함).
     * - 목록과 동일하게 URL 기준 중복 제거(ROW_NUMBER rn=1) 후 page_section 으로 GROUP BY 해야
     *   목록 합계와 어긋나지 않는다.
     * - 키 목록/순서는 PAGE_SECTION 그룹의 활성 코드를 sort_order 순으로 조회해 만들고(하드코딩 금지),
     *   0건인 분류도 0으로 채운다. page_section 이 NULL 인 행은 어느 키에도 매칭되지 않아 카운트에서 빠진다.
     */
    private Map<String, Long> buildSectionCounts(boolean hasKeyword, String kw) {
        // ① 키 목록/순서 — PAGE_SECTION 그룹의 활성 코드를 sort_order 순으로 조회해 0으로 초기화
        Map<String, Long> counts = new LinkedHashMap<>();
        List<CodeDetail> details =
            codeDetailRepository.findAllByGroup_GroupCodeAndActiveTrueOrderBySortOrderAsc(PAGE_SECTION_GROUP_CODE);
        for (CodeDetail detail : details) {
            counts.put(detail.getCode(), 0L);
        }

        // ② URL 중복 제거 후 page_section 별 집계(sections 필터 미적용)
        Query query = entityManager.createNativeQuery(buildSectionCountsSql(hasKeyword));
        if (hasKeyword) {
            query.setParameter("q", kw);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        for (Object[] r : rows) {
            if (r[0] == null) continue; // page_section NULL 인 그룹 — sectionCounts 에는 반영하지 않음
            String code = r[0].toString();
            if (counts.containsKey(code)) {
                counts.put(code, toLong(r[1]));
            }
        }
        return counts;
    }

    /** URL 중복 제거 후 page_section 별 GROUP BY 집계 쿼리(sections 필터 없음). */
    private String buildSectionCountsSql(boolean hasKeyword) {
        StringBuilder inner = new StringBuilder();
        inner.append("SELECT m.page_section::text AS page_section,")
             .append("  ROW_NUMBER() OVER (PARTITION BY m.url")
             .append("    ORDER BY (t.title IS NULL), t.created_at DESC, t.id DESC) AS rn")
             .append(" FROM search_manage m")
             .append(" JOIN search_manage_text t ON t.search_manage_id = m.id")
             .append(" WHERE m.is_active = true");
        if (hasKeyword) {
            inner.append(" AND (t.title ILIKE :q ESCAPE '\\' OR t.text ILIKE :q ESCAPE '\\')");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT x.page_section, count(*) AS cnt")
          .append(" FROM (").append(inner).append(") x")
          .append(" WHERE x.rn = 1")
          .append(" GROUP BY x.page_section");
        return sb.toString();
    }

    /** sections CSV → 대문자 토큰 집합(순서 보존). null/blank 이면 빈 집합(=필터 미적용). */
    private Set<String> parseSections(String sections) {
        Set<String> result = new LinkedHashSet<>();
        if (sections == null || sections.isBlank()) {
            return result;
        }
        for (String raw : sections.split(",")) {
            String tok = raw.trim().toUpperCase();
            if (!tok.isEmpty()) result.add(tok);
        }
        return result;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}

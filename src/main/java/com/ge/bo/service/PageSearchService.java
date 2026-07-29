package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.PageSearchItemResponse;
import com.ge.bo.dto.PageSearchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 통합검색 Pages 탭 서비스 — integration_contents 중 "type 이 없는(NULL/빈문자)" 행만 검색한다.
 *
 * <p>데이터 성격
 *  - type 'B'(BLOG)/'P'(PRESS)/'A'(ARTICLE) 는 {@link MediaSearchService} 담당이고, 그 외(type 미지정)가 Pages 대상이다.
 *  - 이 행들은 content_id 가 비어 있어 원본 page_data 를 역참조하지 않는다. 본 테이블의 title/content 만 사용한다.
 *  - 상세 링크(url)는 이번 라운드 응답 대상에서 제외.</p>
 *
 * <p>쿼리 구조
 *  - Media 와 달리 CTE 3단(base/agg/pg)이 필요 없다. Media 가 CTE 를 쓴 이유는 결과 0행일 때도
 *    sourceCounts 1행을 남겨야 했기 때문인데, Pages 는 0행이면 total 0 이 정답이라 그 제약이 없다.
 *    따라서 {@code COUNT(*) OVER ()} 를 얹은 단일 쿼리로 목록 + 전체건수를 동시에 얻는다.
 *  - 정렬은 updated_at DESC, id DESC. 동일 시각 데이터가 존재하므로 id tie-break 가 필수다
 *    (Media 와 동일 규약이며 기존 인덱스 정렬키와도 일치).</p>
 *
 * <p>키워드 매칭은 원문 컬럼 그대로 수행한다(WHERE 에 평문화 표현식을 쓰면 인덱스/일관성이 깨진다).
 * 평문화는 SELECT 절의 스니펫에만 적용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    // 본문 스니펫 최대 길이 — HTML 태그를 제거한 "평문" 기준 글자수. Media 와 동일 값.
    // 0 이하이면 캡만 해제되어 평문 본문 전체가 내려간다(태그 제거는 캡과 무관하게 항상 적용).
    private static final int SNIPPET_MAX_CHARS = 200;

    /**
     * Pages 검색.
     *
     * @param q      키워드(원본 사용자 입력). null/blank 이면 LIKE 절 미적용(전체 노출).
     * @param page   0-based 페이지(음수면 0으로 보정)
     * @param size   페이지 크기(0 이하이면 10으로 보정)
     * @param siteId 사이트 스코프(X-Site-Id, null 허용) — 있을 때만 (site_id=:siteId OR site_id IS NULL) 적용.
     */
    @Transactional(readOnly = true)
    public PageSearchResponse search(String q, int page, int size, Long siteId) {
        int safeSize = size <= 0 ? 10 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();
        boolean hasSite = siteId != null;

        // 키워드 패턴 — 공용 규칙(SearchSqlSupport) 사용. Media 와 완전히 동일하다.
        // kw     : 평문 컬럼(ic.title)용 — LIKE 이스케이프만
        // kwHtml : HTML 원문 컬럼(ic.content)용 — 엔티티 인코딩 후 LIKE 이스케이프
        String kw = null;
        String kwHtml = null;
        if (hasKeyword) {
            String trimmed = q.trim();
            kw = SearchSqlSupport.toLikePattern(trimmed);
            kwHtml = SearchSqlSupport.toHtmlLikePattern(trimmed);
        }

        Query query = entityManager.createNativeQuery(buildSql(hasKeyword, hasSite));
        if (hasKeyword) {
            query.setParameter("q", kw);
            query.setParameter("qHtml", kwHtml);
        }
        if (hasSite) {
            query.setParameter("siteId", siteId);
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
            String title = r[1] != null ? r[1].toString() : null;
            String snippet = r[2] != null ? r[2].toString() : null;
            content.add(new PageSearchItemResponse(id, title, snippet));
        }

        // totalElements = 첫 행의 count(*) OVER (). 결과가 0행이면 전체도 0건이다.
        long total = rows.isEmpty() ? 0L : toLong(rows.get(0)[3]);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new PageSearchResponse(content, total, totalPages, safePage, safeSize);
    }

    /** 목록 + 전체건수 단일 네이티브 쿼리. */
    private String buildSql(boolean hasKeyword, boolean hasSite) {
        // 스니펫: 평문화 → 캡 순서(캡을 먼저 하면 태그 중간이 잘려 FE 에 태그 조각이 그대로 노출된다).
        // 캡 지점이 단어 사이 공백에 걸리면 끝에 공백이 남으므로 자른 뒤 한 번 더 btrim 한다.
        String plainExpr = SearchSqlSupport.buildPlainTextExpr("ic.content");
        String snippetExpr = SNIPPET_MAX_CHARS > 0
                ? "btrim(left(" + plainExpr + ", :snippetCap))::text"
                : plainExpr + "::text";

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ic.id,")
          .append("  ic.title::text AS title,")
          .append("  ").append(snippetExpr).append(" AS snippet,")
          // 윈도우 함수로 페이징 전 전체 건수를 함께 가져온다(별도 count 쿼리 불필요).
          .append("  count(*) OVER () AS total_count")
          .append(" FROM integration_contents ic")
          // Pages = type 미지정 행. NULL 과 빈문자를 모두 대상으로 본다.
          .append(" WHERE ic.is_visible = true AND (ic.type IS NULL OR ic.type = '')");
        if (hasSite) {
            sb.append(" AND (ic.site_id = :siteId OR ic.site_id IS NULL)");
        }
        if (hasKeyword) {
            // ic.title 은 평문 저장 → raw q. ic.content 는 HTML 원문(& 가 &amp; 로 저장) → 엔티티 인코딩된 q.
            sb.append(" AND (ic.title ILIKE :q ESCAPE '\\' OR ic.content ILIKE :qHtml ESCAPE '\\')");
        }
        // 동일 updated_at 데이터가 존재하므로 id DESC tie-break 필수.
        sb.append(" ORDER BY ic.updated_at DESC, ic.id DESC")
          .append(" LIMIT :size OFFSET :offset");
        return sb.toString();
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}

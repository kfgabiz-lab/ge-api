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

@Slf4j
@Service
@RequiredArgsConstructor
public class PageSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CodeDetailRepository codeDetailRepository;

    private static final int SNIPPET_MAX_CHARS = 200;

    private static final String PAGE_SECTION_GROUP_CODE = "PAGE_SECTION";

    @Transactional(readOnly = true)
    public PageSearchResponse search(String q, String sections, int page, int size) {
        int safeSize = size <= 0 ? 10 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();

        String kw = hasKeyword ? SearchSqlSupport.toLikePattern(q.trim()) : null;
        String kwExact = hasKeyword ? SearchSqlSupport.toLikeExactPattern(q.trim()) : null;
        String kwPrefix = hasKeyword ? SearchSqlSupport.toLikePrefixPattern(q.trim()) : null;
        String kwRegex = hasKeyword ? SearchSqlSupport.toWordStartRegex(q.trim()) : null;

        Set<String> sectionTokens = parseSections(sections);
        boolean hasSections = !sectionTokens.isEmpty();

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

            if (title == null || title.isBlank()) {
                title = url;
            }

            content.add(new PageSearchItemResponse(id, url, title, snippet, section, sectionName));
        }

        long total = rows.isEmpty() ? 0L : toLong(rows.get(0)[6]);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        Map<String, Long> sectionCounts = buildSectionCounts(hasKeyword, kw);

        return new PageSearchResponse(content, total, totalPages, safePage, safeSize, sectionCounts);
    }

    private String buildSql(boolean hasKeyword, boolean hasSections) {
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
             .append(" LEFT JOIN code_detail cd")
             .append("        ON cd.code = m.page_section")
             .append("       AND cd.is_active = true")
             .append("       AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '")
             .append(PAGE_SECTION_GROUP_CODE).append("')")
             .append(" WHERE m.is_active = true");
        if (hasKeyword) {
            inner.append(" AND (t.title ILIKE :q ESCAPE '\\' OR t.text ILIKE :q ESCAPE '\\')");
        }
        if (hasSections) {
            inner.append(" AND m.page_section IN (:sections)");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT x.id, x.url, x.title, x.snippet, x.page_section, x.section_name,")
          .append("  count(*) OVER () AS total_count")
          .append(" FROM (").append(inner).append(") x")
          .append(" WHERE x.rn = 1")
          .append(" ORDER BY x.score DESC, x.sort_at DESC, x.id DESC")
          .append(" LIMIT :size OFFSET :offset");
        return sb.toString();
    }

    private Map<String, Long> buildSectionCounts(boolean hasKeyword, String kw) {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<CodeDetail> details =
            codeDetailRepository.findAllByGroup_GroupCodeAndActiveTrueOrderBySortOrderAsc(PAGE_SECTION_GROUP_CODE);
        for (CodeDetail detail : details) {
            counts.put(detail.getCode(), 0L);
        }

        Query query = entityManager.createNativeQuery(buildSectionCountsSql(hasKeyword));
        if (hasKeyword) {
            query.setParameter("q", kw);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        for (Object[] r : rows) {
            if (r[0] == null) continue;
            String code = r[0].toString();
            if (counts.containsKey(code)) {
                counts.put(code, toLong(r[1]));
            }
        }
        return counts;
    }

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

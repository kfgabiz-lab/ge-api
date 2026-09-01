package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.CategoryRef;
import com.ge.bo.dto.TechHubCategoryCountResponse;
import com.ge.bo.dto.TechHubCertCountResponse;
import com.ge.bo.dto.TechHubChapterResponse;
import com.ge.bo.dto.TechHubContentPageResponse;
import com.ge.bo.dto.TechHubContentResponse;
import com.ge.bo.dto.TechHubDetailResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TechHubService {

    @PersistenceContext
    private EntityManager entityManager;

    public static final String MASTER_GATE =
        " m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false"
      + " AND EXISTS (SELECT 1 FROM contents_version v WHERE v.contents_id = m.id"
      + "   AND v.version_expose = true AND v.is_deleted = false AND v.video_url IS NOT NULL AND v.video_url <> '')";

    private static final String CERT_STANDARD_EXPR = "m.attrs->>'video_prod_standard'";

    private static final Map<String, List<String>> CERT_STANDARD_CODES = Map.of(
        "iec", List.of("1", "3"),
        "ul",  List.of("2", "3")
    );

    private static final List<String> CERT_ORDER = List.of("ul", "iec");

    private record FilterClause(String where, boolean hasQ, boolean hasCats, boolean hasCerts) {}

    private static FilterClause buildFilterClause(String q, List<String> categoryL2Ids, List<String> certStds) {
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categoryL2Ids != null && !categoryL2Ids.isEmpty();
        boolean hasCerts = certStds != null && !certStds.isEmpty();

        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND (m.nahp_title ILIKE :q ESCAPE '\\' OR m.doc_title ILIKE :q ESCAPE '\\')");
        }
        if (hasCats) {
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cc2 WHERE cc2.contents_id = m.id"
                + " AND cc2.category_l2_id IS NOT NULL"
                + " AND cc2.nahp_display_flag = true AND cc2.is_deleted = false"
                + " AND cc2.category_l2_id IN (:cats))");
        }
        if (hasCerts) {
            where.append(" AND ").append(CERT_STANDARD_EXPR).append(" IN (:certStds)");
        }
        return new FilterClause(where.toString(), hasQ, hasCats, hasCerts);
    }

    private static void applyFilterParams(
            Query query, FilterClause fc, String q, List<String> categoryL2Ids, List<String> certStds) {
        if (fc.hasQ()) query.setParameter("q", SearchSqlSupport.toLikePattern(q.trim()));
        if (fc.hasCats()) query.setParameter("cats", categoryL2Ids);
        if (fc.hasCerts()) query.setParameter("certStds", certStds);
    }

    @Transactional(readOnly = true)
    public TechHubContentPageResponse getContents(String q, List<String> categoryL2Ids, List<String> certs,
                                                  int page, int size) {
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);
        List<String> certStds = resolveCertStandardCodes(certs);
        FilterClause fc = buildFilterClause(q, categoryL2Ids, certStds);

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + fc.where());
        applyFilterParams(countQuery, fc, q, categoryL2Ids, certStds);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String sql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)                 AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')          AS source_updated_at,"
            + "  rep.video_url, vc.version_count"
            + " FROM contents_master m"
            + " JOIN LATERAL ("
            + "   SELECT v.video_url FROM contents_version v"
            + "   WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false"
            + "     AND v.video_url IS NOT NULL AND v.video_url <> ''"
            + "   ORDER BY v.sort_key DESC LIMIT 1"
            + " ) rep ON true"
            + " JOIN LATERAL ("
            + "   SELECT count(*)::int AS version_count FROM contents_version v2"
            + "   WHERE v2.contents_id = m.id AND v2.version_expose = true AND v2.is_deleted = false"
            + "     AND v2.video_url IS NOT NULL AND v2.video_url <> ''"
            + " ) vc ON true"
            + fc.where()
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT :size OFFSET :offset";

        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, q, categoryL2Ids, certStds);
        query.setParameter("size", safeSize);
        query.setParameter("offset", safePage * safeSize);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Long> ids = new ArrayList<>();
        for (Object[] r : rows) {
            ids.add(((Number) r[0]).longValue());
        }
        Map<Long, List<CategoryRef>> categoriesById = loadCategoryRefs(ids);

        List<TechHubContentResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            content.add(new TechHubContentResponse(
                id,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? r[2].toString() : null,
                categoriesById.getOrDefault(id, List.of()),
                r[3] != null ? r[3].toString() : null,
                r[4] != null ? ((Number) r[4]).intValue() : 0
            ));
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new TechHubContentPageResponse(content, total, totalPages, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public TechHubDetailResponse getContentDetail(Long masterId) {
        String masterSql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)         AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')  AS source_updated_at"
            + " FROM contents_master m"
            + " WHERE m.id = :id AND m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false";
        Query masterQuery = entityManager.createNativeQuery(masterSql);
        masterQuery.setParameter("id", masterId);
        @SuppressWarnings("unchecked")
        List<Object[]> masterRows = masterQuery.getResultList();
        if (masterRows.isEmpty()) return null;

        Object[] mr = masterRows.get(0);
        String title = mr[1] != null ? mr[1].toString() : null;
        String sourceUpdatedAt = mr[2] != null ? mr[2].toString() : null;
        List<CategoryRef> categories = loadCategoryRefs(List.of(masterId)).getOrDefault(masterId, List.of());
        String categoryL2Id = categories.stream()
            .map(CategoryRef::categoryL2Id)
            .filter(id -> id != null)
            .findFirst()
            .orElse(null);

        String chapterSql = "SELECT v.id, v.version_name, v.sort_key, v.video_url"
            + " FROM contents_version v"
            + " WHERE v.contents_id = :id AND v.version_expose = true AND v.is_deleted = false"
            + "   AND v.video_url IS NOT NULL AND v.video_url <> ''"
            + " ORDER BY v.sort_key DESC";
        Query chapterQuery = entityManager.createNativeQuery(chapterSql);
        chapterQuery.setParameter("id", masterId);
        @SuppressWarnings("unchecked")
        List<Object[]> chapterRows = chapterQuery.getResultList();
        if (chapterRows.isEmpty()) return null;

        List<TechHubChapterResponse> chapters = new ArrayList<>();
        for (Object[] c : chapterRows) {
            chapters.add(new TechHubChapterResponse(
                c[0] != null ? ((Number) c[0]).longValue() : null,
                c[1] != null ? c[1].toString() : null,
                c[2] != null ? ((Number) c[2]).intValue() : 0,
                c[3] != null ? c[3].toString() : null
            ));
        }
        int versionCount = chapters.size();

        List<TechHubContentResponse> relatedVideos = new ArrayList<>();
        if (versionCount == 1 && categoryL2Id != null) {
            relatedVideos = findRelatedVideos(masterId, categoryL2Id);
        }

        return new TechHubDetailResponse(
            mr[0] != null ? ((Number) mr[0]).longValue() : null,
            title, sourceUpdatedAt, categories,
            versionCount, chapters, relatedVideos);
    }

    private List<TechHubContentResponse> findRelatedVideos(Long selfId, String categoryL2Id) {
        FilterClause fc = buildFilterClause(null, List.of(categoryL2Id), null);
        String sql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)                 AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')          AS source_updated_at,"
            + "  rep.video_url, vc.version_count"
            + " FROM contents_master m"
            + " JOIN LATERAL ("
            + "   SELECT v.video_url FROM contents_version v"
            + "   WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false"
            + "     AND v.video_url IS NOT NULL AND v.video_url <> ''"
            + "   ORDER BY v.sort_key DESC LIMIT 1"
            + " ) rep ON true"
            + " JOIN LATERAL ("
            + "   SELECT count(*)::int AS version_count FROM contents_version v2"
            + "   WHERE v2.contents_id = m.id AND v2.version_expose = true AND v2.is_deleted = false"
            + "     AND v2.video_url IS NOT NULL AND v2.video_url <> ''"
            + " ) vc ON true"
            + fc.where()
            + " AND m.id <> :selfId"
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT 3";
        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, null, List.of(categoryL2Id), null);
        query.setParameter("selfId", selfId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Long> ids = new ArrayList<>();
        for (Object[] r : rows) {
            ids.add(((Number) r[0]).longValue());
        }
        Map<Long, List<CategoryRef>> categoriesById = loadCategoryRefs(ids);

        List<TechHubContentResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            result.add(new TechHubContentResponse(
                id,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? r[2].toString() : null,
                categoriesById.getOrDefault(id, List.of()),
                r[3] != null ? r[3].toString() : null,
                r[4] != null ? ((Number) r[4]).intValue() : 0
            ));
        }
        return result;
    }

    private Map<Long, List<CategoryRef>> loadCategoryRefs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Query query = entityManager.createNativeQuery(
            "SELECT DISTINCT cc.contents_id, cc.category_l1_id, cc.category_l2_id"
            + " FROM contents_category cc"
            + " WHERE cc.contents_id IN (:ids)"
            + "   AND cc.nahp_display_flag = true AND cc.is_deleted = false"
            + " ORDER BY cc.contents_id, cc.category_l1_id, cc.category_l2_id");
        query.setParameter("ids", ids);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<Long, List<CategoryRef>> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long contentsId = ((Number) r[0]).longValue();
            result.computeIfAbsent(contentsId, k -> new ArrayList<>())
                .add(new CategoryRef(
                    r[1] != null ? r[1].toString() : null,
                    r[2] != null ? r[2].toString() : null));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TechHubCategoryCountResponse> getCategoryCounts(String q, List<String> categoryL2Ids, List<String> certs) {
        List<String> certStds = resolveCertStandardCodes(certs);
        FilterClause fc = buildFilterClause(q, categoryL2Ids, certStds);

        String sql = "SELECT cc.category_l2_id, count(DISTINCT m.id)::int"
            + " FROM contents_master m"
            + " JOIN contents_category cc ON cc.contents_id = m.id"
            + "   AND cc.category_l2_id IS NOT NULL"
            + "   AND cc.nahp_display_flag = true AND cc.is_deleted = false"
            + fc.where()
            + " GROUP BY cc.category_l2_id";

        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, q, categoryL2Ids, certStds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<TechHubCategoryCountResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            if (r[0] == null) continue;
            result.add(new TechHubCategoryCountResponse(
                r[0].toString(),
                r[1] != null ? ((Number) r[1]).intValue() : 0
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TechHubCertCountResponse> getCertCounts(String q, List<String> categoryL2Ids, List<String> certs) {
        List<String> certStds = resolveCertStandardCodes(certs);
        FilterClause fc = buildFilterClause(q, categoryL2Ids, certStds);

        String sql = "SELECT " + CERT_STANDARD_EXPR + " AS cert_std, count(*)::int"
            + " FROM contents_master m"
            + fc.where()
            + " GROUP BY " + CERT_STANDARD_EXPR;

        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, q, categoryL2Ids, certStds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, Integer> countByStandard = new LinkedHashMap<>();
        for (Object[] r : rows) {
            if (r[0] == null) continue;
            countByStandard.put(r[0].toString(), r[1] != null ? ((Number) r[1]).intValue() : 0);
        }

        List<TechHubCertCountResponse> result = new ArrayList<>();
        for (String certCode : CERT_ORDER) {
            int count = 0;
            for (String std : CERT_STANDARD_CODES.get(certCode)) {
                count += countByStandard.getOrDefault(std, 0);
            }
            result.add(new TechHubCertCountResponse(certCode, count));
        }
        return result;
    }

    private static List<String> resolveCertStandardCodes(List<String> certs) {
        if (certs == null || certs.isEmpty()) return null;
        LinkedHashSet<String> standards = new LinkedHashSet<>();
        for (String cert : certs) {
            if (cert == null) continue;
            List<String> mapped = CERT_STANDARD_CODES.get(cert.trim().toLowerCase());
            if (mapped != null) standards.addAll(mapped);
        }
        return standards.isEmpty() ? null : new ArrayList<>(standards);
    }
}

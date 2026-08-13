package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
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

    /** 문서 1건이 여러 카테고리(contents_category)에 걸쳐 있어도 카드 표시/카운트/필터가 항상 같은 값을
     *  가리키도록, 문서당 대표 카테고리(LV1/LV2) 1개를 고정 선정하는 LATERAL 서브쿼리.
     *  nahp_level_seq(소스 NAHP 가 부여한 문서 내 카테고리 우선순위, 0/1부터 시작)를 1순위로 삼아
     *  가장 우선순위가 높은(값이 작은) 카테고리를 대표로 선정하고, 값이 같거나 없는 경우를 대비해
     *  category_l1_id/category_l2_id/id 순으로 결정적으로 정렬한다(DownloadCenterService와 동일 패턴). */
    private static final String REPRESENTATIVE_CATEGORY_JOIN =
        " LEFT JOIN LATERAL ("
      + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
      + "   WHERE cc.contents_id = m.id AND cc.category_l2_id IS NOT NULL"
      + "     AND cc.nahp_display_flag = true AND cc.is_deleted = false"
      + "   ORDER BY cc.nahp_level_seq ASC NULLS LAST,"
      + "            cc.category_l1_id ASC, cc.category_l2_id ASC, cc.id ASC"
      + "   LIMIT 1"
      + " ) cat ON true";

    /** getContents/getCategoryCounts/getCertCounts/findRelatedVideos 가 공유하는 WHERE 절 빌더 결과.
     *  needsCategoryJoin() 이 true 인 경우에만 REPRESENTATIVE_CATEGORY_JOIN 을 FROM 절에 붙이면 된다. */
    private record FilterClause(String where, boolean hasQ, boolean hasCats, boolean hasCerts) {
        boolean needsCategoryJoin() {
            return hasCats;
        }
    }

    /** q(제목검색)/categoryL2Ids/certStds(이미 resolveCertStandardCodes 로 변환된 표준 코드) 필터를
     *  MASTER_GATE 에 이어붙인 WHERE 절을 만든다. categoryL2Ids 는 대표 카테고리(cat) 기준으로 판정하므로,
     *  반환된 FilterClause.needsCategoryJoin() 이 true 이면 호출부에서 REPRESENTATIVE_CATEGORY_JOIN 을
     *  FROM contents_master m 뒤에 추가해야 한다. */
    private static FilterClause buildFilterClause(String q, List<String> categoryL2Ids, List<String> certStds) {
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categoryL2Ids != null && !categoryL2Ids.isEmpty();
        boolean hasCerts = certStds != null && !certStds.isEmpty();

        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND (m.nahp_title ILIKE :q ESCAPE '\\' OR m.doc_title ILIKE :q ESCAPE '\\')");
        }
        if (hasCats) {
            where.append(" AND cat.category_l2_id IN (:cats)");
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
        String categoryJoin = fc.needsCategoryJoin() ? REPRESENTATIVE_CATEGORY_JOIN : "";

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + categoryJoin + fc.where());
        applyFilterParams(countQuery, fc, q, categoryL2Ids, certStds);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String sql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)                 AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')          AS source_updated_at,"
            + "  cat.category_l1_id, cat.category_l2_id,"
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
            + REPRESENTATIVE_CATEGORY_JOIN
            + fc.where()
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT :size OFFSET :offset";

        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, q, categoryL2Ids, certStds);
        query.setParameter("size", safeSize);
        query.setParameter("offset", safePage * safeSize);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<TechHubContentResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            content.add(new TechHubContentResponse(
                r[0] != null ? ((Number) r[0]).longValue() : null,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? r[2].toString() : null,
                r[3] != null ? r[3].toString() : null,
                r[4] != null ? r[4].toString() : null,
                r[5] != null ? r[5].toString() : null,
                r[6] != null ? ((Number) r[6]).intValue() : 0
            ));
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new TechHubContentPageResponse(content, total, totalPages, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public TechHubDetailResponse getContentDetail(Long masterId) {
        String masterSql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)         AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')  AS source_updated_at,"
            + "  cat.category_l1_id, cat.category_l2_id"
            + " FROM contents_master m"
            + REPRESENTATIVE_CATEGORY_JOIN
            + " WHERE m.id = :id AND m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false";
        Query masterQuery = entityManager.createNativeQuery(masterSql);
        masterQuery.setParameter("id", masterId);
        @SuppressWarnings("unchecked")
        List<Object[]> masterRows = masterQuery.getResultList();
        if (masterRows.isEmpty()) return null;

        Object[] mr = masterRows.get(0);
        String title = mr[1] != null ? mr[1].toString() : null;
        String sourceUpdatedAt = mr[2] != null ? mr[2].toString() : null;
        String categoryL1Id = mr[3] != null ? mr[3].toString() : null;
        String categoryL2Id = mr[4] != null ? mr[4].toString() : null;

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
            title, sourceUpdatedAt, categoryL1Id, categoryL2Id,
            versionCount, chapters, relatedVideos);
    }

    private List<TechHubContentResponse> findRelatedVideos(Long selfId, String categoryL2Id) {
        FilterClause fc = buildFilterClause(null, List.of(categoryL2Id), null);
        String sql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)                 AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')          AS source_updated_at,"
            + "  cat.category_l1_id, cat.category_l2_id,"
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
            + REPRESENTATIVE_CATEGORY_JOIN
            + fc.where()
            + " AND m.id <> :selfId"
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT 3";
        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, null, List.of(categoryL2Id), null);
        query.setParameter("selfId", selfId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<TechHubContentResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new TechHubContentResponse(
                r[0] != null ? ((Number) r[0]).longValue() : null,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? r[2].toString() : null,
                r[3] != null ? r[3].toString() : null,
                r[4] != null ? r[4].toString() : null,
                r[5] != null ? r[5].toString() : null,
                r[6] != null ? ((Number) r[6]).intValue() : 0
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TechHubCategoryCountResponse> getCategoryCounts(String q, List<String> categoryL2Ids, List<String> certs) {
        List<String> certStds = resolveCertStandardCodes(certs);
        FilterClause fc = buildFilterClause(q, categoryL2Ids, certStds);

        String sql = "SELECT cat.category_l2_id, count(*)::int"
            + " FROM contents_master m"
            + REPRESENTATIVE_CATEGORY_JOIN
            + fc.where()
            + " GROUP BY cat.category_l2_id";

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
        String categoryJoin = fc.needsCategoryJoin() ? REPRESENTATIVE_CATEGORY_JOIN : "";

        String sql = "SELECT " + CERT_STANDARD_EXPR + " AS cert_std, count(*)::int"
            + " FROM contents_master m"
            + categoryJoin
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

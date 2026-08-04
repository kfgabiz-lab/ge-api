package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.AzureAiSearchDocument;
import com.ge.bo.dto.AzureAiSearchResponse;
import com.ge.bo.dto.DownloadCenterCategoryCountResponse;
import com.ge.bo.dto.DownloadCenterDocTypeCountResponse;
import com.ge.bo.dto.DownloadCenterContentPageResponse;
import com.ge.bo.dto.DownloadCenterContentResponse;
import com.ge.bo.dto.DownloadCenterFileResponse;
import com.ge.bo.dto.DownloadCenterVersionResponse;
import com.ge.bo.dto.FoCodeResponse;
import com.ge.bo.dto.FoDocumentSearchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadCenterService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CodeService codeService;
    private final AzureAiSearchService azureAiSearchService;

    private static final String MASTER_GATE =
        " m.expose = true AND m.is_deleted = false AND m.doc_type <> 'V'"
      + " AND EXISTS (SELECT 1 FROM contents_version v"
      + "   JOIN contents_file f ON f.contents_version_id = v.id"
      + "     AND f.file_expose = true AND f.is_deleted = false"
      + "   WHERE v.contents_id = m.id"
      + "     AND v.version_expose = true AND v.is_deleted = false)"
      + " AND EXISTS (SELECT 1 FROM contents_category ccg"
      + "   WHERE ccg.contents_id = m.id"
      + "     AND ccg.nahp_display_flag = true AND ccg.is_deleted = false)";

    private static final String DOC_TYPE_GROUP_CODE = "DOC_TYPE";

    private static final String DOC_TYPE_CODE_JOIN =
        " LEFT JOIN code_detail cd"
      + "        ON cd.code = m.doc_type"
      + "       AND cd.is_active = true"
      + "       AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '" + DOC_TYPE_GROUP_CODE + "')";


    @Transactional(readOnly = true)
    public DownloadCenterContentPageResponse getContents(
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes,
            String sort, int page, int size) {
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categories != null && !categories.isEmpty();
        boolean hasParentCats = parentCategories != null && !parentCategories.isEmpty();
        boolean hasDocTypes = docTypes != null && !docTypes.isEmpty();
        boolean hasProductCodes = productCodes != null && !productCodes.isEmpty();

        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q");
        }
        if (hasDocTypes) {
            where.append(" AND m.doc_type IN (:docTypes)");
        }
        List<String> categoryClauses = new ArrayList<>();
        if (hasCats) {
            categoryClauses.add("EXISTS (SELECT 1 FROM contents_category cc"
                + " WHERE cc.contents_id = m.id AND cc.category_l2_id IN (:cats)"
                + "   AND cc.nahp_display_flag = true AND cc.is_deleted = false)");
        }
        if (hasParentCats) {
            categoryClauses.add("EXISTS (SELECT 1 FROM contents_category cc1"
                + " WHERE cc1.contents_id = m.id AND cc1.category_l1_id IN (:parentCats)"
                + "   AND cc1.category_l2_id IS NULL"
                + "   AND cc1.nahp_display_flag = true AND cc1.is_deleted = false)");
        }
        if (!categoryClauses.isEmpty()) {
            where.append(" AND (").append(String.join(" OR ", categoryClauses)).append(")");
        }
        if (hasProductCodes) {
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cc3"
                + " WHERE cc3.contents_id = m.id AND cc3.category_l3_id IN (:productCodes)"
                + "   AND cc3.nahp_display_flag = true AND cc3.is_deleted = false)");
        }

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + where);
        if (hasQ) countQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasDocTypes) countQuery.setParameter("docTypes", docTypes);
        if (hasCats) countQuery.setParameter("cats", categories);
        if (hasParentCats) countQuery.setParameter("parentCats", parentCategories);
        if (hasProductCodes) countQuery.setParameter("productCodes", productCodes);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m"
            + (isDocTypeSort(sort) ? DOC_TYPE_CODE_JOIN : "")
            + where + orderByClause(sort)
            + " LIMIT :size OFFSET :offset");
        if (hasQ) idQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasDocTypes) idQuery.setParameter("docTypes", docTypes);
        if (hasCats) idQuery.setParameter("cats", categories);
        if (hasParentCats) idQuery.setParameter("parentCats", parentCategories);
        if (hasProductCodes) idQuery.setParameter("productCodes", productCodes);
        idQuery.setParameter("size", safeSize);
        idQuery.setParameter("offset", safePage * safeSize);

        @SuppressWarnings("unchecked")
        List<Object> idRows = idQuery.getResultList();
        List<Long> pageIds = new ArrayList<>();
        for (Object o : idRows) {
            pageIds.add(((Number) o).longValue());
        }

        List<DownloadCenterContentResponse> content =
            pageIds.isEmpty() ? new ArrayList<>() : loadContents(pageIds);

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new DownloadCenterContentPageResponse(content, total, totalPages, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public FoDocumentSearchResponse searchDocuments(String q, int limit) {
        if (q == null || q.isBlank()) {
            return new FoDocumentSearchResponse(0L, new ArrayList<>());
        }
        int safeLimit = limit <= 0 ? 10 : limit;

        String trimmed = q.trim();
        String kw = SearchSqlSupport.toLikePattern(trimmed);
        String kwExact = SearchSqlSupport.toLikeExactPattern(trimmed);
        String kwPrefix = SearchSqlSupport.toLikePrefixPattern(trimmed);
        String kwRegex = SearchSqlSupport.toWordStartRegex(trimmed);

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m"
            + " WHERE" + MASTER_GATE
            + " AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'");
        countQuery.setParameter("q", kw);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new FoDocumentSearchResponse(0L, new ArrayList<>());
        }

        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m"
            + DOC_TYPE_CODE_JOIN
            + " WHERE" + MASTER_GATE
            + " AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'"
            + " ORDER BY (CASE"
            + "            WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qExact  ESCAPE '\\' THEN 100"
            + "            WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qPrefix ESCAPE '\\' THEN 80"
            + "            WHEN COALESCE(m.nahp_title, m.doc_title) ~* :qRegex THEN 60"
            + "            ELSE 40 END) DESC,"
            + "          cd.sort_order ASC NULLS LAST,"
            + "          m.source_updated_at DESC NULLS LAST,"
            + "          m.id DESC"
            + " LIMIT :limit");
        idQuery.setParameter("q", kw);
        idQuery.setParameter("qExact", kwExact);
        idQuery.setParameter("qPrefix", kwPrefix);
        idQuery.setParameter("qRegex", kwRegex);
        idQuery.setParameter("limit", safeLimit);

        @SuppressWarnings("unchecked")
        List<Object> idRows = idQuery.getResultList();
        List<Long> pageIds = new ArrayList<>();
        for (Object o : idRows) {
            pageIds.add(((Number) o).longValue());
        }

        List<DownloadCenterContentResponse> items =
            pageIds.isEmpty() ? new ArrayList<>() : loadContents(pageIds);

        return new FoDocumentSearchResponse(total, items);
    }

    /**
     * 챗봇 keyword(+연관검색어, 콤마구분)로 Azure AI Search 후보를 조회한 뒤,
     * 실제 우리 DB(contents_file.file_name)에 존재하는 문서만 골라 반환한다.
     * Azure 결과의 관련도 순서를 그대로 유지한다. 페이징 없이 매칭된 전체 리스트를 반환하며,
     * All탭 프리뷰/Documents탭은 이 리스트를 필요한 만큼만 잘라서 쓴다.
     */
    @Transactional(readOnly = true)
    public List<DownloadCenterContentResponse> searchDocumentsByKeyword(
            String keyword, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }

        AzureAiSearchResponse azureResponse = azureAiSearchService.azureAiSearch(keyword);
        if (azureResponse == null || azureResponse.value() == null || azureResponse.value().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Integer> fileNameRank = new LinkedHashMap<>();
        List<String> candidateFileNames = new ArrayList<>();
        for (AzureAiSearchDocument doc : azureResponse.value()) {
            String fn = doc.fileName();
            if (fn == null || fileNameRank.containsKey(fn)) continue;
            fileNameRank.put(fn, fileNameRank.size());
            candidateFileNames.add(fn);
        }
        if (candidateFileNames.isEmpty()) {
            return new ArrayList<>();
        }

        boolean hasCats = categories != null && !categories.isEmpty();
        boolean hasParentCats = parentCategories != null && !parentCategories.isEmpty();
        boolean hasDocTypes = docTypes != null && !docTypes.isEmpty();
        boolean hasProductCodes = productCodes != null && !productCodes.isEmpty();

        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE)
            .append(" AND f.file_name IN (:fileNames)");
        if (hasDocTypes) {
            where.append(" AND m.doc_type IN (:docTypes)");
        }
        List<String> categoryClauses = new ArrayList<>();
        if (hasCats) {
            categoryClauses.add("EXISTS (SELECT 1 FROM contents_category cc"
                + " WHERE cc.contents_id = m.id AND cc.category_l2_id IN (:cats)"
                + "   AND cc.nahp_display_flag = true AND cc.is_deleted = false)");
        }
        if (hasParentCats) {
            categoryClauses.add("EXISTS (SELECT 1 FROM contents_category cc1"
                + " WHERE cc1.contents_id = m.id AND cc1.category_l1_id IN (:parentCats)"
                + "   AND cc1.category_l2_id IS NULL"
                + "   AND cc1.nahp_display_flag = true AND cc1.is_deleted = false)");
        }
        if (!categoryClauses.isEmpty()) {
            where.append(" AND (").append(String.join(" OR ", categoryClauses)).append(")");
        }
        if (hasProductCodes) {
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cc3"
                + " WHERE cc3.contents_id = m.id AND cc3.category_l3_id IN (:productCodes)"
                + "   AND cc3.nahp_display_flag = true AND cc3.is_deleted = false)");
        }

        Query matchQuery = entityManager.createNativeQuery(
            "SELECT DISTINCT m.id, f.file_name FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + " JOIN contents_file f ON f.contents_version_id = v.id"
            + "   AND f.file_expose = true AND f.is_deleted = false"
            + where);
        matchQuery.setParameter("fileNames", candidateFileNames);
        if (hasDocTypes) matchQuery.setParameter("docTypes", docTypes);
        if (hasCats) matchQuery.setParameter("cats", categories);
        if (hasParentCats) matchQuery.setParameter("parentCats", parentCategories);
        if (hasProductCodes) matchQuery.setParameter("productCodes", productCodes);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = matchQuery.getResultList();
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Integer> masterBestRank = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long masterId = ((Number) r[0]).longValue();
            String fileName = r[1].toString();
            Integer fnRank = fileNameRank.get(fileName);
            if (fnRank == null) continue;
            masterBestRank.merge(masterId, fnRank, Math::min);
        }
        if (masterBestRank.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> orderedIds = masterBestRank.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .toList();

        return loadContents(orderedIds);
    }

    /** All탭 프리뷰용 — 매칭된 전체 리스트 중 상위 limit건만 반환(total은 매칭 전체 건수). */
    @Transactional(readOnly = true)
    public FoDocumentSearchResponse previewByKeyword(String keyword, int limit) {
        List<DownloadCenterContentResponse> matched =
            searchDocumentsByKeyword(keyword, null, null, null, null);
        int safeLimit = limit <= 0 ? 10 : limit;
        List<DownloadCenterContentResponse> items =
            matched.size() > safeLimit ? matched.subList(0, safeLimit) : matched;
        return new FoDocumentSearchResponse(matched.size(), items);
    }

    /** Documents탭용 — 매칭된 전체 리스트(이미 받아온 것) 안에서 page/size만큼 잘라 반환(쿼리 재호출 없음). */
    @Transactional(readOnly = true)
    public DownloadCenterContentPageResponse getContentsByKeyword(
            String keyword, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, int page, int size) {
        List<DownloadCenterContentResponse> matched =
            searchDocumentsByKeyword(keyword, categories, parentCategories, docTypes, productCodes);
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);
        int total = matched.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<DownloadCenterContentResponse> content = matched.subList(fromIndex, toIndex);
        return new DownloadCenterContentPageResponse(content, total, totalPages, safePage, safeSize);
    }

    private Map<String, String> loadDocTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (FoCodeResponse code : codeService.getFoCodes(DOC_TYPE_GROUP_CODE)) {
            labels.put(code.code(), code.name());
        }
        return labels;
    }

    private String orderByClause(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            case "doctype" -> " ORDER BY cd.sort_order ASC NULLS LAST"
                + ", m.source_updated_at DESC NULLS LAST, m.id DESC";
            case "oldest" -> " ORDER BY m.source_updated_at ASC NULLS LAST, m.id ASC";
            case "title" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) ASC, m.id ASC";
            case "title_desc" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) DESC, m.id DESC";
            default -> " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC";
        };
    }

    private static boolean isDocTypeSort(String sort) {
        return "doctype".equals(sort == null ? "" : sort.trim().toLowerCase());
    }

    private List<DownloadCenterContentResponse> loadContents(List<Long> pageIds) {
        String sql = "SELECT m.id, m.doc_type,"
            + "  COALESCE(m.nahp_title, m.doc_title)          AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')   AS content_date,"
            + "  c.category_l1_id, c.category_l2_id,"
            + "  v.id            AS version_id, v.version_name, v.sort_key,"
            + "  f.id            AS file_id, f.file_name, f.file_ext, f.file_size,"
            + "  f.source_system, f.file_path, f.source_file_path"
            + " FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + " JOIN contents_file f ON f.contents_version_id = v.id"
            + "   AND f.file_expose = true AND f.is_deleted = false"
            + " LEFT JOIN LATERAL ("
            + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
            + "   WHERE cc.contents_id = m.id"
            + "     AND cc.nahp_display_flag = true AND cc.is_deleted = false LIMIT 1"
            + " ) c ON true"
            + " WHERE m.id IN (:pageIds)"
            + " ORDER BY m.id, v.sort_key DESC, f.id";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("pageIds", pageIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, String> docTypeLabels = loadDocTypeLabels();

        Map<Long, ContentAcc> masterMap = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long masterId = ((Number) r[0]).longValue();
            ContentAcc acc = masterMap.computeIfAbsent(masterId, k -> {
                String docType = r[1] != null ? r[1].toString() : null;
                return new ContentAcc(
                    masterId,
                    docType,
                    docTypeLabels.get(docType),
                    r[2] != null ? r[2].toString() : null,
                    r[3] != null ? r[3].toString() : null,
                    r[4] != null ? r[4].toString() : null,
                    r[5] != null ? r[5].toString() : null);
            });

            Long versionId = ((Number) r[6]).longValue();
            VersionAcc ver = acc.versions.computeIfAbsent(versionId, k -> new VersionAcc(
                versionId,
                r[7] != null ? r[7].toString() : null,
                r[8] != null ? ((Number) r[8]).intValue() : 0));

            ver.files.add(new DownloadCenterFileResponse(
                r[9] != null ? ((Number) r[9]).longValue() : null,
                r[10] != null ? r[10].toString() : null,
                r[11] != null ? r[11].toString() : null,
                r[12] != null ? ((Number) r[12]).longValue() : null,
                r[12] != null ? formatFileSize(((Number) r[12]).longValue()) : null,
                r[13] != null ? r[13].toString() : null,
                r[14] != null ? r[14].toString() : null,
                r[15] != null ? r[15].toString() : null));
        }

        List<DownloadCenterContentResponse> result = new ArrayList<>();
        for (Long id : pageIds) {
            ContentAcc acc = masterMap.get(id);
            if (acc == null) continue;
            List<DownloadCenterVersionResponse> versions = new ArrayList<>();
            for (VersionAcc v : acc.versions.values()) {
                versions.add(new DownloadCenterVersionResponse(v.versionId, v.versionName, v.sortKey, v.files));
            }
            result.add(new DownloadCenterContentResponse(
                acc.id, acc.docType, acc.docTypeLabel, acc.title, acc.date,
                acc.categoryL1Id, acc.categoryL2Id, versions));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DownloadCenterCategoryCountResponse> getCategoryCounts() {
        String sql = "SELECT c.category_l1_id, c.category_l2_id, count(DISTINCT m.id)::int"
            + " FROM contents_master m"
            + " JOIN contents_category c ON c.contents_id = m.id"
            + "   AND c.nahp_display_flag = true AND c.is_deleted = false"
            + " WHERE" + MASTER_GATE
            + " GROUP BY c.category_l1_id, c.category_l2_id";

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<DownloadCenterCategoryCountResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new DownloadCenterCategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? ((Number) r[2]).intValue() : 0));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DownloadCenterDocTypeCountResponse> getDocTypeCounts(List<String> productCodes) {
        boolean hasProductCodes = productCodes != null && !productCodes.isEmpty();

        String productCodeClause = hasProductCodes
            ? " AND EXISTS (SELECT 1 FROM contents_category cc3"
              + " WHERE cc3.contents_id = m.id AND cc3.category_l3_id IN (:productCodes)"
              + "   AND cc3.nahp_display_flag = true AND cc3.is_deleted = false)"
            : "";

        String sql = "SELECT m.doc_type, count(*)::int"
            + " FROM contents_master m"
            + " WHERE" + MASTER_GATE
            + productCodeClause
            + " GROUP BY m.doc_type";

        Query query = entityManager.createNativeQuery(sql);
        if (hasProductCodes) query.setParameter("productCodes", productCodes);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, Integer> countByDocType = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String docType = r[0] != null ? r[0].toString() : null;
            int count = r[1] != null ? ((Number) r[1]).intValue() : 0;
            if (docType != null) countByDocType.put(docType, count);
        }

        List<DownloadCenterDocTypeCountResponse> result = new ArrayList<>();
        for (Map.Entry<String, String> docType : loadDocTypeLabels().entrySet()) {
            result.add(new DownloadCenterDocTypeCountResponse(
                docType.getKey(),
                docType.getValue(),
                countByDocType.getOrDefault(docType.getKey(), 0)));
        }
        return result;
    }

    private static String formatFileSize(Long bytes) {
        if (bytes == null) return null;
        double b = bytes;
        if (b < 1024) return bytes + "B";
        double kb = b / 1024;
        if (kb < 1024) return String.format("%.2f", kb) + "KB";
        double mb = kb / 1024;
        if (mb < 1024) return String.format("%.2f", mb) + "MB";
        double gb = mb / 1024;
        return String.format("%.2f", gb) + "GB";
    }


    private static final class ContentAcc {
        final Long id;
        final String docType;
        final String docTypeLabel;
        final String title;
        final String date;
        final String categoryL1Id;
        final String categoryL2Id;
        final Map<Long, VersionAcc> versions = new LinkedHashMap<>();

        ContentAcc(Long id, String docType, String docTypeLabel, String title,
                   String date, String categoryL1Id, String categoryL2Id) {
            this.id = id;
            this.docType = docType;
            this.docTypeLabel = docTypeLabel;
            this.title = title;
            this.date = date;
            this.categoryL1Id = categoryL1Id;
            this.categoryL2Id = categoryL2Id;
        }
    }

    private static final class VersionAcc {
        final Long versionId;
        final String versionName;
        final int sortKey;
        final List<DownloadCenterFileResponse> files = new ArrayList<>();

        VersionAcc(Long versionId, String versionName, int sortKey) {
            this.versionId = versionId;
            this.versionName = versionName;
            this.sortKey = sortKey;
        }
    }
}

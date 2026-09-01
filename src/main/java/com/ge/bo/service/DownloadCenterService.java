package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.AzureAiSearchDocument;
import com.ge.bo.dto.AzureAiSearchResponse;
import com.ge.bo.dto.DownloadCenterCategoryCountResponse;
import com.ge.bo.dto.DownloadCenterCategoryCountsResponse;
import com.ge.bo.dto.DownloadCenterDocTypeCountResponse;
import com.ge.bo.dto.DownloadCenterContentPageResponse;
import com.ge.bo.dto.DownloadCenterContentResponse;
import com.ge.bo.dto.DownloadCenterFileResponse;
import com.ge.bo.dto.DownloadCenterL1CategoryCountResponse;
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

    private static final String DOC_TYPE_GROUP_CODE = "DOC_TYPE";

    private static final String TITLE_EXPR = "COALESCE(m.nahp_title, m.doc_title)";

    private static final String MASTER_GATE =
        " m.expose = true AND m.is_deleted = false"
      + " AND m.doc_type IN (SELECT cd.code FROM code_detail cd"
      + "   JOIN code_group cg ON cg.id = cd.group_id"
      + "     AND cg.group_code = '" + DOC_TYPE_GROUP_CODE + "' AND cg.is_deleted = false"
      + "   WHERE cd.is_active = true AND cd.is_deleted = false)"
      + " AND EXISTS (SELECT 1 FROM contents_version v"
      + "   JOIN contents_file f ON f.contents_version_id = v.id"
      + "     AND f.file_expose = true AND f.is_deleted = false"
      + "   WHERE v.contents_id = m.id"
      + "     AND v.version_expose = true AND v.is_deleted = false)"
      + " AND EXISTS (SELECT 1 FROM contents_category ccg"
      + "   WHERE ccg.contents_id = m.id"
      + "     AND ccg.nahp_display_flag = true AND ccg.is_deleted = false)";

    private static final String DOC_TYPE_CODE_JOIN =
        " LEFT JOIN code_detail cd"
      + "        ON cd.code = m.doc_type"
      + "       AND cd.is_active = true"
      + "       AND cd.is_deleted = false"
      + "       AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '" + DOC_TYPE_GROUP_CODE + "' AND is_deleted = false)";

    /** 검색어(:q)가 문서유형 표시명(code_detail.name)과 일치하는지 확인하는 EXISTS 절 */
    private static final String DOC_TYPE_NAME_MATCH =
        "EXISTS (SELECT 1 FROM code_detail cd WHERE cd.code = m.doc_type"
      + " AND cd.is_active = true AND cd.is_deleted = false"
      + " AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '" + DOC_TYPE_GROUP_CODE + "' AND is_deleted = false)"
      + " AND cd.name ILIKE :q ESCAPE '\\')";

    /** 검색어(:q)가 문서에 첨부된 파일명(contents_file.file_name)과 일치하는지 확인하는 EXISTS 절(최신 버전에 한정) */
    private static final String FILE_NAME_MATCH =
        "EXISTS (SELECT 1 FROM contents_version v"
      + " JOIN contents_file f ON f.contents_version_id = v.id"
      + "   AND f.file_expose = true AND f.is_deleted = false"
      + " WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false"
      + " AND v.sort_key = (SELECT MAX(v2.sort_key) FROM contents_version v2"
      + "                    WHERE v2.contents_id = m.id"
      + "                      AND v2.version_expose = true AND v2.is_deleted = false)"
      + " AND f.file_name ILIKE :q ESCAPE '\\')";

    /** LV3 제품코드(예: L01-15-01)로 정확히 매핑된 콘텐츠만 노출한다 — LV1/LV2까지만 매핑된(정확한
     *  제품코드가 없는) 콘텐츠는 제외한다. */
    private static final String PRODUCT_CODE_EXISTS_CLAUSE =
        " EXISTS (SELECT 1 FROM contents_category cc3 WHERE cc3.contents_id = m.id"
      + "   AND cc3.nahp_display_flag = true AND cc3.is_deleted = false"
      + "   AND cc3.category_l3_id IN (:productCodes))";

    /** 문서 1건이 여러 카테고리(contents_category)에 걸쳐 있어도 카드 표시/카운트/필터가 항상 같은 값을
     *  가리키도록, 문서당 대표 카테고리(LV1/LV2) 1개를 고정 선정하는 LATERAL 서브쿼리.
     *  nahp_level_seq(소스 NAHP 가 부여한 문서 내 카테고리 우선순위, 0/1부터 시작)를 1순위로 삼아
     *  가장 우선순위가 높은(값이 작은) 카테고리를 대표로 선정하고, 값이 같거나 없는 경우를 대비해
     *  category_l1_id/category_l2_id/id 순으로 결정적으로 정렬한다. */
    private static final String REPRESENTATIVE_CATEGORY_JOIN =
        " LEFT JOIN LATERAL ("
      + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
      + "   WHERE cc.contents_id = m.id"
      + "     AND cc.nahp_display_flag = true AND cc.is_deleted = false"
      + "   ORDER BY cc.nahp_level_seq ASC NULLS LAST,"
      + "            cc.category_l1_id ASC, cc.category_l2_id ASC NULLS LAST, cc.id ASC"
      + "   LIMIT 1"
      + " ) rc ON true";

    /** getContents/getCategoryCounts/getDocTypeCounts 가 공유하는 WHERE 절 빌더 결과.
     *  needsCategoryJoin 이 true 인 경우에만 REPRESENTATIVE_CATEGORY_JOIN 을 FROM 절에 붙이면 된다. */
    private record FilterClause(
            String where, boolean hasQ, boolean hasDocTypes,
            boolean hasCats, boolean hasParentCats, boolean hasProductCodes,
            boolean hasContentIds) {
        boolean needsCategoryJoin() {
            return hasCats || hasParentCats;
        }
    }

    /** q(제목검색)/categories(LV2)/parentCategories(LV1-only)/docTypes/productCodes 필터를
     *  MASTER_GATE 에 이어붙인 WHERE 절을 만든다. categories/parentCategories 는 대표 카테고리(rc) 기준으로
     *  판정하므로, 반환된 FilterClause.needsCategoryJoin() 이 true 이면 호출부에서 REPRESENTATIVE_CATEGORY_JOIN 을
     *  FROM contents_master m 뒤에 추가해야 한다. */
    private static FilterClause buildFilterClause(
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, List<Long> contentIds) {
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categories != null && !categories.isEmpty();
        boolean hasParentCats = parentCategories != null && !parentCategories.isEmpty();
        boolean hasDocTypes = docTypes != null && !docTypes.isEmpty();
        boolean hasProductCodes = productCodes != null && !productCodes.isEmpty();
        boolean hasContentIds = hasQ && contentIds != null && !contentIds.isEmpty();

        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND (").append(TITLE_EXPR).append(" ILIKE :q ESCAPE '\\'")
                 .append(" OR ").append(DOC_TYPE_NAME_MATCH)
                 .append(" OR ").append(FILE_NAME_MATCH);
            if (hasContentIds) {
                where.append(" OR m.id IN (:contentIds)");
            }
            where.append(")");
        }
        if (hasDocTypes) {
            where.append(" AND m.doc_type IN (:docTypes)");
        }
        List<String> categoryClauses = new ArrayList<>();
        if (hasCats) {
            categoryClauses.add("rc.category_l2_id IN (:cats)");
        }
        if (hasParentCats) {
            categoryClauses.add("(rc.category_l1_id IN (:parentCats) AND rc.category_l2_id IS NULL)");
        }
        if (!categoryClauses.isEmpty()) {
            where.append(" AND (").append(String.join(" OR ", categoryClauses)).append(")");
        }
        if (hasProductCodes) {
            where.append(" AND").append(PRODUCT_CODE_EXISTS_CLAUSE);
        }
        return new FilterClause(
            where.toString(), hasQ, hasDocTypes, hasCats, hasParentCats, hasProductCodes, hasContentIds);
    }

    private static void applyFilterParams(
            Query query, FilterClause fc,
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, List<Long> contentIds) {
        if (fc.hasQ()) query.setParameter("q", "%" + q.trim() + "%");
        if (fc.hasDocTypes()) query.setParameter("docTypes", docTypes);
        if (fc.hasCats()) query.setParameter("cats", categories);
        if (fc.hasParentCats()) query.setParameter("parentCats", parentCategories);
        if (fc.hasProductCodes()) {
            query.setParameter("productCodes", productCodes);
        }
        if (fc.hasContentIds()) {
            query.setParameter("contentIds", contentIds);
        }
    }

    @Transactional(readOnly = true)
    public DownloadCenterContentPageResponse getContents(
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, boolean includeFileContent,
            String sort, int page, int size) {
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);

        List<Long> contentIds = (includeFileContent && q != null && !q.isBlank())
                ? new ArrayList<>(findFileContentMatchRanks(q).keySet())
                : List.of();

        FilterClause fc = buildFilterClause(q, categories, parentCategories, docTypes, productCodes, contentIds);
        String categoryJoin = fc.needsCategoryJoin() ? REPRESENTATIVE_CATEGORY_JOIN : "";

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + categoryJoin + fc.where());
        applyFilterParams(countQuery, fc, q, categories, parentCategories, docTypes, productCodes, contentIds);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m"
            + (isDocTypeSort(sort) ? DOC_TYPE_CODE_JOIN : "")
            + categoryJoin
            + fc.where() + orderByClause(sort, contentIds)
            + " LIMIT :size OFFSET :offset");
        applyFilterParams(idQuery, fc, q, categories, parentCategories, docTypes, productCodes, contentIds);
        idQuery.setParameter("size", safeSize);
        idQuery.setParameter("offset", safePage * safeSize);

        List<Long> pageIds = toIdList(idQuery.getResultList());

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
        List<Long> contentIds = List.of();

        FilterClause fc = buildFilterClause(trimmed, null, null, null, null, contentIds);

        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + fc.where());
        applyFilterParams(countQuery, fc, trimmed, null, null, null, null, contentIds);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new FoDocumentSearchResponse(0L, new ArrayList<>());
        }

        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m"
            + DOC_TYPE_CODE_JOIN
            + fc.where()
            + searchRankOrderBy(contentIds)
            + " LIMIT :limit");
        applyFilterParams(idQuery, fc, trimmed, null, null, null, null, contentIds);
        applySearchRankParams(idQuery, trimmed);
        idQuery.setParameter("limit", safeLimit);

        List<Long> pageIds = toIdList(idQuery.getResultList());

        List<DownloadCenterContentResponse> items =
            pageIds.isEmpty() ? new ArrayList<>() : loadContents(pageIds);

        return new FoDocumentSearchResponse(total, items);
    }

    @Transactional(readOnly = true)
    public List<DownloadCenterContentResponse> searchDocumentsByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        return searchDocumentsRanked(keyword, keyword.split(",", 2)[0].trim());
    }

    private List<DownloadCenterContentResponse> searchDocumentsRanked(String azureKeyword, String matchKeyword) {
        List<Long> contentIds = new ArrayList<>(findFileContentMatchRanks(azureKeyword).keySet());
        String q = matchKeyword == null ? "" : matchKeyword.trim();

        if (q.isEmpty()) {
            return contentIds.isEmpty() ? new ArrayList<>() : loadContents(contentIds);
        }

        FilterClause fc = buildFilterClause(q, null, null, null, null, contentIds);

        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m"
            + DOC_TYPE_CODE_JOIN
            + fc.where()
            + searchRankOrderBy(fc.hasContentIds() ? contentIds : List.of()));
        applyFilterParams(idQuery, fc, q, null, null, null, null, contentIds);
        applySearchRankParams(idQuery, q);

        List<Long> orderedIds = toIdList(idQuery.getResultList());
        return orderedIds.isEmpty() ? new ArrayList<>() : loadContents(orderedIds);
    }

    private static String searchRankOrderBy(List<Long> contentIds) {
        StringBuilder order = new StringBuilder(" ORDER BY ");
        if (contentIds != null && !contentIds.isEmpty()) {
            order.append("array_position(ARRAY[").append(joinLongs(contentIds))
                 .append("]::bigint[], m.id) ASC NULLS LAST, ");
        }
        return order
            .append("(CASE")
            .append(" WHEN ").append(TITLE_EXPR).append(" ILIKE :qExact  ESCAPE '\\' THEN 100")
            .append(" WHEN ").append(TITLE_EXPR).append(" ILIKE :qPrefix ESCAPE '\\' THEN 80")
            .append(" WHEN ").append(TITLE_EXPR).append(" ~* :qRegex THEN 60")
            .append(" WHEN ").append(TITLE_EXPR).append(" ILIKE :q ESCAPE '\\' THEN 50")
            .append(" WHEN ").append(DOC_TYPE_NAME_MATCH).append(" THEN 40")
            .append(" ELSE 20 END) DESC,")
            .append(" cd.sort_order ASC NULLS LAST,")
            .append(" m.source_updated_at DESC NULLS LAST,")
            .append(" m.id DESC")
            .toString();
    }

    private static void applySearchRankParams(Query query, String keyword) {
        query.setParameter("qExact", SearchSqlSupport.toLikeExactPattern(keyword));
        query.setParameter("qPrefix", SearchSqlSupport.toLikePrefixPattern(keyword));
        query.setParameter("qRegex", SearchSqlSupport.toWordStartRegex(keyword));
    }

    private static List<Long> toIdList(List<?> rows) {
        List<Long> ids = new ArrayList<>();
        for (Object o : rows) {
            ids.add(((Number) o).longValue());
        }
        return ids;
    }

    /**
     * Azure AI Search(파일 본문 인덱스)로 keyword 에 매칭되는 파일명을 얻고, 그 파일명을 실제로 보유한
     * contents_master 를 DB 에서 되찾아 "관련도 순위" 맵을 만든다.
     * - 반환: contents_master.id → 0..k-1 로 조밀하게(dense) 재부여된 관련도 순번(LinkedHashMap = 순위 오름차순).
     * - DB 재매칭은 MASTER_GATE + version_expose/file_expose 게이트만 적용하고 최신 버전 제한은 두지 않는다
     *   (구버전 파일에서만 본문이 매칭되는 경우도 문서 단위로는 검색 결과에 포함시켜야 하므로).
     * - Azure 실패/결과 없음이면 빈 맵을 반환한다.
     */
    private LinkedHashMap<Long, Integer> findFileContentMatchRanks(String keyword) {
        LinkedHashMap<Long, Integer> ranks = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            return ranks;
        }

        AzureAiSearchResponse azureResponse = azureAiSearchService.azureAiSearch(keyword);

        Map<String, Integer> fileNameRank = new LinkedHashMap<>();
        List<String> candidateFileNames = new ArrayList<>();
        if (azureResponse != null && azureResponse.value() != null) {
            for (AzureAiSearchDocument doc : azureResponse.value()) {
                String fn = doc.fileName();
                if (fn == null || fileNameRank.containsKey(fn)) continue;
                fileNameRank.put(fn, fileNameRank.size());
                candidateFileNames.add(fn);
            }
        }
        if (candidateFileNames.isEmpty()) {
            return ranks;
        }

        Query matchQuery = entityManager.createNativeQuery(
            "SELECT DISTINCT m.id, f.file_name FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + " JOIN contents_file f ON f.contents_version_id = v.id"
            + "   AND f.file_expose = true AND f.is_deleted = false"
            + " WHERE" + MASTER_GATE
            + " AND f.file_name IN (:fileNames)");
        matchQuery.setParameter("fileNames", candidateFileNames);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = matchQuery.getResultList();

        Map<Long, Integer> masterBestRank = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long masterId = ((Number) r[0]).longValue();
            String fileName = r[1].toString();
            Integer fnRank = fileNameRank.get(fileName);
            if (fnRank == null) continue;
            masterBestRank.merge(masterId, fnRank, Math::min);
        }

        List<Long> orderedIds = masterBestRank.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .toList();
        for (Long id : orderedIds) {
            ranks.put(id, ranks.size());
        }
        return ranks;
    }

    @Transactional(readOnly = true)
    public FoDocumentSearchResponse getContentsByKeyword(String keyword, String q) {
        List<DownloadCenterContentResponse> matched;
        if (keyword != null && !keyword.isBlank()) {
            matched = searchDocumentsByKeyword(keyword);
        } else if (q != null && !q.isBlank()) {
            matched = searchDocumentsRanked(q, q);
        } else {
            matched = new ArrayList<>();
        }
        return new FoDocumentSearchResponse(matched.size(), matched);
    }

    private Map<String, String> loadDocTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (FoCodeResponse code : codeService.getFoCodes(DOC_TYPE_GROUP_CODE)) {
            labels.put(code.code(), code.name());
        }
        return labels;
    }

    private static final String NEWEST_ORDER_BY =
        " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC";

    private String orderByClause(String sort, List<Long> contentIds) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            case "doctype" -> " ORDER BY cd.sort_order ASC NULLS LAST"
                + ", m.source_updated_at DESC NULLS LAST, m.id DESC";
            case "title" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) ASC, m.id ASC";
            case "title_desc" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) DESC, m.id DESC";
            case "relevance" -> (contentIds == null || contentIds.isEmpty())
                ? NEWEST_ORDER_BY
                : " ORDER BY array_position(ARRAY[" + joinLongs(contentIds) + "]::bigint[], m.id) ASC NULLS LAST,"
                  + "          m.source_updated_at DESC NULLS LAST, m.id DESC";
            default -> NEWEST_ORDER_BY;
        };
    }

    /** Long 값만 콤마로 이어 붙인다(SQL 배열 리터럴 인라인용 — 사용자 입력 문자열은 절대 들어가지 않는다). */
    private static String joinLongs(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(Long.toString(id));
        }
        return sb.toString();
    }

    private static boolean isDocTypeSort(String sort) {
        return "doctype".equals(sort == null ? "" : sort.trim().toLowerCase());
    }

    private List<DownloadCenterContentResponse> loadContents(List<Long> pageIds) {
        String sql = "SELECT m.id, m.doc_type,"
            + "  COALESCE(m.nahp_title, m.doc_title)          AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')   AS content_date,"
            + "  rc.category_l1_id, rc.category_l2_id,"
            + "  v.id            AS version_id, v.version_name, v.sort_key,"
            + "  f.id            AS file_id, f.file_name, f.file_ext, f.file_size,"
            + "  f.source_system, f.file_path, f.source_file_path"
            + " FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + " JOIN contents_file f ON f.contents_version_id = v.id"
            + "   AND f.file_expose = true AND f.is_deleted = false"
            + REPRESENTATIVE_CATEGORY_JOIN
            + " WHERE m.id IN (:pageIds)"
            // 버전 정렬 기준: version_name 앞의 문자 접두사(예: "V1.5"의 "V")를 떼어낸 뒤 점 개수로 분기한다.
            // - 점이 0~1개(예: "1.5", "4.80")면 진짜 소수(decimal)로 보고 numeric 캐스팅 그대로 비교한다
            //   ("1.10"은 실제로 1.1을 뜻하므로 1.2보다 작아야 맞다 — 정수쌍으로 보면 반대로 나옴).
            // - 점이 2개 이상(예: "3.90.1414")이면 소수 하나로 볼 수 없는 major.minor.build 형식이므로,
            //   각 구간을 정수 배열로 캐스팅해 사전식 비교한다("3.90.1414" vs "3.80.0605"가 자릿수 때문에
            //   문자열 비교로 틀어지는 문제 방지). 두 분기 모두 numeric[]로 반환 타입을 맞춰 ORDER BY에서
            // 함께 비교 가능하게 한다. CATALOG("YYYYMM vNN" 형식, 공백 포함)/CERTI(항상 NULL)처럼 두 형식
            // 다 아닌 버전명은 NULL로 처리해 뒤로 미룬다 — 두 소스 모두 문서당 버전이 항상 1건뿐이라 순서
            // 자체가 의미 없어 안전하다.
            + " ORDER BY m.id,"
            + "   CASE WHEN regexp_replace(v.version_name, '^[A-Za-z]+', '') ~ '^[0-9]+(\\.[0-9]+){2,}$'"
            + "        THEN string_to_array(regexp_replace(v.version_name, '^[A-Za-z]+', ''), '.')::numeric[]"
            + "        WHEN regexp_replace(v.version_name, '^[A-Za-z]+', '') ~ '^[0-9]+(\\.[0-9]+)?$'"
            + "        THEN ARRAY[regexp_replace(v.version_name, '^[A-Za-z]+', '')::numeric]"
            + "        ELSE NULL END DESC NULLS LAST,"
            + "   f.id";

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
    public DownloadCenterCategoryCountsResponse getCategoryCounts(
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, boolean includeFileContent) {
        List<Long> contentIds = (includeFileContent && q != null && !q.isBlank())
                ? new ArrayList<>(findFileContentMatchRanks(q).keySet())
                : List.of();

        FilterClause fc = buildFilterClause(q, categories, parentCategories, docTypes, productCodes, contentIds);

        String l2Sql = "SELECT rc.category_l1_id, rc.category_l2_id, count(*)::int"
            + " FROM contents_master m"
            + REPRESENTATIVE_CATEGORY_JOIN
            + fc.where()
            + " GROUP BY rc.category_l1_id, rc.category_l2_id";
        Query l2Query = entityManager.createNativeQuery(l2Sql);
        applyFilterParams(l2Query, fc, q, categories, parentCategories, docTypes, productCodes, contentIds);
        @SuppressWarnings("unchecked")
        List<Object[]> l2Rows = l2Query.getResultList();
        List<DownloadCenterCategoryCountResponse> l2Counts = new ArrayList<>();
        for (Object[] r : l2Rows) {
            l2Counts.add(new DownloadCenterCategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? r[1].toString() : null,
                r[2] != null ? ((Number) r[2]).intValue() : 0));
        }

        String l1Sql = "SELECT rc.category_l1_id, count(*)::int"
            + " FROM contents_master m"
            + REPRESENTATIVE_CATEGORY_JOIN
            + fc.where()
            + " GROUP BY rc.category_l1_id";
        Query l1Query = entityManager.createNativeQuery(l1Sql);
        applyFilterParams(l1Query, fc, q, categories, parentCategories, docTypes, productCodes, contentIds);
        @SuppressWarnings("unchecked")
        List<Object[]> l1Rows = l1Query.getResultList();
        List<DownloadCenterL1CategoryCountResponse> l1Counts = new ArrayList<>();
        for (Object[] r : l1Rows) {
            l1Counts.add(new DownloadCenterL1CategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? ((Number) r[1]).intValue() : 0));
        }

        return new DownloadCenterCategoryCountsResponse(l1Counts, l2Counts);
    }

    @Transactional(readOnly = true)
    public List<DownloadCenterDocTypeCountResponse> getDocTypeCounts(
            String q, List<String> categories, List<String> parentCategories,
            List<String> docTypes, List<String> productCodes, boolean includeFileContent) {
        List<Long> contentIds = (includeFileContent && q != null && !q.isBlank())
                ? new ArrayList<>(findFileContentMatchRanks(q).keySet())
                : List.of();

        FilterClause fc = buildFilterClause(q, categories, parentCategories, docTypes, productCodes, contentIds);

        String sql = "SELECT m.doc_type, count(*)::int"
            + " FROM contents_master m"
            + (fc.needsCategoryJoin() ? REPRESENTATIVE_CATEGORY_JOIN : "")
            + fc.where()
            + " GROUP BY m.doc_type";

        Query query = entityManager.createNativeQuery(sql);
        applyFilterParams(query, fc, q, categories, parentCategories, docTypes, productCodes, contentIds);
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

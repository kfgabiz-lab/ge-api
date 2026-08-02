package com.ge.bo.service;

import com.ge.bo.common.search.SearchSqlSupport;
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

/**
 * FO Download Center(Support > Download Center) 콘텐츠 조회 서비스.
 * - 데이터 소스: contents_master/contents_version/contents_file/contents_category (SSQ 배치 적재 테이블, page_data와 별개 도메인).
 *   → PageDataService(page_data 전용)와 분리해 전용 서비스로 둔다. TechHubService(영상 doc_type='V')와도 대상 도메인이 다르다.
 * - 대상: 영상 제외(doc_type <> 'V') 문서 콘텐츠. 노출 게이트: master.expose + version.version_expose + file.file_expose + category.nahp_display_flag + 삭제 아님.
 * - 카드 단위 = contents_master(다버전/다파일도 1장). 버전(sort_key DESC)마다 파일 목록을 함께 내린다.
 * - 2단 쿼리: ① 게이트/필터/정렬로 master.id 페이지 목록 조회 → ② pageIds 로 master+version+file 일괄 조인 조회 후 Java 그룹핑.
 *   contents_master 에 site_id 컬럼이 없어 사이트 스코프 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadCenterService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CodeService codeService;

    // 공통 게이트(master): 노출 + 미삭제 + 영상 제외 + "노출 버전 & 노출 파일 존재" + "노출 카테고리 존재".
    // :q / :docTypes / :cats / :productCodes 는 호출부에서 조건부로 덧붙인다.
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

    // 문서유형 코드/라벨/노출순서의 원천 = 공통코드 그룹(code_group.group_code).
    private static final String DOC_TYPE_GROUP_CODE = "DOC_TYPE";

    // 문서유형 정렬용 공통코드 조인 — m.doc_type 을 code_detail(DOC_TYPE) 에 붙여 cd.sort_order 를 정렬키로 쓴다.
    // 노출 순서의 원천은 DB 공통코드이므로 코드에 우선순위를 두지 않는다(순서 변경은 공통코드 관리화면에서).
    // LEFT JOIN 이라 공통코드에 없는 doc_type 도 누락되지 않고, ORDER BY 의 NULLS LAST 로 맨 뒤에 배치된다.
    private static final String DOC_TYPE_CODE_JOIN =
        " LEFT JOIN code_detail cd"
      + "        ON cd.code = m.doc_type"
      + "       AND cd.is_active = true"
      + "       AND cd.group_id = (SELECT id FROM code_group WHERE group_code = '" + DOC_TYPE_GROUP_CODE + "')";


    /**
     * Download Center 목록(카드) 조회 — 키워드 + docType + LV2 카테고리 + LV3 제품코드 필터 + 정렬 페이징.
     *
     * @param q            키워드(제목 nahp_title/doc_title ILIKE). null/blank 이면 미적용.
     * @param categories   선택된 LV2 코드 목록(그룹 내 OR = IN). null/empty 이면 미적용.
     * @param parentCategories 선택된 LV1 카테고리 코드 목록(예: L01). 해당 LV1 에 속하지만 LV2 가 지정되지 않은
     *                     (category_l2_id IS NULL) 콘텐츠를 categories(LV2) 조건과 OR 로 함께 포함시킨다.
     *                     null/empty 이면 미적용 → 기존 동작과 동일.
     * @param docTypes     선택된 문서유형 코드 목록(IN). null/empty 이면 미적용.
     * @param productCodes 선택된 LV3 제품코드 목록(예: "L01-02-01", 그룹 내 OR = IN). null/empty 이면 미적용.
     *                     제품상세 Downloads 섹션에서 "현재 제품과 연계된 파일만" 거를 때 사용한다.
     *                     한 제품(slug)이 복수 product_code 를 갖는 경우가 있어 다중값을 받는다.
     *                     categories(LV2)와는 AND 로 결합된다.
     * @param sort         정렬: doctype(문서유형 우선순위)/newest(기본)/oldest/title/title_desc.
     * @param page         0-based 페이지.
     * @param size         페이지 크기(기본 12).
     */
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

        // 공통 WHERE(게이트 + 조건부 키워드/문서유형/카테고리/제품코드)
        // ⚠️ 이 where 문자열을 ①건수 쿼리와 ②id 목록 쿼리가 공유하므로 조건은 양쪽에 항상 동일하게 반영된다.
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
            // LV2 조건과 동일한 EXISTS 패턴 — 한 콘텐츠가 여러 제품에 매핑돼도 행 중복이 생기지 않는다.
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cc3"
                + " WHERE cc3.contents_id = m.id AND cc3.category_l3_id IN (:productCodes)"
                + "   AND cc3.nahp_display_flag = true AND cc3.is_deleted = false)");
        }

        // ① 전체 건수
        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + where);
        if (hasQ) countQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasDocTypes) countQuery.setParameter("docTypes", docTypes);
        if (hasCats) countQuery.setParameter("cats", categories);
        if (hasParentCats) countQuery.setParameter("parentCats", parentCategories);
        if (hasProductCodes) countQuery.setParameter("productCodes", productCodes);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ② 현재 페이지 master.id 목록(정렬 적용)
        // sort=doctype 일 때만 공통코드 조인을 덧붙인다(다른 정렬에는 불필요한 조인을 만들지 않는다).
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

    /**
     * FO 통합검색용 문서 키워드 검색 — 제목(nahp_title 우선, 없으면 doc_title) 부분일치.
     * - q null/blank 이면 전체 덤프 방지를 위해 즉시 {0, []} 반환.
     * - 게이트는 목록 조회와 동일한 MASTER_GATE(노출 + 미삭제 + 영상 제외 + 노출 버전/파일 존재) 재사용.
     * - 상위 N id 조회 후 loadContents(pageIds) 로 버전/파일 중첩을 완성(pageIds 순서 보존).
     *
     * @param q     검색 키워드
     * @param limit 반환 상위 건수(<=0 이면 10)
     */
    @Transactional(readOnly = true)
    public FoDocumentSearchResponse searchDocuments(String q, int limit) {
        // q 빈값(null/공백)이면 전체 덤프 방지를 위해 즉시 빈 결과 반환
        if (q == null || q.isBlank()) {
            return new FoDocumentSearchResponse(0L, new ArrayList<>());
        }
        int safeLimit = limit <= 0 ? 10 : limit;

        // LIKE 와일드카드 이스케이프 — '\' 를 먼저 이스케이프한 뒤 '%'/'_' 를 이스케이프하고 %...% 로 감싼다(PageDataService.searchProducts 동일 방식)
        String trimmed = q.trim();
        String kw = SearchSqlSupport.toLikePattern(trimmed);
        String kwExact = SearchSqlSupport.toLikeExactPattern(trimmed);
        String kwPrefix = SearchSqlSupport.toLikePrefixPattern(trimmed);
        String kwRegex = SearchSqlSupport.toWordStartRegex(trimmed);

        // ① 전체 매칭 건수(total) — limit 무관
        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m"
            + " WHERE" + MASTER_GATE
            + " AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'");
        countQuery.setParameter("q", kw);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new FoDocumentSearchResponse(0L, new ArrayList<>());
        }

        // ② 상위 N master.id — doc_type 공통코드(DOC_TYPE) 순서 정렬(LEFT JOIN + NULLS LAST 방어)
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

        // ③ 버전/파일 중첩 로딩(기존 loadContents 재사용, pageIds 순서 보존)
        List<DownloadCenterContentResponse> items =
            pageIds.isEmpty() ? new ArrayList<>() : loadContents(pageIds);

        return new FoDocumentSearchResponse(total, items);
    }

    /**
     * 문서유형 공통코드(DOC_TYPE) 조회 — code → 영문 라벨 매핑을 sort_order 오름차순으로 반환한다.
     * - FO 공개 코드 조회 공통 경로(CodeService.getFoCodes)를 그대로 재사용한다.
     *   라벨은 code_detail.name(한글 기준값)이 아니라 nameMsgKey 의 메시지리소스 en 값이다
     *   (예: code C → name "Catalogs" 가 아니라 common.label.catalog 의 en "Catalog").
     *   msgKey 가 없거나 미등록이면 getFoCodes 내부 폴백으로 name 이 그대로 쓰인다.
     * - 반환 Map 의 순회 순서가 곧 /doctype-counts 응답의 문서유형 나열 순서다.
     */
    private Map<String, String> loadDocTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (FoCodeResponse code : codeService.getFoCodes(DOC_TYPE_GROUP_CODE)) {
            labels.put(code.code(), code.name());
        }
        return labels;
    }

    // 정렬 절 — doctype/newest(기본)/oldest/title/title_desc.
    // ⚠️ 미정의 값은 400 이 아니라 조용히 newest 로 처리된다(default 분기). 새 정렬을 추가할 때는
    //    반드시 여기 case 를 먼저 넣어야 하며, FE 가 보내는 값과 철자가 다르면 "정렬이 안 먹는" 게 아니라
    //    "최신순으로 보이는" 증상이 되므로 오진하기 쉽다.
    private String orderByClause(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            // 문서유형 순서(공통코드 sort_order) → 동일 유형 내에서는 등록일시 내림차순(기획서 명시).
            // ⚠️ 이 case 를 쓰려면 쿼리에 DOC_TYPE_CODE_JOIN(cd) 이 함께 붙어 있어야 한다(isDocTypeSort 로 분기).
            case "doctype" -> " ORDER BY cd.sort_order ASC NULLS LAST"
                + ", m.source_updated_at DESC NULLS LAST, m.id DESC";
            case "oldest" -> " ORDER BY m.source_updated_at ASC NULLS LAST, m.id ASC";
            case "title" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) ASC, m.id ASC";
            case "title_desc" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) DESC, m.id DESC";
            default -> " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"; // newest
        };
    }

    // orderByClause 의 "doctype" case 와 동일한 정규화 기준 — 분기가 어긋나면 cd 별칭 미해석 오류가 나므로 함께 관리한다.
    private static boolean isDocTypeSort(String sort) {
        return "doctype".equals(sort == null ? "" : sort.trim().toLowerCase());
    }

    /**
     * ② pageIds 로 master+version(노출)+file(노출) 일괄 조인 조회 후 master→version→file 그룹핑.
     * - 대표 카테고리는 LEFT JOIN LATERAL(LIMIT 1)로 함께 조회.
     * - 반환 순서는 ① 단계 pageIds 순서(정렬 결과)대로 재정렬.
     */
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

        // masterId → 콘텐츠 누적기(버전 → 파일 목록). 삽입 순서 유지.
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

        // pageIds(정렬 결과) 순서대로 재정렬해 반환.
        List<DownloadCenterContentResponse> result = new ArrayList<>();
        for (Long id : pageIds) {
            ContentAcc acc = masterMap.get(id);
            if (acc == null) continue; // 방어(게이트상 발생하지 않음)
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

    /**
     * LV2 카테고리별 노출 콘텐츠 건수 — 필터 아코디언 숫자용.
     * LV1>LV2 계층 자체는 FE가 category-data로 구성하고, 여기 없는 LV2는 0으로 표시한다.
     */
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

    /**
     * 문서유형(docType)별 노출 콘텐츠 건수 — 필터 패널 문서유형 옆 숫자용.
     * - 게이트는 getCategoryCounts 와 동일(MASTER_GATE). GROUP BY m.doc_type 으로 실제 건수를 집계한 뒤,
     *   공통코드(DOC_TYPE) 에 등록된 문서유형을 sort_order 순으로 항상 순회해
     *   매칭 안 되면 count=0 으로 채워 반환한다(FE가 전 유형 렌더링).
     * - productCodes 미지정(null/empty) 이면 Download Center 전체 기준(기존 동작과 동일 — 하위호환).
     *
     * @param productCodes 선택된 LV3 제품코드 목록(예: "L02-01-01"). null/empty 이면 미적용(전체 기준).
     *                     지정 시 getContents 와 동일한 EXISTS 패턴으로 "해당 제품에 연계된 콘텐츠"만 집계한다.
     *                     제품상세(/product/[slug]) Downloads 섹션 Document Type 필터의 유형별 검색결과 수용.
     */
    @Transactional(readOnly = true)
    public List<DownloadCenterDocTypeCountResponse> getDocTypeCounts(List<String> productCodes) {
        boolean hasProductCodes = productCodes != null && !productCodes.isEmpty();

        // getContents 의 productCodes EXISTS 조건과 동일 패턴 — 한 콘텐츠가 여러 제품에 매핑돼도 중복 집계되지 않는다.
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

        // 집계 결과를 docType → count 맵으로 수집.
        Map<String, Integer> countByDocType = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String docType = r[0] != null ? r[0].toString() : null;
            int count = r[1] != null ? ((Number) r[1]).intValue() : 0;
            if (docType != null) countByDocType.put(docType, count);
        }

        // 공통코드에 등록된 문서유형 전체를 항상 순회 — 매칭 없으면 count=0.
        List<DownloadCenterDocTypeCountResponse> result = new ArrayList<>();
        for (Map.Entry<String, String> docType : loadDocTypeLabels().entrySet()) {
            result.add(new DownloadCenterDocTypeCountResponse(
                docType.getKey(),
                docType.getValue(),
                countByDocType.getOrDefault(docType.getKey(), 0)));
        }
        return result;
    }

    // file_size(byte) → 사람이 읽는 단위(B/KB/MB/GB, 소수 2자리). null 이면 null.
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

    // ── 그룹핑용 가변 누적기(내부 전용) ──

    // 콘텐츠(master) 누적기: 메타 + 버전 맵(삽입 순서 = sort_key DESC).
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

    // 버전 누적기: 메타 + 파일 목록(조회 순서 = f.id ASC).
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

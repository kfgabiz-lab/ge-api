package com.ge.bo.service;

import com.ge.bo.dto.DownloadCenterCategoryCountResponse;
import com.ge.bo.dto.DownloadCenterDocTypeCountResponse;
import com.ge.bo.dto.DownloadCenterContentPageResponse;
import com.ge.bo.dto.DownloadCenterContentResponse;
import com.ge.bo.dto.DownloadCenterFileResponse;
import com.ge.bo.dto.DownloadCenterVersionResponse;
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
 * - 대상: 영상 제외(doc_type <> 'V') 문서 콘텐츠. 노출 게이트: master.expose + version.version_expose + file.file_expose + 삭제 아님.
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

    // 공통 게이트(master): 노출 + 미삭제 + 영상 제외 + "노출 버전 & 노출 파일 존재".
    // :q / :docTypes / :cats 는 호출부에서 조건부로 덧붙인다.
    private static final String MASTER_GATE =
        " m.expose = true AND m.is_deleted = false AND m.doc_type <> 'V'"
      + " AND EXISTS (SELECT 1 FROM contents_version v"
      + "   JOIN contents_file f ON f.contents_version_id = v.id"
      + "     AND f.file_expose = true AND f.is_deleted = false"
      + "   WHERE v.contents_id = m.id"
      + "     AND v.version_expose = true AND v.is_deleted = false)";

    // docType 표시 라벨 매핑.
    private static final Map<String, String> DOC_TYPE_LABELS = Map.of(
        "C", "Catalog",
        "M", "Manuals",
        "D", "Drawings",
        "S", "Software",
        "R", "Certificates",
        "O", "Tech Data");

    // 응답에 항상 포함하는 문서유형 순서(FE가 모든 유형을 항상 렌더링하도록 고정 순회).
    // OS/Firmware 는 대응 doc_type 이 없어 제외(FE 정적 표시, count=0 고정).
    private static final List<String> DOC_TYPE_ORDER = List.of("C", "M", "D", "S", "R", "O");


    /**
     * Download Center 목록(카드) 조회 — 키워드 + docType + LV2 카테고리 필터 + 정렬 페이징.
     *
     * @param q          키워드(제목 nahp_title/doc_title ILIKE). null/blank 이면 미적용.
     * @param categories 선택된 LV2 코드 목록(그룹 내 OR = IN). null/empty 이면 미적용.
     * @param docTypes   선택된 문서유형 코드 목록(IN). null/empty 이면 미적용.
     * @param sort       정렬: newest(기본)/oldest/title.
     * @param page       0-based 페이지.
     * @param size       페이지 크기(기본 12).
     */
    @Transactional(readOnly = true)
    public DownloadCenterContentPageResponse getContents(
            String q, List<String> categories, List<String> docTypes, String sort, int page, int size) {
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categories != null && !categories.isEmpty();
        boolean hasDocTypes = docTypes != null && !docTypes.isEmpty();

        // 공통 WHERE(게이트 + 조건부 키워드/문서유형/카테고리)
        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q");
        }
        if (hasDocTypes) {
            where.append(" AND m.doc_type IN (:docTypes)");
        }
        if (hasCats) {
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cc"
                + " WHERE cc.contents_id = m.id AND cc.category_l2_id IN (:cats))");
        }

        // ① 전체 건수
        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + where);
        if (hasQ) countQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasDocTypes) countQuery.setParameter("docTypes", docTypes);
        if (hasCats) countQuery.setParameter("cats", categories);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ② 현재 페이지 master.id 목록(정렬 적용)
        Query idQuery = entityManager.createNativeQuery(
            "SELECT m.id FROM contents_master m" + where + orderByClause(sort)
            + " LIMIT :size OFFSET :offset");
        if (hasQ) idQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasDocTypes) idQuery.setParameter("docTypes", docTypes);
        if (hasCats) idQuery.setParameter("cats", categories);
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

    // 정렬 절 — newest(기본)/oldest/title.
    private String orderByClause(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            case "oldest" -> " ORDER BY m.source_updated_at ASC NULLS LAST, m.id ASC";
            case "title" -> " ORDER BY COALESCE(m.nahp_title, m.doc_title) ASC, m.id ASC";
            default -> " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"; // newest
        };
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
            + "  f.source_system, f.file_path"
            + " FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + " JOIN contents_file f ON f.contents_version_id = v.id"
            + "   AND f.file_expose = true AND f.is_deleted = false"
            + " LEFT JOIN LATERAL ("
            + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
            + "   WHERE cc.contents_id = m.id LIMIT 1"
            + " ) c ON true"
            + " WHERE m.id IN (:pageIds)"
            + " ORDER BY m.id, v.sort_key DESC, f.id";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("pageIds", pageIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // masterId → 콘텐츠 누적기(버전 → 파일 목록). 삽입 순서 유지.
        Map<Long, ContentAcc> masterMap = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long masterId = ((Number) r[0]).longValue();
            ContentAcc acc = masterMap.computeIfAbsent(masterId, k -> {
                String docType = r[1] != null ? r[1].toString() : null;
                return new ContentAcc(
                    masterId,
                    docType,
                    DOC_TYPE_LABELS.get(docType),
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
                r[14] != null ? r[14].toString() : null));
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
        String sql = "SELECT c.category_l2_id, count(DISTINCT m.id)::int"
            + " FROM contents_master m"
            + " JOIN contents_category c ON c.contents_id = m.id"
            + " WHERE" + MASTER_GATE
            + " GROUP BY c.category_l2_id";

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<DownloadCenterCategoryCountResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new DownloadCenterCategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? ((Number) r[1]).intValue() : 0));
        }
        return result;
    }

    /**
     * 문서유형(docType)별 노출 콘텐츠 건수 — 필터 패널 문서유형 옆 숫자용.
     * - 게이트는 getCategoryCounts 와 동일(MASTER_GATE). GROUP BY m.doc_type 으로 실제 건수를 집계한 뒤,
     *   6개 문서유형(C/M/D/S/R/O) 순서를 항상 순회해 매칭 안 되면 count=0 으로 채워 반환한다(FE가 전 유형 렌더링).
     * - OS/Firmware 는 대응 doc_type 이 없어 제외(FE 정적 표시, count=0 고정).
     */
    @Transactional(readOnly = true)
    public List<DownloadCenterDocTypeCountResponse> getDocTypeCounts() {
        String sql = "SELECT m.doc_type, count(*)::int"
            + " FROM contents_master m"
            + " WHERE" + MASTER_GATE
            + " GROUP BY m.doc_type";

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // 집계 결과를 docType → count 맵으로 수집.
        Map<String, Integer> countByDocType = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String docType = r[0] != null ? r[0].toString() : null;
            int count = r[1] != null ? ((Number) r[1]).intValue() : 0;
            if (docType != null) countByDocType.put(docType, count);
        }

        // 6개 문서유형 전체를 항상 순회 — 매칭 없으면 count=0.
        List<DownloadCenterDocTypeCountResponse> result = new ArrayList<>();
        for (String docType : DOC_TYPE_ORDER) {
            result.add(new DownloadCenterDocTypeCountResponse(
                docType,
                DOC_TYPE_LABELS.get(docType),
                countByDocType.getOrDefault(docType, 0)));
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

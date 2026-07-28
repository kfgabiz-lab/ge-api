package com.ge.bo.service;

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

/**
 * FO Tech Hub(Support > Tech Hub) 콘텐츠 조회 서비스.
 * - 데이터 소스: contents_master/contents_version/contents_category (SSQ 배치 적재 테이블, page_data와 별개 도메인).
 *   → PageDataService(page_data 전용)와 분리해 전용 서비스로 둔다.
 * - 대상: 영상 콘텐츠(doc_type='V')만. 노출 게이트: master.expose AND version.version_expose AND 삭제 아님 AND video_url 존재.
 * - 카드 단위 = contents_master(다버전 시리즈도 1장), 대표 버전 = Chapter 1(sort_key 최대 = version_name '1').
 * - 정렬: source_updated_at DESC(원천 갱신일). contents_master에 site_id 컬럼이 없어 사이트 스코프 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechHubService {

    @PersistenceContext
    private EntityManager entityManager;

    // 영상 콘텐츠 공통 게이트(master) + "노출 버전 존재" 조건. :cats/:q/:certStds 는 호출부에서 조건부로 덧붙인다.
    // 통합 미디어 검색(MediaSearchService)에서도 Tech Hub 블록 게이트로 그대로 재사용하기 위해 public 으로 공개한다(동작 불변, 가시성만 확대).
    public static final String MASTER_GATE =
        " m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false"
      + " AND EXISTS (SELECT 1 FROM contents_version v WHERE v.contents_id = m.id"
      + "   AND v.version_expose = true AND v.is_deleted = false AND v.video_url IS NOT NULL AND v.video_url <> '')";

    // 인증(Certification) 원천값 — contents_master.attrs 안의 문자열 코드. 전용 컬럼 없음.
    private static final String CERT_STANDARD_EXPR = "m.attrs->>'video_prod_standard'";

    /**
     * 인증 코드(FO 필터 id) → video_prod_standard 코드값 매핑.
     * '3'(IEC/UL 동시 취득)은 UL·IEC 양쪽에 모두 해당하므로 두 목록에 함께 들어간다.
     * 비교는 문자열 IN 으로만 한다(정수 캐스팅 시 비숫자 값 유입되면 쿼리 자체가 예외).
     */
    private static final Map<String, List<String>> CERT_STANDARD_CODES = Map.of(
        "iec", List.of("1", "3"),
        "ul",  List.of("2", "3")
    );

    // cert-counts 응답 순서(FO 필터 패널 표기 순서와 동일: UL → IEC).
    private static final List<String> CERT_ORDER = List.of("ul", "iec");

    /**
     * Tech Hub 목록(카드) 조회 — 키워드(제목 LIKE) + LV2 카테고리 필터 + 인증 필터 + source_updated_at DESC 페이징.
     * 필터 그룹 간은 AND, 그룹 내 다중선택은 OR(IN) — 카테고리 필터와 동일 사상.
     *
     * @param q               키워드(제목 nahp_title/doc_title ILIKE). null/blank 이면 미적용.
     * @param categoryL2Ids   선택된 LV2 코드 목록(그룹 내 OR = IN). null/empty 이면 미적용.
     * @param certs           선택된 인증 코드 목록(ul/iec, 그룹 내 OR). null/empty/미인식 이면 미적용(기존 동작 유지).
     * @param page            0-based 페이지
     * @param size            페이지 크기(기본 12)
     */
    @Transactional(readOnly = true)
    public TechHubContentPageResponse getContents(String q, List<String> categoryL2Ids, List<String> certs,
                                                  int page, int size) {
        int safeSize = size <= 0 ? 12 : size;
        int safePage = Math.max(page, 0);
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCats = categoryL2Ids != null && !categoryL2Ids.isEmpty();
        List<String> certStds = resolveCertStandardCodes(certs);
        boolean hasCerts = certStds != null;

        // 공통 WHERE(게이트 + 조건부 키워드/카테고리/인증)
        StringBuilder where = new StringBuilder(" WHERE").append(MASTER_GATE);
        if (hasQ) {
            where.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q");
        }
        if (hasCats) {
            where.append(" AND EXISTS (SELECT 1 FROM contents_category cf"
                + " WHERE cf.contents_id = m.id AND cf.category_l2_id IN (:cats))");
        }
        if (hasCerts) {
            // attrs 없거나 값이 매핑 밖(NULL 포함)이면 자동 제외 — 인증 필터 선택 시 미분류 콘텐츠는 노출하지 않는다.
            where.append(" AND ").append(CERT_STANDARD_EXPR).append(" IN (:certStds)");
        }

        // ① 전체 건수
        Query countQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM contents_master m" + where);
        if (hasQ) countQuery.setParameter("q", "%" + q.trim() + "%");
        if (hasCats) countQuery.setParameter("cats", categoryL2Ids);
        if (hasCerts) countQuery.setParameter("certStds", certStds);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ② 현재 페이지 카드 목록
        // 대표 버전(rep) = Chapter 1(sort_key 최대). versionCount(vc) = 노출 버전 수. 카테고리(cat) = 표시용 1건.
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
            + " LEFT JOIN LATERAL ("
            + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
            + "   WHERE cc.contents_id = m.id AND cc.category_l2_id IS NOT NULL LIMIT 1"
            + " ) cat ON true"
            + where
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT :size OFFSET :offset";

        Query query = entityManager.createNativeQuery(sql);
        if (hasQ) query.setParameter("q", "%" + q.trim() + "%");
        if (hasCats) query.setParameter("cats", categoryL2Ids);
        if (hasCerts) query.setParameter("certStds", certStds);
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

    /**
     * Tech Hub 콘텐츠 상세 조회(콘텐츠 = contents_master 단위) — tech3.png
     * - 노출 게이트(master.expose + version.version_expose) 적용. 미존재/미노출/재생가능버전 없음 → null(컨트롤러 404).
     * - chapters: 노출 버전 전체(sort_key DESC = Chapter 1 먼저). 상세 API 1회로 전부 내려 FE가 클라이언트 전환.
     * - versionCount==1 이면 Related Video(동일 LV2 최신 3, 자기 제외)까지 함께 반환.
     */
    @Transactional(readOnly = true)
    public TechHubDetailResponse getContentDetail(Long masterId) {
        // ① 마스터(노출 게이트) + 대표 카테고리
        String masterSql = "SELECT m.id,"
            + "  COALESCE(m.nahp_title, m.doc_title)         AS title,"
            + "  to_char(m.source_updated_at, 'YYYY-MM-DD')  AS source_updated_at,"
            + "  cat.category_l1_id, cat.category_l2_id"
            + " FROM contents_master m"
            + " LEFT JOIN LATERAL ("
            + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
            + "   WHERE cc.contents_id = m.id AND cc.category_l2_id IS NOT NULL LIMIT 1"
            + " ) cat ON true"
            + " WHERE m.id = :id AND m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false";
        Query masterQuery = entityManager.createNativeQuery(masterSql);
        masterQuery.setParameter("id", masterId);
        @SuppressWarnings("unchecked")
        List<Object[]> masterRows = masterQuery.getResultList();
        if (masterRows.isEmpty()) return null; // 미존재/미노출

        Object[] mr = masterRows.get(0);
        String title = mr[1] != null ? mr[1].toString() : null;
        String sourceUpdatedAt = mr[2] != null ? mr[2].toString() : null;
        String categoryL1Id = mr[3] != null ? mr[3].toString() : null;
        String categoryL2Id = mr[4] != null ? mr[4].toString() : null;

        // ② 챕터(노출 버전) 목록 — sort_key DESC(Chapter 1 먼저)
        String chapterSql = "SELECT v.id, v.version_name, v.sort_key, v.video_url"
            + " FROM contents_version v"
            + " WHERE v.contents_id = :id AND v.version_expose = true AND v.is_deleted = false"
            + "   AND v.video_url IS NOT NULL AND v.video_url <> ''"
            + " ORDER BY v.sort_key DESC";
        Query chapterQuery = entityManager.createNativeQuery(chapterSql);
        chapterQuery.setParameter("id", masterId);
        @SuppressWarnings("unchecked")
        List<Object[]> chapterRows = chapterQuery.getResultList();
        if (chapterRows.isEmpty()) return null; // 재생 가능한 노출 버전 없음

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

        // ③ 단일버전이면 Related Video(동일 LV2 최신 3, 자기 제외)
        List<TechHubContentResponse> relatedVideos = new ArrayList<>();
        if (versionCount == 1 && categoryL2Id != null) {
            relatedVideos = findRelatedVideos(masterId, categoryL2Id);
        }

        return new TechHubDetailResponse(
            mr[0] != null ? ((Number) mr[0]).longValue() : null,
            title, sourceUpdatedAt, categoryL1Id, categoryL2Id,
            versionCount, chapters, relatedVideos);
    }

    // 동일 LV2 콘텐츠 최신 3건(자기 제외) — 카드 DTO(TechHubContentResponse) 재사용.
    private List<TechHubContentResponse> findRelatedVideos(Long selfId, String categoryL2Id) {
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
            + " LEFT JOIN LATERAL ("
            + "   SELECT cc.category_l1_id, cc.category_l2_id FROM contents_category cc"
            + "   WHERE cc.contents_id = m.id AND cc.category_l2_id IS NOT NULL LIMIT 1"
            + " ) cat ON true"
            + " WHERE m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false AND m.id <> :selfId"
            + "   AND EXISTS (SELECT 1 FROM contents_category cf"
            + "     WHERE cf.contents_id = m.id AND cf.category_l2_id = :l2)"
            + " ORDER BY m.source_updated_at DESC NULLS LAST, m.id DESC"
            + " LIMIT 3";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("selfId", selfId);
        query.setParameter("l2", categoryL2Id);
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

    /**
     * LV2 카테고리별 노출 영상 콘텐츠 건수 — 필터 아코디언 숫자(tech2.png)용.
     * LV1>LV2 계층 자체는 FE가 category-data로 구성하고, 여기 없는 LV2는 0으로 표시한다.
     */
    @Transactional(readOnly = true)
    public List<TechHubCategoryCountResponse> getCategoryCounts() {
        String sql = "SELECT c.category_l2_id, count(DISTINCT m.id)::int"
            + " FROM contents_master m"
            + " JOIN contents_version v ON v.contents_id = m.id"
            + "   AND v.version_expose = true AND v.is_deleted = false"
            + "   AND v.video_url IS NOT NULL AND v.video_url <> ''"
            + " JOIN contents_category c ON c.contents_id = m.id AND c.category_l2_id IS NOT NULL"
            + " WHERE m.doc_type = 'V' AND m.expose = true AND m.is_deleted = false"
            + " GROUP BY c.category_l2_id";

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<TechHubCategoryCountResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new TechHubCategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? ((Number) r[1]).intValue() : 0
            ));
        }
        return result;
    }

    /**
     * 인증(UL/IEC)별 노출 영상 콘텐츠 건수 — 필터 패널 숫자용.
     * - 게이트는 getCategoryCounts 와 동일(MASTER_GATE). video_prod_standard 코드값으로 GROUP BY 한 뒤
     *   '3'(IEC/UL 동시)을 UL·IEC 양쪽에 가산하므로 두 count 합 ≠ 전체 건수일 수 있다.
     * - UL/IEC 두 항목을 항상 순회해 매칭 없으면 count=0 으로 채워 반환한다(FE가 두 옵션 모두 렌더링).
     */
    @Transactional(readOnly = true)
    public List<TechHubCertCountResponse> getCertCounts() {
        String sql = "SELECT " + CERT_STANDARD_EXPR + " AS cert_std, count(*)::int"
            + " FROM contents_master m"
            + " WHERE" + MASTER_GATE
            + " GROUP BY " + CERT_STANDARD_EXPR;

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // 원천 코드값 → 건수 맵(값 없는 콘텐츠는 key=null 로 모이며 어느 인증에도 가산되지 않는다).
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

    /**
     * 요청 인증 코드(ul/iec) → 조회할 video_prod_standard 코드값 목록.
     * - 그룹 내 다중선택은 합집합(OR) — UL+IEC 동시 선택 시 ('1','2','3').
     * - 인식 못한 토큰은 무시하며, 유효 토큰이 하나도 없으면 null(필터 미적용 = 기존 동작).
     */
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

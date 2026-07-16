package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.dto.FoProductGroupResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FO 공개 제품 그룹(Discover Our Products) 조회 비즈니스 로직
 * - PageDataService 의 네이티브 JSONB 조회 패턴(WHERE data_slug, data_json 경로 접근, id IN 일괄조회) 재사용
 * - 2쿼리 배치로 N+1 방지:
 *   1) prdGrp-data 그룹 목록(isVisible=001) 조회
 *   2) 그룹들의 ms[].id 를 한 번에 product-data 에서 조회 → id→제품 맵 구성
 * - Java 에서 각 그룹의 ms(=[{id, sortOrder}])를 제품 상세로 치환 후 정렬
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoProductGroupService {

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** 그룹 조회 slug */
    private static final String GROUP_SLUG = "prdGrp-data";
    /** 제품 조회 slug */
    private static final String PRODUCT_SLUG = "product-data";

    /**
     * Discover Our Products — 공개 그룹 + 각 그룹 내 제품 목록 조회
     * @return 그룹 목록 (prdGrpOrd ASC, tie-breaker id ASC / 그룹 내 제품은 sortOrder ASC, tie-breaker id ASC)
     */
    @Transactional(readOnly = true)
    public List<FoProductGroupResponse> getProductGroups() {
        // 1) 공개(is_visible=001) 그룹 목록 조회 — product_group.group_order 오름차순(NULL/빈값은 마지막), 동률 시 id 오름차순
        //    구버전(테스트) 스키마는 is_visible 경로가 신 key(product_group) 아래에 없으므로 이 WHERE 에 자동 제외됨
        String groupSql = "SELECT id, data_json::text FROM page_data"
                + " WHERE data_slug = :slug"
                + " AND data_json->'product_group'->>'is_visible' = '001'"
                + " ORDER BY NULLIF(data_json->'product_group'->>'group_order','')::numeric ASC NULLS LAST, id ASC";
        Query groupQuery = entityManager.createNativeQuery(groupSql);
        groupQuery.setParameter("slug", GROUP_SLUG);

        @SuppressWarnings("unchecked")
        List<Object[]> groupRows = groupQuery.getResultList();
        if (groupRows.isEmpty()) {
            return Collections.emptyList();
        }

        // 그룹 파싱 + 전체 제품 id 수집 (N+1 방지용 배치 대상)
        List<GroupRaw> groups = new ArrayList<>();
        Set<Long> productIds = new LinkedHashSet<>();
        for (Object[] row : groupRows) {
            Long groupId = ((Number) row[0]).longValue();
            Map<String, Object> dataJson = parseJson(row[1]);
            Map<String, Object> form1 = asMap(dataJson.get("product_group"));

            GroupRaw g = new GroupRaw();
            g.id = groupId;
            g.prdGrpNm = asString(form1.get("group_name"));
            g.prdGrpOrd = asString(form1.get("group_order"));
            // ms = [{id, sortOrder}] — id/sortOrder 원본 추출
            for (Object item : asList(dataJson.get("ms"))) {
                Map<String, Object> ms = asMap(item);
                Long pid = asLong(ms.get("id"));
                if (pid == null) continue;
                MsRef ref = new MsRef();
                ref.productId = pid;
                ref.sortOrder = asString(ms.get("sort_order"));
                g.msRefs.add(ref);
                productIds.add(pid);
            }
            groups.add(g);
        }

        // 2) 수집한 제품 id 를 한 번에 조회 → id→제품 맵 구성 (N+1 방지)
        Map<Long, ProductRaw> productMap = fetchProducts(productIds);

        // 3) 각 그룹의 ms 를 제품 상세로 치환 + 정렬(dangling 제외)
        List<FoProductGroupResponse> result = new ArrayList<>();
        for (GroupRaw g : groups) {
            List<FoProductGroupResponse.Product> products = new ArrayList<>();
            for (MsRef ref : g.msRefs) {
                ProductRaw p = productMap.get(ref.productId);
                if (p == null) continue; // 존재하지 않는 제품 참조(dangling)는 제거
                products.add(FoProductGroupResponse.Product.builder()
                        .id(ref.productId)
                        .productNm(p.productNm)
                        .prdSubDesc(p.prdSubDesc)
                        .awards(p.awards)
                        .image(p.image)
                        .slug(p.slug)
                        .sortOrder(ref.sortOrder)
                        .build());
            }
            // 정렬: sortOrder(문자열→숫자) ASC, 동률 시 제품 id ASC
            products.sort(Comparator
                    .comparingDouble((FoProductGroupResponse.Product p) -> parseSortOrder(p.sortOrder()))
                    .thenComparingLong(FoProductGroupResponse.Product::id));

            result.add(FoProductGroupResponse.builder()
                    .id(g.id)
                    .prdGrpNm(g.prdGrpNm)
                    .prdGrpOrd(g.prdGrpOrd)
                    .ms(products)
                    .build());
        }
        return result;
    }

    /**
     * 제품 id 집합을 한 번에 조회하여 id→제품 상세 맵 구성 (N+1 방지)
     * - product-data 는 BO 등록 시점에 이미 필터링된 제품만 선택 가능한 구조라 별도 where 없음
     * - id 는 JSON 에서 파싱된 Long 값만 사용하므로 IN 절 인라인이 안전
     */
    private Map<Long, ProductRaw> fetchProducts(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String idList = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String productSql = "SELECT data_json::text FROM page_data"
                + " WHERE data_slug = :slug"
                + " AND (data_json->>'id')::bigint IN (" + idList + ")";
        Query productQuery = entityManager.createNativeQuery(productSql);
        productQuery.setParameter("slug", PRODUCT_SLUG);

        @SuppressWarnings("unchecked")
        List<Object> rows = productQuery.getResultList();

        Map<Long, ProductRaw> map = new LinkedHashMap<>();
        for (Object row : rows) {
            Map<String, Object> dataJson = parseJson(row);
            Long pid = asLong(dataJson.get("id"));
            if (pid == null) continue;
            Map<String, Object> form = asMap(dataJson.get("product"));
            Map<String, Object> info = asMap(dataJson.get("product_info")); // 대부분 미입력 → 빈 맵
            Map<String, Object> seo = asMap(dataJson.get("seo"));           // 대부분 미입력 → 빈 맵

            ProductRaw p = new ProductRaw();
            p.productNm = asString(form.get("product_name"));
            p.prdSubDesc = asString(form.get("product_description"));
            p.awards = asString(form.get("awards"));
            // product_info.image 는 미디어 ID 배열 → 첫 요소(미디어 ID)를 hero/banner 와 동일한 프록시 경로로 변환
            p.image = resolveFirstImageUrl(info.get("image"));
            p.slug = asString(seo.get("slug"));
            map.put(pid, p);
        }
        return map;
    }

    // ── private 헬퍼 ──────────────────────────────────────────

    /** data_json::text → Map 파싱 (실패 시 빈 맵) */
    private Map<String, Object> parseJson(Object rawText) {
        if (rawText == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(rawText.toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            log.warn("product-group dataJson 파싱 실패: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Object → Map (아니면 빈 맵) */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return Collections.emptyMap();
    }

    /** Object → List (아니면 빈 리스트) */
    private List<?> asList(Object obj) {
        if (obj instanceof List) return (List<?>) obj;
        return Collections.emptyList();
    }

    /** Object → String (null 이면 null, 빈 문자열은 그대로 유지) */
    private String asString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /** Object → Long (숫자/숫자문자열만, 실패 시 null) */
    private Long asLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * product_info.image(미디어 ID 배열)의 첫 요소를 FO 프록시 이미지 URL 로 변환
     * - hero/banner(BannerSwiper/VideoSwiper)가 쓰는 기존 프록시 경로 "/api/v1/fo/page-files/{id}" 재사용
     * - 배열이 없거나 비어있거나 첫 요소가 숫자로 파싱 안 되면 null (기존처럼 FE 플레이스홀더 폴백)
     */
    private String resolveFirstImageUrl(Object imageValue) {
        List<?> ids = asList(imageValue);
        if (ids.isEmpty()) return null;
        Long mediaId = asLong(ids.get(0));
        if (mediaId == null) return null;
        return "/api/v1/fo/page-files/" + mediaId;
    }

    /** sortOrder 문자열 → 정렬용 숫자 (빈값/파싱실패는 맨 뒤로) */
    private double parseSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(sortOrder.trim());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    /** 그룹 파싱 중간 표현 */
    private static class GroupRaw {
        Long id;
        String prdGrpNm;
        String prdGrpOrd;
        final List<MsRef> msRefs = new ArrayList<>();
    }

    /** 그룹 내 ms 항목(제품 참조 + 정렬값) 중간 표현 */
    private static class MsRef {
        Long productId;
        String sortOrder;
    }

    /** 제품 상세 중간 표현 */
    private static class ProductRaw {
        String productNm;
        String prdSubDesc;
        String awards;
        String image;
        String slug;
    }
}

package com.ge.bo.controller;

import com.ge.bo.dto.CategoryLv2RowResponse;
import com.ge.bo.dto.CategoryProductRowResponse;
import com.ge.bo.dto.ProductInsightRowResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FO 카테고리 랜딩 전용 API — 비로그인 전체 허용 (/api/v1/fo/**, FoProductController와 동일)
 * - 하위 카테고리 Lv2 목록(기획서 3번 요건): Lv1 맵핑 + Lv2 공개 + 하위 Lv3 제품 중 (공개+판매중) 1건 이상인 카테고리만 노출
 * - Highlights 인사이트(기획서 13번 요건): 위와 동일한 "노출가능 제품" 집합에 맵핑된 blog/press/articles 최신 3건
 * 두 조회 모두 PageDataService의 category-data/product-data 조인 컨벤션(CATEGORY_LV2_CTE)을 공유한다.
 */
@RestController
@RequestMapping("/api/v1/fo/categories")
@RequiredArgsConstructor
public class FoCategoryController {

    private final PageDataService pageDataService;

    /**
     * Lv1(categoryId) 하위 "노출가능 Lv2" 카테고리 목록 조회
     * GET /api/v1/fo/categories/{categoryId}/lv2
     * sortOrder 오름차순(숫자 캐스팅, 동률/미지정 시 id 오름차순) 정렬.
     */
    @GetMapping("/{categoryId}/lv2")
    public ResponseEntity<List<CategoryLv2RowResponse>> getLv2(
            @PathVariable Long categoryId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findCategoryLv2(categoryId, siteId));
    }

    /**
     * Lv1(categoryId) 기준 Highlights 인사이트(blog/press/articles) 최신 3건 조회
     * GET /api/v1/fo/categories/{categoryId}/insights
     * 응답 DTO는 제품상세 Insights(ProductInsightRowResponse)와 동일 구조를 재사용한다.
     */
    @GetMapping("/{categoryId}/insights")
    public ResponseEntity<List<ProductInsightRowResponse>> getInsights(
            @PathVariable Long categoryId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findCategoryInsights(categoryId, siteId));
    }

    /**
     * Lv2(categoryId) 하위 "노출가능 제품" 목록 조회 — FO Lv2 카테고리 랜딩 제품 카드
     * GET /api/v1/fo/categories/{categoryId}/products
     * 연결행 sortOrder 오름차순(동률/미지정 시 제품 id 오름차순) 정렬, 건수 제한 없음.
     */
    @GetMapping("/{categoryId}/products")
    public ResponseEntity<List<CategoryProductRowResponse>> getProducts(
            @PathVariable Long categoryId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findCategoryProducts(categoryId, siteId));
    }

    /**
     * Lv2(categoryId) "자신" 기준 Highlights 인사이트 조회 — FO Lv2 카테고리 랜딩
     * GET /api/v1/fo/categories/{categoryId}/lv2-insights
     * Lv1용 /insights 와 응답 구조는 동일하고, 대상 제품 집합만 Lv2 직접 맵핑으로 다르다.
     */
    @GetMapping("/{categoryId}/lv2-insights")
    public ResponseEntity<List<ProductInsightRowResponse>> getLv2Insights(
            @PathVariable Long categoryId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findCategoryInsightsBySelf(categoryId, siteId));
    }
}

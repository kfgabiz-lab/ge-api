package com.ge.bo.controller;

import com.ge.bo.dto.FoProductSearchResponse;
import com.ge.bo.dto.ProductInsightRowResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FO 제품상세 전용 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - 제품 담당 배너(스펙 11~13): 제품에 맵핑된 담당자 이메일(productManager-data.ms 배열 포함) 서버 필터 조회
 * - 제품 Insights(스펙 38): 제품에 맵핑된 게시글(blog/press/articles, product_list 배열 포함) 서버 필터 조회
 * 두 조회 모두 PageDataService의 "data_json->'...' @> to_jsonb(:id)" 컨벤션(배열 포함)을 재사용한다.
 */
@RestController
@RequestMapping("/api/v1/fo/products")
@RequiredArgsConstructor
public class FoProductController {

    private final PageDataService pageDataService;

    /**
     * 제품 담당자 이메일 조회 (productManager-data.ms 배열에 productId 포함 + is_visible=001)
     * GET /api/v1/fo/products/{productId}/manager-email
     * 매칭 담당자 없으면 {"email": null} 반환 → FE는 축약형 배너(이메일/복사 미노출)로 처리.
     */
    @GetMapping("/{productId}/manager-email")
    public ResponseEntity<Map<String, String>> getManagerEmail(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        Map<String, String> body = new HashMap<>();
        body.put("email", pageDataService.findProductManagerEmail(productId, siteId).orElse(null));
        return ResponseEntity.ok(body);
    }

    /**
     * 제품 맵핑 게시글(blog/press/articles) 최신 3건 조회
     * GET /api/v1/fo/products/{productId}/insights
     * product_list 배열에 productId 포함 + 공개 + 게시일 과거 조건, 게시일 내림차순(동률 id 내림차순) 상위 3건.
     */
    @GetMapping("/{productId}/insights")
    public ResponseEntity<List<ProductInsightRowResponse>> getInsights(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findProductInsights(productId, siteId));
    }

    /**
     * 제품 키워드/카테고리 검색 (제품명/제품설명 부분일치 + 카테고리 필터 + is_visible=001 공개)
     * GET /api/v1/fo/products/search?q={키워드}&categories={콤마구분 depth2 row_id}&offset={기본0}&limit={기본4}
     * 응답: { total: 전체 매칭 건수, items: [{id, productName, productDescription, image, slug}] }
     * - categories: depth2 category-data row_id(page_data.id) 콤마구분 목록(예: 595,597,602). 미지정/빈값이면 카테고리 필터 없음.
     * - q, categories 둘 다 비어있으면 total=0, items=[] 즉시 반환(전체 덤프 방지).
     * - 하위호환: categories/offset 미지정 시 기존(q=&limit=4)과 동일 동작.
     */
    @GetMapping("/search")
    public ResponseEntity<FoProductSearchResponse> searchProducts(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "4") int limit,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        // categories 콤마 문자열 → List<Long>. 미지정/빈값/공백은 카테고리 필터 없음(빈 리스트).
        List<Long> categoryIds = new ArrayList<>();
        if (categories != null && !categories.isBlank()) {
            for (String token : categories.split(",")) {
                String t = token.trim();
                if (!t.isEmpty()) {
                    categoryIds.add(Long.parseLong(t));
                }
            }
        }
        return ResponseEntity.ok(pageDataService.searchProducts(q, categoryIds, offset, limit, siteId));
    }
}

package com.ge.bo.controller;

import com.ge.bo.dto.FoProductCategoryCountResponse;
import com.ge.bo.dto.FoProductRelevanceRowResponse;
import com.ge.bo.dto.FoProductSearchResponse;
import com.ge.bo.dto.ProductInsightRowResponse;
import com.ge.bo.service.FoProductRelevanceService;
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

@RestController
@RequestMapping("/api/v1/fo/products")
@RequiredArgsConstructor
public class FoProductController {

    private final PageDataService pageDataService;
    private final FoProductRelevanceService foProductRelevanceService;

    @GetMapping("/{slug}/relevant-products")
    public ResponseEntity<List<FoProductRelevanceRowResponse>> getRelevantProducts(
            @PathVariable String slug,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(foProductRelevanceService.findRelevantProducts(slug, siteId));
    }

    @GetMapping("/{productId}/manager-email")
    public ResponseEntity<Map<String, String>> getManagerEmail(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        Map<String, String> body = new HashMap<>();
        body.put("email", pageDataService.findProductManagerEmail(productId, siteId).orElse(null));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{productId}/insights")
    public ResponseEntity<List<ProductInsightRowResponse>> getInsights(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findProductInsights(productId, siteId));
    }

    @GetMapping("/search")
    public ResponseEntity<FoProductSearchResponse> searchProducts(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "4") int limit,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
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

    @GetMapping("/category-counts")
    public List<FoProductCategoryCountResponse> categoryCounts() {
        return pageDataService.getProductCategoryCounts();
    }
}

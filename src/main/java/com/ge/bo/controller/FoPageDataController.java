package com.ge.bo.controller;

import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * FO 공개 페이지 데이터 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - PageDataService.search() 를 그대로 재사용하는 얇은 래퍼 (FO 공개 조회 전용)
 * - 관리자용 PageDataController(/api/v1/page-data/{slug}) 와 동일한 조회 로직을 위임만 함
 */
@RestController
@RequestMapping("/api/v1/fo/page-data/{slug}")
@RequiredArgsConstructor
public class FoPageDataController {

    private final PageDataService pageDataService;

    /**
     * 목록 조회 — 페이지네이션 + 동적 JSONB 검색 (관리자 API 와 동일)
     * GET /api/v1/fo/page-data/{slug}
     * Query Params: page(기본 0), size(기본 20), sort, unpaged(기본 false — true면 size 무시하고 조건에 맞는 전체 반환), 그 외는 검색 조건
     */
    @GetMapping
    public ResponseEntity<PageDataListResponse> search(
            @PathVariable String slug,
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unpaged,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.search(slug, allParams, page, size, siteId, unpaged));
    }
}

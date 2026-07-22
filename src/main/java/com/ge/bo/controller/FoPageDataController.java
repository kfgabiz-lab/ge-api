package com.ge.bo.controller;

import com.ge.bo.dto.AdjacentResponse;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 상세 단건 조회 — 목록(search) content[0]과 동일한 PageDataResponse 반환
     * GET /api/v1/fo/page-data/{slug}/{id}
     * 데이터가 없거나 상태 게이트 조건을 통과하지 못하면 404
     * (id는 Long이므로 숫자 경로만 매칭 — 비숫자 경로는 타입 변환 실패로 400)
     */
    @GetMapping("/{id}")
    public ResponseEntity<PageDataResponse> detail(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        PageDataResponse response = pageDataService.findPublicDetail(slug, id, allParams, siteId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    /**
     * 조회수 증가 — 상세 페이지 진입 시 count를 원자적으로 +1 (fire-and-forget, 바디 없음)
     * POST /api/v1/fo/page-data/{slug}/{id}/view-count
     * 존재/미존재 무관하게 항상 204 반환(열거 공격 방지) — 상세조회(detail)와 완전히 분리된 write 전용
     * (id는 Long이므로 숫자 경로만 매칭 — 비숫자 경로는 타입 변환 실패로 400)
     */
    @PostMapping("/{id}/view-count")
    public ResponseEntity<Void> incrementViewCount(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        pageDataService.incrementViewCount(slug, id, siteId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 인접글(이전/다음) 조회 — 정렬 기준으로 prev/next를 각 1건씩 반환
     * GET /api/v1/fo/page-data/{slug}/{id}/adjacent?sortField=...&titleField=...
     * 응답: {"prev":{"id","title"}|null, "next":{"id","title"}|null}
     */
    @GetMapping("/{id}/adjacent")
    public ResponseEntity<AdjacentResponse> adjacent(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestParam String sortField,
            @RequestParam String titleField,
            @RequestParam Map<String, String> allParams,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(
                pageDataService.findAdjacent(slug, id, sortField, titleField, allParams, siteId));
    }
}

package com.ge.bo.controller;

import com.ge.bo.dto.PageSearchResponse;
import com.ge.bo.service.PageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 통합검색 Pages 탭 API — 비로그인 전체 허용(/api/v1/fo/**, SecurityConfig permitAll).
 * - integration_contents 중 type 이 없는(NULL/빈문자) 행만 검색한다(type 'B'/'P'/'A' 는 media-search 담당).
 * - 얇은 위임 컨트롤러: 쿼리 조립/정렬/페이징은 모두 PageSearchService.
 */
@RestController
@RequestMapping("/api/v1/fo/page-search")
@RequiredArgsConstructor
public class FoPageSearchController {

    private final PageSearchService pageSearchService;

    /**
     * Pages 검색
     * GET /api/v1/fo/page-search?q={키워드}&page=0&size=10 (Header X-Site-Id optional)
     * - q: 제목/본문 부분일치(대소문자 무시). 미지정 시 전체.
     * - 정렬 updated_at DESC, id DESC. 페이지 크기 기본 10.
     */
    @GetMapping
    public ResponseEntity<PageSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageSearchService.search(q, page, size, siteId));
    }
}

package com.ge.bo.controller;

import com.ge.bo.dto.MediaSearchResponse;
import com.ge.bo.service.MediaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 통합 미디어 검색 API — 비로그인 전체 허용(/api/v1/fo/**, SecurityConfig permitAll).
 * - 4개 소스(Tech Hub 영상 + page_data blog/press/articles)를 단일 UNION 쿼리로 합쳐 검색/페이징한다.
 * - 얇은 위임 컨트롤러: 파싱/조립/정렬은 모두 MediaSearchService.
 */
@RestController
@RequestMapping("/api/v1/fo/media-search")
@RequiredArgsConstructor
public class FoMediaSearchController {

    private final MediaSearchService mediaSearchService;

    /**
     * 통합 미디어 검색
     * GET /api/v1/fo/media-search?q={키워드}&sources={CSV}&page=0&size=20 (Header X-Site-Id optional)
     * - q: 제목/본문 부분일치(대소문자 무시). 미지정 시 전체.
     * - sources: TECH_HUB,BLOG,PRESS,ARTICLE 중 CSV. 미지정 시 4개 전체.
     * - 정렬 sort_date DESC, 페이지 크기 기본 20.
     */
    @GetMapping
    public ResponseEntity<MediaSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sources,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(mediaSearchService.search(q, sources, page, size, siteId));
    }
}

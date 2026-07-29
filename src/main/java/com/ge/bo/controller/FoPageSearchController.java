package com.ge.bo.controller;

import com.ge.bo.dto.PageSearchResponse;
import com.ge.bo.service.PageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 통합검색 Pages 탭 API — 비로그인 전체 허용(/api/v1/fo/**, SecurityConfig permitAll).
 *
 * <p>원천은 관리자 기능 "검색관리"(search_manage / search_manage_text)다.
 * is_active=true 인 페이지만 대상이며, 결과는 URL 기준으로 중복 제거되어 내려간다.
 * 얇은 위임 컨트롤러 — 쿼리 조립/중복제거/정렬/페이징은 모두 {@link PageSearchService} 담당.</p>
 */
@RestController
@RequestMapping("/api/v1/fo/page-search")
@RequiredArgsConstructor
public class FoPageSearchController {

    private final PageSearchService pageSearchService;

    /**
     * Pages 검색
     * GET /api/v1/fo/page-search?q={키워드}&sections={CSV}&page=0&size=10
     * - q: 검색텍스트의 title 또는 text 부분일치(대소문자 무시). 미지정 시 활성 페이지 전체.
     * - sections: 분류(page_section) 코드 CSV(예 "MARKETS,SERVICE"). 미지정/빈 값이면 필터 미적용(전체).
     *   Media 검색(FoMediaSearchController)의 sources 파라미터와 동일한 CSV 스타일.
     * - 정렬 search_manage.updated_at DESC, id DESC. 페이지 크기 기본 10.
     * - search_manage 에는 site 스코프 컬럼이 없어 X-Site-Id 는 받지 않는다.
     */
    @GetMapping
    public ResponseEntity<PageSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sections,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(pageSearchService.search(q, sections, page, size));
    }
}

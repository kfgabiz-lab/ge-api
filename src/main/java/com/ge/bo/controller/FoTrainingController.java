package com.ge.bo.controller;

import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.service.FoTrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 공개 커리큘럼(교육) API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - FoTrainingService 에 위임하는 얇은 래퍼
 * - 카테고리(category-data id) 기준으로 연결된 공개 커리큘럼(currMgmt-data) 목록을 조회한다.
 */
@RestController
@RequestMapping("/api/v1/fo/training")
@RequiredArgsConstructor
public class FoTrainingController {

    private final FoTrainingService foTrainingService;

    /**
     * 카테고리별 커리큘럼 목록 조회
     * GET /api/v1/fo/training/curriculum-by-category
     * @param categoryIds    콤마구분 category-data id 목록 (필수, 예: "1731,1732,1745")
     *                       - 상위 묶음(depth1/depth2)에 속한 리프 id 여러 개를 한꺼번에 매칭 가능
     *                       - 단일값(콤마 없음)도 그대로 동작
     *                       - 비숫자/빈 목록이면 서비스에서 400 처리
     * @param trainingCourse 교육과정 코드(01/02/03, 선택)
     * @param page           0-based 페이지 번호 (기본 0)
     * @param size           페이지 크기 (기본 10)
     * @param siteId         사이트 스코프 (X-Site-Id 헤더, 선택)
     */
    @GetMapping("/curriculum-by-category")
    public ResponseEntity<PageDataListResponse> getCurriculumByCategory(
            @RequestParam String categoryIds,
            @RequestParam(required = false) String trainingCourse,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(
                foTrainingService.findCurriculumByCategory(categoryIds, trainingCourse, page, size, siteId));
    }
}

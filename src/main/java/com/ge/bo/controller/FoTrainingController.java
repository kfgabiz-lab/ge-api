package com.ge.bo.controller;

import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.TrainingProductTreeResponse;
import com.ge.bo.dto.TrainingRequestSubmitRequest;
import com.ge.bo.dto.TrainingRequestSubmitResponse;
import com.ge.bo.service.FoTrainingService;
import com.ge.bo.service.PageDataService;
import com.ge.bo.service.TrainingRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final PageDataService pageDataService;
    private final TrainingRequestService trainingRequestService;

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

    /**
     * Training Request(Step4) 제품 선택 트리 조회 — Power/Automation 각각
     * Lv1(category) > Lv2(category) > 제품(has_training='001') 구조, 빈 가지 제외.
     * GET /api/v1/fo/training/product-tree
     */
    @GetMapping("/product-tree")
    public ResponseEntity<TrainingProductTreeResponse> getProductTree(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findTrainingProductTree(siteId));
    }

    /**
     * Training Request(비정기 교육 신청) 제출 — Step1~4 입력값 저장
     * POST /api/v1/fo/training/requests
     * 인증 불필요 (SecurityConfig 에서 /api/v1/fo/** permitAll)
     */
    @PostMapping("/requests")
    public ResponseEntity<TrainingRequestSubmitResponse> submitRequest(
            @Valid @RequestBody TrainingRequestSubmitRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(trainingRequestService.submit(request, getClientIp(httpRequest)));
    }

    /**
     * 실제 클라이언트 IP 추출 — 리버스 프록시 환경에서는 X-Forwarded-For 헤더 우선
     * (TrainingRegistrationController 의 동일 로직 복제)
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            // 여러 IP가 콤마로 연결된 경우 첫 번째가 실제 클라이언트 IP
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

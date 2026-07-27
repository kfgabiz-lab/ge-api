package com.ge.bo.controller;

import com.ge.bo.dto.TrainingRegistrationRequest;
import com.ge.bo.dto.TrainingRegistrationResponse;
import com.ge.bo.service.TrainingRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 트레이닝 세션 등록(리드 캡처) 접수 API
 * - POST /api/v1/fo/training/registrations — 세션 상세 Registration Form 제출 저장
 * - 인증 불필요 (SecurityConfig 에서 /api/v1/fo/** permitAll)
 * ContactUsInquiryController 패턴 그대로(getClientIp 복제 재사용).
 */
@RestController
@RequestMapping("/api/v1/fo/training/registrations")
@RequiredArgsConstructor
public class TrainingRegistrationController {

    private final TrainingRegistrationService trainingRegistrationService;

    @PostMapping
    public ResponseEntity<TrainingRegistrationResponse> submit(
            @Valid @RequestBody TrainingRegistrationRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        TrainingRegistrationResponse response = trainingRegistrationService.submit(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 실제 클라이언트 IP 추출 — 리버스 프록시 환경에서는 X-Forwarded-For 헤더 우선
     * (ContactUsInquiryController 의 동일 로직 복제)
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

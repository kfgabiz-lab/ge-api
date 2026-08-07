package com.ge.bo.controller;

import com.ge.bo.common.util.ClientIpUtils;
import com.ge.bo.dto.TrainingRegistrationRequest;
import com.ge.bo.dto.TrainingRegistrationResponse;
import com.ge.bo.service.TrainingRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        String clientIp = ClientIpUtils.resolve(httpRequest);
        TrainingRegistrationResponse response = trainingRegistrationService.submit(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

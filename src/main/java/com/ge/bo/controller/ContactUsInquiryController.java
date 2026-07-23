package com.ge.bo.controller;

import com.ge.bo.dto.ContactUsInquiryRequest;
import com.ge.bo.dto.ContactUsInquiryResponse;
import com.ge.bo.service.ContactUsInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO Contact Us 문의 접수 API
 * - POST /api/v1/fo/contact-us — 폼 제출 내용을 CTP(Salesforce)로 전송한다 (DB 저장은 비활성화 상태)
 * - 인증 불필요 (SecurityConfig에서 /api/v1/fo/** permitAll)
 * ※ 기존 CTP 전용 ContactUsController(/api/v1/public/contact-us)와는 완전히 별개 — devices-tree 기반 카테고리/담당자 조회가 추가된 신규 연동
 */
@RestController
@RequestMapping("/api/v1/fo/contact-us")
@RequiredArgsConstructor
public class ContactUsInquiryController {

    private final ContactUsInquiryService contactUsInquiryService;

    @PostMapping
    public ResponseEntity<ContactUsInquiryResponse> submit(
            @Valid @RequestBody ContactUsInquiryRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        ContactUsInquiryResponse response = contactUsInquiryService.submit(request, clientIp, siteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 실제 클라이언트 IP 추출 — 리버스 프록시 환경에서는 X-Forwarded-For 헤더 우선
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

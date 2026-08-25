package com.ge.bo.controller;

import com.ge.bo.dto.ContactUsDetailRequest;
import com.ge.bo.dto.ContactUsDetailResponse;
import com.ge.bo.service.ContactUsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contact Us 문의결과조회 API (IF_SRR_NAHP_CTP_0002)
 * 인증 없이 접근 가능 — SecurityConfig에서 /api/v1/public/** permitAll 처리됨
 * ※ 문의 접수(submit)는 신규 폼(ContactUsInquiryController, /api/v1/fo/contact-us)으로 일원화되어
 *   이 컨트롤러에서는 제거됨 — FE에서 호출하지 않는 미사용 엔드포인트였음(2026-08-25 정리)
 */
@RestController
@RequestMapping("/api/v1/public/contact-us")
@RequiredArgsConstructor
public class ContactUsController {

    private final ContactUsService contactUsService;

    @PostMapping("/answer")
    public ResponseEntity<ContactUsDetailResponse> getDetail(@Valid @RequestBody ContactUsDetailRequest request) {
        return ResponseEntity.ok(contactUsService.getDetail(request));
    }
}

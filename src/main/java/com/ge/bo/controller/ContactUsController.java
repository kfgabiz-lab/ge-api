package com.ge.bo.controller;

import com.ge.bo.dto.ContactUsDetailRequest;
import com.ge.bo.dto.ContactUsDetailResponse;
import com.ge.bo.dto.ContactUsRequest;
import com.ge.bo.dto.ContactUsResponse;
import com.ge.bo.service.ContactUsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contact Us(북미 홈페이지 문의 접수) API (IF_SRR_NAHP_CTP_0001)
 * 인증 없이 접근 가능 — SecurityConfig에서 /api/v1/public/** permitAll 처리됨
 */
@RestController
@RequestMapping("/api/v1/public/contact-us")
@RequiredArgsConstructor
public class ContactUsController {

    private final ContactUsService contactUsService;

    @PostMapping
    public ResponseEntity<ContactUsResponse> submit(@Valid @RequestBody ContactUsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactUsService.submit(request));
    }

    @PostMapping("/answer")
    public ResponseEntity<ContactUsDetailResponse> getDetail(@Valid @RequestBody ContactUsDetailRequest request) {
        return ResponseEntity.ok(contactUsService.getDetail(request));
    }
}

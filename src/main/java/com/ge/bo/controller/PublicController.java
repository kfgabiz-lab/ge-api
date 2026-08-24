package com.ge.bo.controller;

import com.ge.bo.dto.CodeGroupResponse;
import com.ge.bo.dto.ServerTimeResponse;
import com.ge.bo.service.CaptchaService;
import com.ge.bo.service.CaptchaService.CaptchaResponse;
import com.ge.bo.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 인증 없이 접근 가능한 공개 API
 * SecurityConfig에서 /api/v1/public/** 전체 permitAll 처리됨
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

  private final CodeService codeService;
  private final CaptchaService captchaService;

    /* 공통코드 전체 조회 — 인증 불필요 */
  @GetMapping("/codes")
    public ResponseEntity<List<CodeGroupResponse>> getCodes() {
    return ResponseEntity.ok(codeService.getAllGroups());
  }

    /* 서버 기준시각 조회 — 인증 불필요, 캐시 금지 */
  @GetMapping("/server-time")
    public ResponseEntity<ServerTimeResponse> getServerTime() {
    Instant now = Instant.now();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new ServerTimeResponse(now.toEpochMilli(), now.toString()));
  }

    /*
     * 자체 캡차 발급 — 인증 불필요. SVG 이미지(data URI)와 정답+발급시각을 담은 암호화 토큰을 반환한다.
     * 서버 상태를 전혀 저장하지 않는 stateless 방식(ls.redis-enabled 분기, FO 별도 오리진 여부와 무관하게 동일 동작) —
     * FE는 captchaToken을 보관해뒀다가 제출 시 captchaCode와 함께 그대로 되돌려준다.
     * BO 로그인 / FO 트레이닝 폼 등 reCAPTCHA를 대체하는 모든 화면에서 공용으로 사용한다.
     */
  @GetMapping("/captcha-image")
    public ResponseEntity<CaptchaResponse> getCaptchaImage() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(captchaService.generate());
  }
}

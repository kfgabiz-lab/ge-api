package com.ge.bo.controller;

import com.ge.bo.dto.PreviewTokenVerifyResponse;
import com.ge.bo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FO 공개 미리보기 토큰 검증 API — 비로그인 전체 허용 (/api/v1/fo/preview-tokens)
 * PreviewTokenController(BO)가 발급한 토큰이 요청된 slug+recordId와 일치하는지 검증만 수행한다.
 */
@RestController
@RequestMapping("/api/v1/fo/preview-tokens")
@RequiredArgsConstructor
public class FoPreviewTokenController {

  private final JwtTokenProvider jwtTokenProvider;

  @GetMapping("/verify")
  public ResponseEntity<PreviewTokenVerifyResponse> verify(
      @RequestParam String token,
      @RequestParam String slug,
      @RequestParam String recordId) {
    boolean valid = jwtTokenProvider.validatePreviewToken(token, slug, recordId);
    return ResponseEntity.ok(new PreviewTokenVerifyResponse(valid));
  }
}

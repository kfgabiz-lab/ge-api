package com.ge.bo.controller;

import com.ge.bo.dto.PreviewTokenRequest;
import com.ge.bo.dto.PreviewTokenResponse;
import com.ge.bo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * BO 관리자용 미리보기 토큰 발급 API — 인증 필요 (/api/v1/preview-tokens)
 * 비공개(미승인/예약게시 미도래) 상태의 컨텐츠를 FO에서 미리 볼 수 있도록,
 * 특정 slug+recordId 1건만 조회 가능한 단기(5분) JWT를 발급한다.
 */
@RestController
@RequestMapping("/api/v1/preview-tokens")
@RequiredArgsConstructor
public class PreviewTokenController {

  private final JwtTokenProvider jwtTokenProvider;

  @PostMapping
  public ResponseEntity<PreviewTokenResponse> issue(@RequestBody PreviewTokenRequest request) {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    String token = jwtTokenProvider.generatePreviewToken(email, request.getSlug(), request.getRecordId());
    return ResponseEntity.ok(new PreviewTokenResponse(token, 5 * 60));
  }
}

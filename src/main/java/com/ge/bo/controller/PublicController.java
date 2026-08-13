package com.ge.bo.controller;

import com.ge.bo.dto.CodeGroupResponse;
import com.ge.bo.dto.ServerTimeResponse;
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
}

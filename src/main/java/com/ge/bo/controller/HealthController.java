package com.ge.bo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버 상태 확인용 헬스 체크 API
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> healthCheck() {

    return ResponseEntity.ok(Map.of(
            "status", "OK"
    ));
  }
}

package com.ge.bo.controller;

import com.ge.bo.common.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 서버 상태 확인용 헬스 체크 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HealthController {

  private final MailService mailService;

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> healthCheck() {

    // 이메일 전송 테스트.
    mailService.sendMail(
            "abbgnog@gmail.com",
            "테스트 메일",
            "<h1>안녕하세요</h1><p>HTML 메일입니다.</p>"
    );

    return ResponseEntity.ok(Map.of(
            "status", "OK",
            "message", "bo America 백오피스 API 서버가 정상 동작 중입니다.",
            "timestamp", LocalDateTime.now().toString()));
  }
}

package com.ge.bo.controller;

import com.ge.bo.common.mail.MailService;
import com.ge.bo.repository.EmailSendHisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버 상태 확인용 헬스 체크 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

  private final MailService mailService;

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> healthCheck() {

    try {
      // 임시 메일 테스트
      mailService.sendMail("comgsu@ls-electric.com", "test", "01", "01", null, 1L);
    }catch (Exception e){
      log.error(e.getMessage());
    }
    return ResponseEntity.ok(Map.of(
            "status", "OK"
    ));
  }
}

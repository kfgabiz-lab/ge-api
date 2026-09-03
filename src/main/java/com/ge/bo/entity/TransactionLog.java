package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 트랜잭션 로그 엔티티 — transaction_log 테이블 매핑
 * 이력성 테이블이므로 수정 없이 저장만 한다
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "transaction_log")
public class TransactionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 변경 유형: CREATE / UPDATE / DELETE */
  @Column(nullable = false, length = 10)
  private String actionType;

  /** HTTP 메서드: POST / PUT / PATCH / DELETE */
  @Column(nullable = false, length = 10)
  private String method;

  /** 요청 URL (쿼리스트링 포함) */
  @Column(nullable = false, length = 500)
  private String requestUrl;

  /** 요청 바디 JSON (민감 정보 마스킹 후 저장) */
  @Column(columnDefinition = "TEXT")
  private String requestBody;

  /** 응답 HTTP 상태코드 */
  @Column(nullable = false)
  private Integer httpStatus;

  /** 로그인 사용자 이메일 (비로그인 시 NULL) */
  @Column(length = 100)
  private String loginUser;

  /** 클라이언트 IP (getRemoteAddr() 기준) */
  @Column(length = 50)
  private String clientIp;

  /** 요청 처리 시간(ms) */
  private Long durationMs;

  /** 요청 사이트 ID (X-Site-Id 헤더 기준, 헤더 없으면 null) */
  @Column(name = "site_id")
  private Long siteId;

  /** 요청 출처: BO / FO / BATCH / EXTERNAL */
  @Column(length = 10)
  private String source;

  /** 발생일시 */
  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Builder
  public TransactionLog(String actionType, String method, String requestUrl,
      String requestBody, Integer httpStatus, String loginUser,
      String clientIp, Long durationMs, Long siteId, String source) {
    this.actionType = actionType;
    this.method = method;
    this.requestUrl = requestUrl;
    this.requestBody = requestBody;
    this.httpStatus = httpStatus;
    this.loginUser = loginUser;
    this.clientIp = clientIp;
    this.durationMs = durationMs;
    this.siteId = siteId;
    this.source = source;
    this.createdAt = OffsetDateTime.now();
  }
}

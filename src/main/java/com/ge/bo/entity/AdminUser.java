package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "admin_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AdminUser {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(nullable = false, unique = true, length = 100)
    private String email;

  @Column(nullable = false, length = 50)
    private String name;

  @Column(nullable = false, length = 255)
    private String passwordHash;

  @Column(nullable = false, length = 20, name = "role_code")
    private String role;

    /* 부서코드 */
  @Column(length = 50)
    private String deptCode;

    /* 부서명 */
  @Column(length = 100)
    private String deptName;

    /* 비고 */
  @Column(length = 500)
    private String remark;

  private LocalDateTime lastLoginAt;

  @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true;

  @Builder.Default
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

  private LocalDateTime lockedUntil;

  /** TOTP 비밀키 (Base32 인코딩, 최초 QR 등록 시 생성) */
  @Column(name = "totp_secret", columnDefinition = "TEXT")
  private String totpSecret;

  /** 2FA 활성화 여부 (QR 등록 + 코드 확인 완료 시 true) */
  @Builder.Default
  @Column(name = "is_totp_enabled", nullable = false)
  private boolean totpEnabled = false;

  @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /* 등록일 */
  @Column(nullable = false, name = "reg_date", updatable = false)
    private LocalDate regDt;

    /* 등록시간 */
  @Column(nullable = false, name = "reg_time", updatable = false)
    private LocalTime regTm;

    /* 저장 전 등록일/등록시간 자동 세팅 (KST 기준) */
  @PrePersist
    private void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    this.regDt = now.toLocalDate();
    this.regTm = now.toLocalTime();
  }
}

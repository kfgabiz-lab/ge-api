package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 접속이력 엔티티 — login_log 테이블 매핑
 * 이력성 테이블이므로 수정 없이 저장만 한다
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "login_log")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인한 관리자 ID (탈퇴/삭제 시 null로 보존) */
    @Column(name = "admin_user_id")
    private Long adminUserId;

    /** 로그인 시도 이메일 (FK가 null 되어도 보존) */
    @Column(nullable = false, length = 100)
    private String loginEmail;

    /** 로그인 결과: SUCCESS / FAIL */
    @Column(nullable = false, length = 10)
    private String status;

    /** 실패 사유 (성공 시 null) */
    @Column(length = 100)
    private String failReason;

    /** 클라이언트 IP (X-Forwarded-For 우선) */
    @Column(length = 50)
    private String clientIp;

    /** 브라우저 User-Agent */
    @Column(length = 500)
    private String userAgent;

    /** 시도 일시 */
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public LoginLog(Long adminUserId, String loginEmail, String status,
                    String failReason, String clientIp, String userAgent) {
        this.adminUserId = adminUserId;
        this.loginEmail = loginEmail;
        this.status = status;
        this.failReason = failReason;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.createdAt = OffsetDateTime.now();
    }
}

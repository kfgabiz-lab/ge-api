package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 오류로그 엔티티 — error_log 테이블 매핑
 * 이력성 테이블이므로 수정 없이 저장만 한다
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "error_log")
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 에러코드 (예: MENU_NOT_FOUND, VALIDATION_FAILED) */
    @Column(length = 50)
    private String errorCode;

    /** HTTP 상태코드 (400, 403, 404, 409, 500 등) */
    @Column(nullable = false)
    private Integer httpStatus;

    /** HTTP 메서드 (GET, POST, PUT, DELETE) */
    @Column(length = 10)
    private String method;

    /** 요청 URL (쿼리스트링 포함) */
    @Column(length = 500)
    private String requestUrl;

    /** 사용자에게 노출된 에러 메시지 */
    @Column(length = 500)
    private String message;

    /** 스택트레이스 — 500 에러만 저장, 나머지는 null */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** 요청자 IP (X-Forwarded-For 우선) */
    @Column(length = 50)
    private String clientIp;

    /** 로그인 사용자 이메일 (비로그인 시 null) */
    @Column(length = 100)
    private String loginUser;

    /** 발생일시 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ErrorLog(String errorCode, Integer httpStatus, String method, String requestUrl,
                    String message, String stackTrace, String clientIp, String loginUser) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.method = method;
        this.requestUrl = requestUrl;
        this.message = message;
        this.stackTrace = stackTrace;
        this.clientIp = clientIp;
        this.loginUser = loginUser;
        this.createdAt = LocalDateTime.now();
    }
}

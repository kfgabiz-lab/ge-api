package com.ge.bo.dto;

import com.ge.bo.entity.LoginLog;

import java.time.OffsetDateTime;

/**
 * 접속이력 목록 응답 DTO — userAgent 제외 (대용량 TEXT)
 */
public record LoginLogResponse(
        Long id,
        Long adminUserId,
        String loginEmail,
        String status,
        String failReason,
        String clientIp,
        OffsetDateTime createdAt) {

    /** loginEmail 필드명은 FE 호환을 위해 유지 — 값은 admin_user_id로 조회한 employeeId(사번) */
    public static LoginLogResponse from(LoginLog e, String employeeId) {
        return new LoginLogResponse(
                e.getId(),
                e.getAdminUserId(),
                employeeId,
                e.getStatus(),
                e.getFailReason(),
                e.getClientIp(),
                e.getCreatedAt());
    }
}

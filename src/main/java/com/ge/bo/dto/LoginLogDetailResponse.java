package com.ge.bo.dto;

import com.ge.bo.entity.LoginLog;

import java.time.OffsetDateTime;

/**
 * 접속이력 상세 응답 DTO — userAgent 포함
 */
public record LoginLogDetailResponse(
        Long id,
        Long adminUserId,
        String loginEmail,
        String status,
        String failReason,
        String clientIp,
        String userAgent,
        OffsetDateTime createdAt) {

    /** loginEmail 필드명은 FE 호환을 위해 유지 — 값은 admin_user_id로 조회한 employeeId(사번) */
    public static LoginLogDetailResponse from(LoginLog e, String employeeId) {
        return new LoginLogDetailResponse(
                e.getId(),
                e.getAdminUserId(),
                employeeId,
                e.getStatus(),
                e.getFailReason(),
                e.getClientIp(),
                e.getUserAgent(),
                e.getCreatedAt());
    }
}

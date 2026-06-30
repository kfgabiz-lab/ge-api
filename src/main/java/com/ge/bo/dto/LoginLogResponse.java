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

    public static LoginLogResponse from(LoginLog e) {
        return new LoginLogResponse(
                e.getId(),
                e.getAdminUserId(),
                e.getLoginEmail(),
                e.getStatus(),
                e.getFailReason(),
                e.getClientIp(),
                e.getCreatedAt());
    }
}

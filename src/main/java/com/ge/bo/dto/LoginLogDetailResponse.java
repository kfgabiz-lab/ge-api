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

    public static LoginLogDetailResponse from(LoginLog e) {
        return new LoginLogDetailResponse(
                e.getId(),
                e.getAdminUserId(),
                e.getLoginEmail(),
                e.getStatus(),
                e.getFailReason(),
                e.getClientIp(),
                e.getUserAgent(),
                e.getCreatedAt());
    }
}

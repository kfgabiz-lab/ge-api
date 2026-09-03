package com.ge.bo.dto;

import com.ge.bo.entity.TransactionLog;

import java.time.OffsetDateTime;

/**
 * 트랜잭션 로그 목록 응답 DTO — requestBody 제외 (대용량 TEXT 컬럼)
 */
public record TransactionLogResponse(
        Long id,
        String actionType,
        String method,
        String requestUrl,
        Integer httpStatus,
        String loginUser,
        String clientIp,
        Long durationMs,
        String source,
        Long siteId,
        OffsetDateTime createdAt) {

    public static TransactionLogResponse from(TransactionLog t) {
        return new TransactionLogResponse(
                t.getId(),
                t.getActionType(),
                t.getMethod(),
                t.getRequestUrl(),
                t.getHttpStatus(),
                t.getLoginUser(),
                t.getClientIp(),
                t.getDurationMs(),
                t.getSource(),
                t.getSiteId(),
                t.getCreatedAt());
    }
}

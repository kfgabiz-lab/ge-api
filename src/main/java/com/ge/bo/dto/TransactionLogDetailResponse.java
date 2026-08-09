package com.ge.bo.dto;

import com.ge.bo.entity.TransactionLog;

import java.time.OffsetDateTime;

/**
 * 트랜잭션 로그 상세 응답 DTO — requestBody 포함
 */
public record TransactionLogDetailResponse(
        Long id,
        String actionType,
        String method,
        String requestUrl,
        String requestBody,
        Integer httpStatus,
        String loginUser,
        String clientIp,
        Long durationMs,
        OffsetDateTime createdAt) {

    public static TransactionLogDetailResponse from(TransactionLog t, String decryptedRequestBody) {
        return new TransactionLogDetailResponse(
                t.getId(),
                t.getActionType(),
                t.getMethod(),
                t.getRequestUrl(),
                decryptedRequestBody,
                t.getHttpStatus(),
                t.getLoginUser(),
                t.getClientIp(),
                t.getDurationMs(),
                t.getCreatedAt());
    }
}

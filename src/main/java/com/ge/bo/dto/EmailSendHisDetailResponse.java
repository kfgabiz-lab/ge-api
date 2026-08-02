package com.ge.bo.dto;

import java.time.OffsetDateTime;

/**
 * 이메일 발송 이력 단건 상세 응답 DTO — subject/content 포함(재발송 확인용)
 */
public record EmailSendHisDetailResponse(
        Long id,
        String emailSendType,
        String emailSendDetailType,
        String recipientEmail,
        String subject,
        String content,
        String sendStatus,
        OffsetDateTime sentAt,
        OffsetDateTime lastResendAt) {
}

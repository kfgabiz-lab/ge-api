package com.ge.bo.dto;

import java.time.OffsetDateTime;

/**
 * 이메일 발송 이력 목록 응답 DTO — subject/content 제외
 */
public record EmailSendHisResponse(
        Long id,
        String emailSendType,
        String emailSendDetailType,
        String recipientEmail,
        String sendStatus,
        OffsetDateTime sentAt,
        OffsetDateTime lastResendAt) {
}

package com.ge.bo.common.mail;

public record MailSendEvent(
        String to,
        String subject,
        String content,
        String emailSendType,
        String emailSendDetailType,
        Long siteId) {
}

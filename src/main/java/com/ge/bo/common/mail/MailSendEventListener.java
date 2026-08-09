package com.ge.bo.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailSendEventListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MailSendEvent event) {
        try {
            mailService.sendMail(event.to(), event.subject(), event.content(),
                    event.emailSendType(), event.emailSendDetailType(), event.siteId());
        } catch (Exception e) {
            log.warn("메일 비동기 발송 처리 실패 - emailSendType={}, message={}",
                    event.emailSendType(), e.getMessage());
        }
    }
}

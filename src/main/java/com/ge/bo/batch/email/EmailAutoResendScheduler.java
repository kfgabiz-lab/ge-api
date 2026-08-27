package com.ge.bo.batch.email;

import com.ge.bo.repository.EmailSendHisRepository;
import com.ge.bo.service.EmailSendHisService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("dev | prod")
@RequiredArgsConstructor
public class EmailAutoResendScheduler {

    private static final long LOCK_KEY = 917_240_115L;
    private static final int WINDOW_MINUTES = 15;

    private final EmailSendHisRepository emailSendHisRepository;
    private final EmailSendHisService emailSendHisService;
    private final EntityManager entityManager;

    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void resendFailedEmails() {
        Boolean acquired = (Boolean) entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                .setParameter("key", LOCK_KEY)
                .getSingleResult();
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("이메일 자동 재발송 배치 — 다른 인스턴스가 이미 실행 중이라 이번 주기는 건너뜀");
            return;
        }

        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusMinutes(WINDOW_MINUTES);
        List<Long> targetIds = emailSendHisRepository.findAutoResendTargetIds(start, end);
        if (targetIds.isEmpty()) {
            return;
        }

        log.info("이메일 자동 재발송 배치 시작 — 대상 {}건 (창: {} ~ {})", targetIds.size(), start, end);
        int success = 0;
        for (Long id : targetIds) {
            try {
                if ("S".equals(emailSendHisService.resend(id))) {
                    success++;
                }
            } catch (RuntimeException e) {
                log.warn("이메일 자동 재발송 실패 — id={}: {}", id, e.getMessage());
            }
        }
        log.info("이메일 자동 재발송 배치 종료 — 대상 {}건 중 {}건 성공", targetIds.size(), success);
    }
}

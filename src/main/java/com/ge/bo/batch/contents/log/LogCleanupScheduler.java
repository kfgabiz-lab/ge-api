package com.ge.bo.batch.contents.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그인 및 트랜잭션 로그 정리 스케줄러.
 * - 로그 보관기간(1년)이 지난 데이터를 정기적으로 삭제하기 위한 배치를 실행한다.
 * - 매일 북미 동부시간(America/New_York) 기준 새벽 3시에 실행한다.
 * - 실제 로그 삭제 처리는 LogCleanupBatchService에 위임한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanupScheduler {

    private final LogCleanupBatchService logCleanupBatchService;

    /**
     * 매일 북미 동부시간 기준 새벽 3시에 로그 정리 배치를 실행한다.
     * - 오늘 날짜 기준 1년 전 데이터를 삭제 대상으로 한다.
     * - America/New_York 시간대를 사용하여 서머타임(DST)을 자동 반영한다.
     */
    @Scheduled(
            cron = "0 0 3 * * *",
            zone = "America/New_York"
    )
    public void cleanupOldLogs() {
        logCleanupBatchService.cleanup();
    }
}

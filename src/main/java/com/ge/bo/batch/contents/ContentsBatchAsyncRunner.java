package com.ge.bo.batch.contents;

import com.ge.bo.batch.contents.catalog.CatalogContentsBatchService;
import com.ge.bo.batch.contents.certi.CertiContentsBatchService;
import com.ge.bo.batch.contents.ssq.SsqContentsBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.function.LongConsumer;

/**
 * 콘텐츠 통합배치 수동 트리거(run-all)의 백그라운드 실행 담당 — ContentsBatchOrchestrator가 배치로그(batchId)를
 * 먼저 확보해 즉시 응답한 뒤, 이 빈의 @Async 메서드로 실제 처리를 넘긴다. @Async는 프록시를 거쳐야 동작하므로
 * Orchestrator 자기 자신이 호출하지 않고 별도 빈으로 분리했다(REQUIRES_NEW 트랜잭션 분리와 동일한 이유).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentsBatchAsyncRunner {

    private final CatalogContentsBatchService catalogContentsBatchService;
    private final SsqContentsBatchService ssqContentsBatchService;
    private final CertiContentsBatchService certiContentsBatchService;

    /**
     * batchId가 null이면 해당 소스는 이미 다른 실행이 진행 중이라 이번 트리거에서 건너뛴 것 — 실행하지 않는다.
     */
    @Async
    public void runAll(Long catalogBatchId, Long ssqBatchId, Long certiBatchId) {
        runSource("CATALOG", catalogBatchId, catalogContentsBatchService::execute);
        runSource("SSQ", ssqBatchId, ssqContentsBatchService::execute);
        runSource("CERTI", certiBatchId, certiContentsBatchService::execute);
    }

    /** 소스 1개만 비동기로 트리거 — EAI가 각 소스 IF 적재 완료 후 개별 호출하는 용도. */
    @Async
    public void runCatalogOnly(long batchId) {
        runSource("CATALOG", batchId, catalogContentsBatchService::execute);
    }

    @Async
    public void runSsqOnly(long batchId) {
        runSource("SSQ", batchId, ssqContentsBatchService::execute);
    }

    @Async
    public void runCertiOnly(long batchId) {
        runSource("CERTI", batchId, certiContentsBatchService::execute);
    }

    private void runSource(String sourceSystem, Long batchId, LongConsumer action) {
        if (batchId == null) {
            return;
        }
        try {
            action.accept(batchId);
        } catch (RuntimeException e) {
            log.warn("{} 콘텐츠 배치 실패(비동기) batchId={} — 해당 소스는 건너뛰고 다음 소스로 계속 진행: {}",
                sourceSystem, batchId, e.getMessage());
        }
    }
}

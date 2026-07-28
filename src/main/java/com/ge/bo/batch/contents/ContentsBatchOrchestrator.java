package com.ge.bo.batch.contents;

import com.ge.bo.batch.contents.catalog.CatalogContentsBatchService;
import com.ge.bo.batch.contents.certi.CertiContentsBatchService;
import com.ge.bo.batch.contents.ssq.SsqContentsBatchService;
import com.ge.bo.entity.ContentsBatchLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 카탈로그 → SSQ → 인증서 순차 실행 — 수동/EAI 호출 트리거가 동일한 순서·오류 처리 정책을 공유하도록 이 지점에 모은다.
 * 스케줄러 없이 EAI가 각 소스 IF 적재를 끝낸 뒤 호출하는 방식으로 운영한다(2026-07-24 확정).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentsBatchOrchestrator {

    private final CatalogContentsBatchService catalogContentsBatchService;
    private final SsqContentsBatchService ssqContentsBatchService;
    private final CertiContentsBatchService certiContentsBatchService;
    private final ContentsBatchAsyncRunner asyncRunner;

    /**
     * 수동 트리거(run-all API) 전용 — 3개 소스의 배치로그를 즉시 RUNNING으로 선점해 batchId를 바로 확보하고,
     * 실제 처리는 ContentsBatchAsyncRunner에 위임한 뒤 곧바로 반환한다. 프론트는 반환된 batchId로
     * GET /contents-batch/{batchId}를 폴링해 진행 상황을 확인한다(장시간 동기 응답이 프록시/타임아웃에 끊기는 문제 회피).
     */
    public AsyncBatchIds startAll() {
        Long catalogBatchId = acquireOrReuse("CATALOG", catalogContentsBatchService::acquireLock);
        Long ssqBatchId = acquireOrReuse("SSQ", ssqContentsBatchService::acquireLock);
        Long certiBatchId = acquireOrReuse("CERTI", certiContentsBatchService::acquireLock);
        asyncRunner.runAll(catalogBatchId, ssqBatchId, certiBatchId);
        return new AsyncBatchIds(catalogBatchId, ssqBatchId, certiBatchId);
    }

    /**
     * 소스 1개만 즉시 반환 트리거 — EAI가 각 소스 IF 적재를 끝낸 뒤 그 소스만 개별 호출하는 용도.
     * 이미 실행 중이면(acquireLock의 conflict) 예외가 그대로 올라가 컨트롤러가 에러 응답을 내려준다
     * (run-all과 달리 단일 트리거는 건너뛰지 않고 명확히 실패를 알려주는 게 맞다고 판단).
     */
    public Long startCatalog() {
        Long batchId = catalogContentsBatchService.acquireLock().getBatchId();
        asyncRunner.runCatalogOnly(batchId);
        return batchId;
    }

    public Long startSsq() {
        Long batchId = ssqContentsBatchService.acquireLock().getBatchId();
        asyncRunner.runSsqOnly(batchId);
        return batchId;
    }

    public Long startCerti() {
        Long batchId = certiContentsBatchService.acquireLock().getBatchId();
        asyncRunner.runCertiOnly(batchId);
        return batchId;
    }

    /**
     * 배치로그 선점을 시도하고, 이미 실행 중인 배치가 있어 선점에 실패하면(acquireLock의 conflict 예외) 새로
     * 시작하지 않고 null을 반환한다 — ContentsBatchAsyncRunner가 null batchId는 건너뛴다. 이렇게 해야 이미
     * RUNNING인 배치로그 위에 또 다른 실행을 겹쳐 실행하는 것을 막는다.
     */
    private Long acquireOrReuse(String sourceSystem, Supplier<ContentsBatchLog> acquireLock) {
        try {
            return acquireLock.get().getBatchId();
        } catch (RuntimeException e) {
            log.warn("{} 콘텐츠 배치 시작 실패(이미 실행 중일 수 있음) — 이번 트리거에서는 건너뜀: {}", sourceSystem, e.getMessage());
            return null;
        }
    }

    public record AsyncBatchIds(Long catalogBatchId, Long ssqBatchId, Long certiBatchId) {
    }
}

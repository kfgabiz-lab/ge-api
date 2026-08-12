package com.ge.bo.controller;

import com.ge.bo.batch.contents.ContentsBatchOrchestrator;
import com.ge.bo.batch.contents.ssq.SsqCategoryRemapService;
import com.ge.bo.dto.ContentsBatchLogResponse;
import com.ge.bo.dto.ContentsBatchRunAllResponse;
import com.ge.bo.dto.ContentsBatchTriggerResponse;
import com.ge.bo.dto.ContentsIfFailRowResponse;
import com.ge.bo.dto.ContentsProcessedDocResponse;
import com.ge.bo.dto.SsqCategoryRemapResponse;
import com.ge.bo.entity.ContentsBatchLog;
import com.ge.bo.entity.ContentsIfFailRow;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.ContentsBatchLogRepository;
import com.ge.bo.repository.ContentsIfFailRowRepository;
import com.ge.bo.repository.ContentsMasterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * NAHP 콘텐츠 통합배치(카탈로그/SSQ/인증서 IF → contents_* 4테이블) 실행 API.
 */
@RestController
@RequestMapping("/api/v1/contents-batch")
@RequiredArgsConstructor
public class ContentsBatchController {

    private final SsqCategoryRemapService ssqCategoryRemapService;
    private final ContentsBatchOrchestrator contentsBatchOrchestrator;
    private final ContentsBatchLogRepository batchLogRepository;
    private final ContentsIfFailRowRepository failRowRepository;
    private final ContentsMasterRepository masterRepository;
    private final ObjectMapper objectMapper;

    /**
     * CATALOG 콘텐츠 배치 개별 실행 — EAI가 CATALOG IF 적재 완료 후 직접 호출
     */
    @PostMapping("/catalog/run")
    public ResponseEntity<ContentsBatchTriggerResponse> runCatalogBatch() {
        Long batchId = contentsBatchOrchestrator.startCatalog();
        return ResponseEntity.ok(ContentsBatchTriggerResponse.of(batchId));
    }

    /**
     * SSQ 콘텐츠 배치 개별 실행 — EAI가 SSQ IF 적재 완료 후 직접 호출
     */
    @PostMapping("/ssq/run")
    public ResponseEntity<ContentsBatchTriggerResponse> runSsqBatch() {
        Long batchId = contentsBatchOrchestrator.startSsq();
        return ResponseEntity.ok(ContentsBatchTriggerResponse.of(batchId));
    }

    /**
     * CERTI 콘텐츠 배치 개별 실행 — EAI가 CERTI IF 적재 완료 후 직접 호출
     */
    @PostMapping("/certi/run")
    public ResponseEntity<ContentsBatchTriggerResponse> runCertiBatch() {
        Long batchId = contentsBatchOrchestrator.startCerti();
        return ResponseEntity.ok(ContentsBatchTriggerResponse.of(batchId));
    }

    /**
     * 카탈로그 → SSQ → 인증서 순으로 3개 배치를 순차 실행 — 수동 통합 트리거용.
     * 배치로그 3건을 즉시 RUNNING으로 선점해 batchId만 반환하고, 실제 처리는 백그라운드(비동기)로 진행된다
     * (긴 동기 응답이 프록시/클라이언트 타임아웃에 끊기는 문제 회피). 진행 상황은 GET /contents-batch/{batchId}로
     * 폴링해 확인한다. 한 소스가 시스템 오류로 실패해도 전체를 중단하지 않고 다음 소스로 계속 진행한다.
     * ※ 인증 없이 공개된 엔드포인트 — EAI 후속 배치 트리거용(SecurityConfig에서 permitAll 처리).
     */
    @PostMapping("/run-all")
    public ResponseEntity<ContentsBatchRunAllResponse> runAllBatches() {
        ContentsBatchOrchestrator.AsyncBatchIds ids = contentsBatchOrchestrator.startAll();
        return ResponseEntity.ok(new ContentsBatchRunAllResponse(ids.catalogBatchId(), ids.ssqBatchId(), ids.certiBatchId()));
    }

    /**
     * 배치 실행 이력 단건 조회 — row_counts/report(jsonb)를 파싱해 그대로 내려준다(수동 실행 화면의 간단 리포트용).
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<ContentsBatchLogResponse> getBatchLog(@PathVariable Long batchId) {
        ContentsBatchLog batchLog = batchLogRepository.findById(batchId)
            .orElseThrow(ErrorCode.CONTENTS_BATCH_LOG_NOT_FOUND::toException);
        ContentsBatchLogResponse response = new ContentsBatchLogResponse(
            batchLog.getBatchId(), batchLog.getSourceSystem(), batchLog.getStatus(), batchLog.getCurrentStep(),
            readJson(batchLog.getRowCounts()), readJson(batchLog.getReport()), batchLog.getErrorMessage(),
            batchLog.getStartedAt(), batchLog.getFinishedAt());
        return ResponseEntity.ok(response);
    }

    /**
     * 소스(카탈로그/SSQ/인증서)별 가장 최근 배치 이력 조회 — 수동 실행 화면이 새로고침돼도 마지막 실행 결과를
     * 다시 보여줄 수 있도록, 페이지 진입 시 이 API로 마지막 상태를 복원한다(실행 이력이 없는 소스는 결과에서 빠진다).
     */
    @GetMapping("/latest")
    public ResponseEntity<List<ContentsBatchLogResponse>> getLatestBatchLogs() {
        List<ContentsBatchLogResponse> response = new ArrayList<>();
        for (String sourceSystem : List.of("CATALOG", "SSQ", "CERTI")) {
            batchLogRepository.findFirstBySourceSystemOrderByBatchIdDesc(sourceSystem)
                .ifPresent(batchLog -> response.add(new ContentsBatchLogResponse(
                    batchLog.getBatchId(), batchLog.getSourceSystem(), batchLog.getStatus(), batchLog.getCurrentStep(),
                    readJson(batchLog.getRowCounts()), readJson(batchLog.getReport()), batchLog.getErrorMessage(),
                    batchLog.getStartedAt(), batchLog.getFinishedAt())));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 배치 1회 실행에서 격리된(적재 실패) 행 목록 조회 — 리포트 노트만으로는 원본 데이터를 볼 수 없어서,
     * 어떤 원천 행이 왜 걸렸는지 raw_data까지 그대로 내려준다.
     */
    @GetMapping("/{batchId}/fail-rows")
    public ResponseEntity<List<ContentsIfFailRowResponse>> getFailRows(@PathVariable Long batchId) {
        List<ContentsIfFailRowResponse> response = failRowRepository.findByBatchId(batchId).stream()
            .map(this::toFailRowResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 배치 1회 실행에서 실제로 적재된(성공+부분성공) 문서 목록 조회 — 격리/실패 행만으로는 "뭐가 문제였는지"만
     * 보이고 "뭐가 정상 처리됐는지"는 알 수 없어서, 성공 쪽도 어드민 화면에서 바로 확인할 수 있게 내려준다.
     */
    @GetMapping("/{batchId}/processed-docs")
    public ResponseEntity<List<ContentsProcessedDocResponse>> getProcessedDocs(@PathVariable Long batchId) {
        ContentsBatchLog batchLog = batchLogRepository.findById(batchId)
            .orElseThrow(ErrorCode.CONTENTS_BATCH_LOG_NOT_FOUND::toException);
        List<ContentsProcessedDocResponse> response = masterRepository
            .findProcessedDocsByBatch(batchLog.getSourceSystem(), batchId).stream()
            .map(row -> new ContentsProcessedDocResponse((Long) row[0], (String) row[1], (String) row[2],
                (String) row[3], (Boolean) row[4], (Boolean) row[5], (Long) row[6], (Long) row[7], (Long) row[8]))
            .toList();
        return ResponseEntity.ok(response);
    }

    private ContentsIfFailRowResponse toFailRowResponse(ContentsIfFailRow row) {
        return new ContentsIfFailRowResponse(row.getId(), row.getBatchId(), row.getSourceSystem(), row.getSourceTable(),
            row.getSourceDocKey(), row.getSourceRowKey(), row.getFailStep(), row.getFailCode(), row.getFailDetail(),
            readJson(row.getRawData()), row.getStatus(), row.getResolvedNote(), row.getResolvedAt(), row.getCreatedAt());
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("contents_batch_log의 jsonb 컬럼 파싱 실패", e);
        }
    }

    /**
     * SSQ 미매핑 카테고리 재처리 — SsqCategoryMappingEntry(변환표)에 새 항목을 추가·배포한 뒤 실행하면,
     * 원본 IF 재수신 없이도 그동안 nahp_category_id=NULL이던 카테고리가 채워진다.
     */
    @PostMapping("/ssq/remap-categories")
    public ResponseEntity<SsqCategoryRemapResponse> remapSsqCategories() {
        int remapped = ssqCategoryRemapService.remapUnmapped();
        return ResponseEntity.ok(SsqCategoryRemapResponse.of(remapped));
    }
}

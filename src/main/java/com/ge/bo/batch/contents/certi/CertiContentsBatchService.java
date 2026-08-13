package com.ge.bo.batch.contents.certi;

import com.ge.bo.batch.contents.ContentsJsonSupport;
import com.ge.bo.batch.contents.ContentsWriter;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.WriteCounts;
import com.ge.bo.entity.ContentsBatchLog;
import com.ge.bo.entity.ContentsCategory;
import com.ge.bo.entity.ContentsFile;
import com.ge.bo.entity.ContentsIfFailRow;
import com.ge.bo.entity.ContentsVersion;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.ContentsBatchLogRepository;
import com.ge.bo.repository.ContentsCategoryRepository;
import com.ge.bo.repository.ContentsFileRepository;
import com.ge.bo.repository.ContentsIfFailRowRepository;
import com.ge.bo.repository.ContentsVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CERTI 소스 콘텐츠 통합배치 오케스트레이터 — Catalog/SsqContentsBatchService와 동일 골격(ContentsWriter 공용 재사용).
 * CERTI는 원천 테이블이 1개뿐이라 "헤더 없이 파일만 존재" 같은 교차 불일치 케이스가 없다.
 * 델타(증분) 소스라 행 부재 기반 삭제 감지는 하지 않지만, CERTI_STATUS='7'(규격폐기)은 Converter가
 * explicitDelete=true로 명시적 삭제 신호를 세워 is_deleted=true를 즉시 반영한다. Writer의 null 보존 정책이 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertiContentsBatchService {

    private static final String SOURCE_SYSTEM = "CERTI";

    private final CertiContentsReader reader;
    private final CertiContentsConverter converter;
    private final ContentsWriter writer;
    private final ContentsJsonSupport jsonSupport;
    private final ContentsBatchLogRepository batchLogRepository;
    private final ContentsIfFailRowRepository failRowRepository;
    private final ContentsCategoryRepository categoryRepository;
    private final ContentsVersionRepository versionRepository;
    private final ContentsFileRepository fileRepository;

    public Long run() {
        ContentsBatchLog batchLog = acquireLock();
        execute(batchLog.getBatchId());
        return batchLog.getBatchId();
    }

    /**
     * 배치로그가 이미 RUNNING으로 생성돼 있다는 전제하에 실제 처리만 수행 — 비동기 트리거(오케스트레이터)가
     * batchId를 먼저 확보해 즉시 응답한 뒤 백그라운드에서 이 메서드를 호출하는 용도.
     */
    public void execute(long batchId) {
        ContentsBatchLog batchLog = batchLogRepository.findById(batchId)
            .orElseThrow(() -> new IllegalStateException("CERTI 배치로그를 찾을 수 없습니다. batchId=" + batchId));
        BatchTally tally = new BatchTally();

        try {
            markStep(batchLog, "CLEANSE");
            quarantineDuplicateKeys(batchId, tally);
            Map<String, List<CertiRow>> groups = reader.loadPendingGroups();
            tally.receivedRowCount = groups.values().stream().mapToInt(List::size).sum();
            tally.receivedDocumentCount = groups.size();

            markStep(batchLog, "UPSERT");
            Set<Long> cleanSuccessContentsIds = new LinkedHashSet<>();

            for (Map.Entry<String, List<CertiRow>> entry : groups.entrySet()) {
                processDocument(entry.getValue(), batchId, tally, cleanSuccessContentsIds);
            }

            markStep(batchLog, "DELETE_CHECK");
            int deletedCount = deleteStaleRows(cleanSuccessContentsIds, batchId, tally);

            markStep(batchLog, "REPORT");
            String status = (tally.failedDocCount == 0 && tally.partialDocCount == 0) ? "SUCCESS" : "PARTIAL_SUCCESS";
            batchLog.complete(status, jsonSupport.toJson(tally.toRowCounts()), jsonSupport.toJson(tally.toReport(deletedCount)));
            batchLogRepository.save(batchLog);
            log.info("CERTI 콘텐츠 배치 완료 batchId={} status={} success={} partial={} failed={}",
                batchId, status, tally.successDocCount, tally.partialDocCount, tally.failedDocCount);
        } catch (RuntimeException e) {
            log.error("CERTI 콘텐츠 배치 시스템 오류 batchId={}", batchId, e);
            batchLog.fail(stackTraceToString(e));
            batchLogRepository.save(batchLog);
            throw e;
        }
    }

    /** 어드민 화면(contents_batch_log.error_message)에서 원인을 바로 볼 수 있도록 메시지 대신 전체 스택트레이스를 남긴다 */
    private String stackTraceToString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private void processDocument(List<CertiRow> rows, long batchId, BatchTally tally,
                                  Set<Long> cleanSuccessContentsIds) {
        CertiRow first = rows.get(0);
        String certiNo = first.certiNo();
        String bi = first.bi();
        String sourceDocKey = certiNo + "|" + bi;

        ConversionResult result;
        try {
            result = converter.convert(certiNo, bi, rows);
        } catch (RuntimeException e) {
            log.warn("CERTI 문서 변환 중 예기치 못한 오류 {}", sourceDocKey, e);
            saveFailRow(batchId, "if_r_certi_master", sourceDocKey, sourceDocKey, "CONVERT", "UNEXPECTED_ERROR",
                e.getMessage(), Map.of("certiNo", certiNo, "bi", bi));
            reader.markIfResult(certiNo, bi, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        if (!result.hasDocument()) {
            for (RowFailure f : result.rowFailures()) {
                saveFailRow(batchId, f.sourceTable(), sourceDocKey, f.sourceRowKey(), "CONVERT", f.failCode(),
                    f.failDetail(), f.rawData());
            }
            reader.markIfResult(certiNo, bi, "E");
            tally.failedDocCount++;
            tally.quarantineCount += result.rowFailures().size();
            return;
        }

        WriteCounts counts;
        try {
            counts = writer.writeDocument(result.document(), batchId);
        } catch (RuntimeException e) {
            log.warn("CERTI 문서 저장 실패 {}", sourceDocKey, e);
            saveFailRow(batchId, "contents_master", sourceDocKey, sourceDocKey, "UPSERT", "UPSERT_ERROR",
                e.getMessage(), Map.of("certiNo", certiNo, "bi", bi));
            reader.markIfResult(certiNo, bi, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        for (RowFailure f : result.rowFailures()) {
            saveFailRow(batchId, f.sourceTable(), sourceDocKey, f.sourceRowKey(), "CONVERT", f.failCode(),
                f.failDetail(), f.rawData());
        }
        tally.reportNotes.addAll(result.reportNotes());
        tally.accumulate(counts);
        reader.markIfResult(certiNo, bi, "P");

        if (result.hasRowFailures()) {
            tally.partialDocCount++;
            tally.quarantineCount += result.rowFailures().size();
        } else {
            tally.successDocCount++;
            cleanSuccessContentsIds.add(counts.contentsId());
        }
    }

    /**
     * 하위 행 삭제 처리(문서 스코프) — 완전 정상 처리된 문서에 한해, 이번 배치 도장을 못 받은 카테고리를
     * 소스에서 삭제된 것으로 간주해 즉시 soft delete한다. 문서가 델타에 포함될 때 그 문서의 카테고리 행 전체가
     * 함께 온다는 전제 확인됨(2026-07-24) — CatalogContentsBatchService#deleteStaleRows와 동일 패턴.
     * CERTI는 버전이 항상 DEFAULT 1건, 파일도 최대 1건(LAST_CERTI_FILE)뿐이라 실제로는 카테고리만 대상이 된다.
     */
    private int deleteStaleRows(Set<Long> cleanSuccessContentsIds, long batchId, BatchTally tally) {
        int deletedCount = 0;
        for (Long contentsId : cleanSuccessContentsIds) {
            List<ContentsCategory> staleCategories =
                categoryRepository.findStale(contentsId, batchId);
            for (ContentsCategory stale : staleCategories) {
                stale.markDeleted();
                categoryRepository.save(stale);
            }

            List<ContentsVersion> staleVersions =
                versionRepository.findStale(contentsId, batchId);
            for (ContentsVersion stale : staleVersions) {
                stale.markDeleted();
                versionRepository.save(stale);
            }

            deletedCount += staleCategories.size() + staleVersions.size();

            for (ContentsVersion version : versionRepository.findByContentsId(contentsId)) {
                List<ContentsFile> staleFiles = fileRepository
                    .findStale(version.getId(), batchId);
                for (ContentsFile file : staleFiles) {
                    file.markDeleted();
                    fileRepository.save(file);
                }
                deletedCount += staleFiles.size();
            }
        }
        if (deletedCount > 0) {
            tally.reportNotes.add("소스 재전송에서 빠진 카테고리 " + deletedCount + "건을 삭제 처리함");
        }
        return deletedCount;
    }

    public ContentsBatchLog acquireLock() {
        try {
            return batchLogRepository.save(ContentsBatchLog.builder()
                .sourceSystem(SOURCE_SYSTEM)
                .status("RUNNING")
                .currentStep("INIT")
                .startedAt(java.time.OffsetDateTime.now())
                .build());
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("이미 실행 중인 CERTI 콘텐츠 배치가 있습니다.");
        }
    }

    private void markStep(ContentsBatchLog batchLog, String step) {
        batchLog.updateStep(step);
        batchLogRepository.save(batchLog);
    }

    /**
     * 원천에 동일 복합키(certi_no, bi, nahp_level_seq)로 중복 수신된 행이 있으면, Hibernate가 같은 엔티티로
     * 인식해 값이 조용히 유실될 위험이 있다. loadPendingGroups() 호출 전에 먼저 실행해
     * if_date가 가장 이른 1건만 남기고 나머지(나중 도착한 중복분)를 'E'로 격리한다.
     */
    private void quarantineDuplicateKeys(long batchId, BatchTally tally) {
        for (Object[] row : reader.findDuplicateKeyRows()) {
            String certiNo = String.valueOf(row[0]);
            String bi = String.valueOf(row[1]);
            Object levelSeq = row[2];
            Object keptIfDate = row[3];
            String sourceDocKey = certiNo + "|" + bi;
            saveFailRow(batchId, "if_r_certi_master", sourceDocKey, sourceDocKey + ", nahp_level_seq=" + levelSeq,
                "CLEANSE", "DUPLICATE_KEY",
                "동일 복합키(certi_no+bi+nahp_level_seq)로 중복 수신된 행 — 나중 도착분 격리, 최초 수신분만 처리"
                    + (keptIfDate != null ? " (채택된 행 if_date=" + keptIfDate + ")" : ""),
                Map.of("certiNo", certiNo, "bi", bi, "nahpLevelSeq", String.valueOf(levelSeq), "keptIfDate", String.valueOf(keptIfDate)));
            tally.duplicateKeyCount++;
        }
        int quarantined = reader.quarantineDuplicateKeys();
        if (quarantined > 0) {
            log.warn("CERTI IF 복합키 중복 {}건 격리(E) 처리", quarantined);
        }
    }

    private void saveFailRow(long batchId, String sourceTable, String sourceDocKey, String sourceRowKey,
                              String failStep, String failCode, String failDetail, Map<String, Object> rawData) {
        failRowRepository.save(ContentsIfFailRow.builder()
            .batchId(batchId)
            .sourceSystem(SOURCE_SYSTEM)
            .sourceTable(sourceTable)
            .sourceDocKey(sourceDocKey)
            .sourceRowKey(sourceRowKey)
            .failStep(failStep)
            .failCode(failCode)
            .failDetail(failDetail)
            .rawData(jsonSupport.toJson(rawData))
            .build());
    }

    private static final class BatchTally {
        int receivedRowCount;
        int receivedDocumentCount;
        int successDocCount;
        int partialDocCount;
        int failedDocCount;
        int duplicateKeyCount;
        int quarantineCount;
        int masterInsert;
        int masterUpdate;
        int categoryInsert;
        int categoryUpdate;
        int versionInsert;
        int versionUpdate;
        int fileInsert;
        int fileUpdate;
        final List<String> reportNotes = new ArrayList<>();

        void accumulate(WriteCounts c) {
            masterInsert += c.masterInsert();
            masterUpdate += c.masterUpdate();
            categoryInsert += c.categoryInsert();
            categoryUpdate += c.categoryUpdate();
            versionInsert += c.versionInsert();
            versionUpdate += c.versionUpdate();
            fileInsert += c.fileInsert();
            fileUpdate += c.fileUpdate();
        }

        Map<String, Object> toRowCounts() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("received_row_count", receivedRowCount);
            map.put("received_document_count", receivedDocumentCount);
            map.put("success_count", successDocCount);
            map.put("partial_count", partialDocCount);
            map.put("failure_count", failedDocCount);
            map.put("duplicate_key_count", duplicateKeyCount);
            map.put("row_failure_count", quarantineCount);
            map.put("master_insert_count", masterInsert);
            map.put("master_update_count", masterUpdate);
            map.put("category_insert_count", categoryInsert);
            map.put("category_update_count", categoryUpdate);
            map.put("version_insert_count", versionInsert);
            map.put("version_update_count", versionUpdate);
            map.put("file_insert_count", fileInsert);
            map.put("file_update_count", fileUpdate);
            return map;
        }

        Map<String, Object> toReport(int deletedCount) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deleted_count", deletedCount);
            map.put("notes", reportNotes);
            return map;
        }
    }
}

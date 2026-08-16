package com.ge.bo.batch.contents.ssq;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * SSQ 소스 콘텐츠 통합배치 오케스트레이터 — CatalogContentsBatchService와 동일 골격(ContentsWriter 공용 재사용).
 * 문서 자연키가 String(CTLG_CODE)이 아닌 int(doc_id)라는 점, if_r_ssq_file_info가 JPA 엔티티가 아니라는 점만 다르다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsqContentsBatchService {

    private static final String SOURCE_SYSTEM = "SSQ";

    private final SsqContentsReader reader;
    private final SsqContentsConverter converter;
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
            .orElseThrow(() -> new IllegalStateException("SSQ 배치로그를 찾을 수 없습니다. batchId=" + batchId));
        BatchTally tally = new BatchTally();

        try {
            markStep(batchLog, "CLEANSE");
            quarantineDuplicateKeys(batchId, tally);
            Map<Integer, List<SsqDocumentRow>> docGroups = reader.loadPendingDocumentGroups();
            Map<Integer, List<SsqFileInfoRow>> fileGroups = reader.loadPendingFileGroups();
            tally.receivedRowCount = docGroups.values().stream().mapToInt(List::size).sum()
                + fileGroups.values().stream().mapToInt(List::size).sum();
            // 이번 배치가 실제로 조회한 원천 행 중 if_date가 가장 최근인 행의 if_trc_id를 배치로그에 남긴다
            docGroups.values().stream().flatMap(List::stream)
                .forEach(row -> tally.considerTrcId(row.ifTrcId(), row.ifDate()));
            fileGroups.values().stream().flatMap(List::stream)
                .forEach(row -> tally.considerTrcId(row.ifTrcId(), row.ifDate()));

            Set<Integer> allDocIds = new TreeSet<>();
            allDocIds.addAll(docGroups.keySet());
            allDocIds.addAll(fileGroups.keySet());
            tally.receivedDocumentCount = allDocIds.size();

            markStep(batchLog, "UPSERT");
            // contentsId -> docId — 삭제 감지 리포트에서 자연키(doc_id)로 노출하기 위해 매핑을 유지한다.
            Map<Long, Integer> cleanSuccessDocIdsByContentsId = new LinkedHashMap<>();

            for (Integer docId : allDocIds) {
                processDocument(docId, docGroups.get(docId), fileGroups.get(docId), batchId, tally, cleanSuccessDocIdsByContentsId);
            }

            markStep(batchLog, "DELETE_CHECK");
            int deletedCount = deleteStaleRows(cleanSuccessDocIdsByContentsId, batchId, tally);

            markStep(batchLog, "REPORT");
            String status = (tally.failedDocCount == 0 && tally.partialDocCount == 0) ? "SUCCESS" : "PARTIAL_SUCCESS";
            batchLog.complete(status, jsonSupport.toJson(tally.toRowCounts()), jsonSupport.toJson(tally.toReport(deletedCount)),
                tally.latestTrcId);
            batchLogRepository.save(batchLog);
            log.info("SSQ 콘텐츠 배치 완료 batchId={} status={} success={} partial={} failed={}",
                batchId, status, tally.successDocCount, tally.partialDocCount, tally.failedDocCount);
        } catch (RuntimeException e) {
            log.error("SSQ 콘텐츠 배치 시스템 오류 batchId={}", batchId, e);
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

    private void processDocument(int docId, List<SsqDocumentRow> docRows, List<SsqFileInfoRow> fileRows, long batchId,
                                  BatchTally tally, Map<Long, Integer> cleanSuccessDocIdsByContentsId) {
        List<SsqFileInfoRow> files = fileRows != null ? fileRows : List.of();

        if (docRows == null) {
            // 문서 IF만 있고 카테고리 IF가 없음 — EAI는 문서가 갱신될 때 문서 IF와 파일 IF를 항상 함께
            // 재전송하므로(단독으로 파일만 보내는 경우 없음, 2026-08-05 확인), 지금 문서 IF 없이 파일만 있는
            // 이 행들은 격리해도 나중에 문서 IF가 정상적으로 오면 그때 파일도 새 물리 행으로 함께 재전송되어
            // 다시 처리된다.
            for (SsqFileInfoRow file : files) {
                saveFailRow(batchId, "if_r_ssq_file_info", String.valueOf(docId), "doc_id=" + docId + ", file_id=" + file.fileId(),
                    file.ifTrcId(), "CONVERT", "HEADER_MISSING", "문서 IF(if_r_ssq_document)가 아직 도착하지 않아 파일만으로는 문서를 만들 수 없음",
                    Map.of("docId", docId, "fileId", String.valueOf(file.fileId())));
            }
            reader.markFileResultOnly(docId, "E");
            tally.failedDocCount++;
            tally.quarantineCount += files.size();
            return;
        }

        ConversionResult result;
        try {
            result = converter.convert(docId, docRows, files);
        } catch (RuntimeException e) {
            log.warn("SSQ 문서 변환 중 예기치 못한 오류 docId={}", docId, e);
            saveFailRow(batchId, "if_r_ssq_document", String.valueOf(docId), "doc_id=" + docId, docRows.get(0).ifTrcId(),
                "CONVERT", "UNEXPECTED_ERROR", e.getMessage(), Map.of("docId", docId));
            reader.markIfResult(docId, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        if (!result.hasDocument()) {
            for (RowFailure f : result.rowFailures()) {
                saveFailRow(batchId, f.sourceTable(), String.valueOf(docId), f.sourceRowKey(), docRows.get(0).ifTrcId(),
                    "CONVERT", f.failCode(), f.failDetail(), f.rawData());
            }
            reader.markIfResult(docId, "E");
            tally.failedDocCount++;
            tally.quarantineCount += result.rowFailures().size();
            return;
        }

        WriteCounts counts;
        try {
            counts = writer.writeDocument(result.document(), batchId);
        } catch (RuntimeException e) {
            log.warn("SSQ 문서 저장 실패 docId={}", docId, e);
            saveFailRow(batchId, "contents_master", String.valueOf(docId), "doc_id=" + docId, docRows.get(0).ifTrcId(),
                "UPSERT", "UPSERT_ERROR", e.getMessage(), Map.of("docId", docId, "docType", result.document().getDocType()));
            reader.markIfResult(docId, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        for (RowFailure f : result.rowFailures()) {
            saveFailRow(batchId, f.sourceTable(), String.valueOf(docId), f.sourceRowKey(), docRows.get(0).ifTrcId(),
                "CONVERT", f.failCode(), f.failDetail(), f.rawData());
        }
        tally.reportNotes.addAll(result.reportNotes());
        tally.accumulate(counts);
        reader.markIfResult(docId, "P");

        // 이미 존재하던 문서(이번 배치에서 master가 새로 INSERT된 게 아님)에 새 카테고리/에피소드가 추가됐는지,
        // 또는 기존 문서·카테고리·에피소드의 필드값(expose/delete_yn 등 포함 전체)이 바뀌었는지 감지 —
        // 브랜드 뉴 문서는 전부 "신규"인 게 당연해서 의미가 없으므로 제외한다(ContentsWriter의 diff*가 이미
        // 기존 행이 없으면 빈 리스트를 반환하므로 카테고리/에피소드 단위로는 별도 분기가 필요 없다).
        if (counts.masterInsert() == 0) {
            for (String change : counts.masterFieldChanges()) {
                tally.reportNotes.add("문서 값 변경 감지: doc_id=" + docId + ", " + change);
            }
            for (String path : counts.insertedCategoryPaths()) {
                tally.reportNotes.add("카테고리 추가 감지: doc_id=" + docId + ", path=" + path);
            }
            for (String versionKey : counts.insertedVersionKeys()) {
                tally.reportNotes.add("에피소드 추가 감지: doc_id=" + docId + ", version_id=" + versionKey);
            }
            for (String detail : counts.changedCategoryDetails()) {
                tally.reportNotes.add("카테고리 값 변경 감지: doc_id=" + docId + ", path=" + detail);
            }
            for (String detail : counts.changedVersionDetails()) {
                tally.reportNotes.add("에피소드 값 변경 감지: doc_id=" + docId + ", version_id=" + detail);
            }
        }

        if (result.hasRowFailures()) {
            tally.partialDocCount++;
            tally.quarantineCount += result.rowFailures().size();
        } else {
            tally.successDocCount++;
            cleanSuccessDocIdsByContentsId.put(counts.contentsId(), docId);
        }
    }

    /**
     * 하위 행 삭제 처리(문서 스코프) — 완전 정상 처리된 문서에 한해, 이번 배치 도장을 못 받은 카테고리·에피소드·파일을
     * 소스에서 삭제된 것으로 간주해 즉시 soft delete한다. 문서가 델타에 포함될 때 그 문서의 하위 행 전체가 함께
     * 온다는 전제 확인됨(2026-07-24) — CatalogContentsBatchService#deleteStaleRows와 동일 패턴.
     */
    private int deleteStaleRows(Map<Long, Integer> cleanSuccessDocIdsByContentsId, long batchId, BatchTally tally) {
        int deletedCount = 0;
        for (Map.Entry<Long, Integer> entry : cleanSuccessDocIdsByContentsId.entrySet()) {
            long contentsId = entry.getKey();
            int docId = entry.getValue();
            List<ContentsCategory> staleCategories =
                categoryRepository.findStale(contentsId, batchId);
            for (ContentsCategory stale : staleCategories) {
                stale.markDeleted();
                categoryRepository.save(stale);
                tally.reportNotes.add("카테고리 삭제 처리: doc_id=" + docId + ", path=" + stale.getSourcePath());
            }

            List<ContentsVersion> staleVersions =
                versionRepository.findStale(contentsId, batchId);
            for (ContentsVersion stale : staleVersions) {
                stale.markDeleted();
                versionRepository.save(stale);
                tally.reportNotes.add("에피소드 삭제 처리: doc_id=" + docId + ", version_id=" + stale.getSourceVersionKey());
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
            throw BusinessException.conflict("이미 실행 중인 SSQ 콘텐츠 배치가 있습니다.");
        }
    }

    private void markStep(ContentsBatchLog batchLog, String step) {
        batchLog.updateStep(step);
        batchLogRepository.save(batchLog);
    }

    /**
     * 원천에 동일 복합키(doc_id, spec_group, level_1~4)로 중복 수신된 행이 있으면, Hibernate가 같은 엔티티로
     * 인식해 값이 조용히 유실될 위험이 있다. loadPendingDocumentGroups() 호출 전에 먼저 실행해
     * if_date가 가장 이른 1건만 남기고 나머지(나중 도착한 중복분)를 'E'로 격리한다.
     */
    private void quarantineDuplicateKeys(long batchId, BatchTally tally) {
        for (Object[] row : reader.findDuplicateKeyRows()) {
            int docId = ((Number) row[0]).intValue();
            String rowKey = "doc_id=" + docId + ", spec_group=" + row[1] + ", level_1~4=["
                + row[2] + "," + row[3] + "," + row[4] + "," + row[5] + "]";
            Object keptIfDate = row[6];
            String ifTrcId = (String) row[7];
            saveFailRow(batchId, "if_r_ssq_document", String.valueOf(docId), rowKey, ifTrcId, "CLEANSE", "DUPLICATE_KEY",
                "동일 복합키(doc_id+spec_group+level_1~4)로 중복 수신된 행 — 나중 도착분 격리, 최초 수신분만 처리"
                    + (keptIfDate != null ? " (채택된 행 if_date=" + keptIfDate + ")" : ""),
                Map.of("docId", docId, "specGroup", String.valueOf(row[1]), "level1", String.valueOf(row[2]),
                    "level2", String.valueOf(row[3]), "level3", String.valueOf(row[4]), "level4", String.valueOf(row[5]),
                    "keptIfDate", String.valueOf(keptIfDate)));
            tally.duplicateKeyCount++;
        }
        int quarantined = reader.quarantineDuplicateKeys();
        if (quarantined > 0) {
            log.warn("SSQ IF 복합키 중복 {}건 격리(E) 처리", quarantined);
        }
    }

    private void saveFailRow(long batchId, String sourceTable, String sourceDocKey, String sourceRowKey,
                              String ifTrcId, String failStep, String failCode, String failDetail, Map<String, Object> rawData) {
        failRowRepository.save(ContentsIfFailRow.builder()
            .batchId(batchId)
            .sourceSystem(SOURCE_SYSTEM)
            .sourceTable(sourceTable)
            .sourceDocKey(sourceDocKey)
            .sourceRowKey(sourceRowKey)
            .ifTrcId(ifTrcId)
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
        String latestTrcId;
        java.time.LocalDateTime latestIfDate;
        final List<String> reportNotes = new ArrayList<>();

        /** if_date가 가장 최근인 행의 if_trc_id를 유지한다(마지막 전송분만 남김) */
        void considerTrcId(String trcId, java.time.LocalDateTime ifDate) {
            if (trcId == null || ifDate == null) {
                return;
            }
            if (latestIfDate == null || ifDate.isAfter(latestIfDate)) {
                latestIfDate = ifDate;
                latestTrcId = trcId;
            }
        }

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

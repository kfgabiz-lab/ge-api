package com.ge.bo.batch.contents.catalog;

import com.ge.bo.batch.contents.ContentsJsonSupport;
import com.ge.bo.batch.contents.ContentsWriter;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.WriteCounts;
import com.ge.bo.entity.ContentsBatchLog;
import com.ge.bo.entity.ContentsCategory;
import com.ge.bo.entity.ContentsFile;
import com.ge.bo.entity.ContentsIfFailRow;
import com.ge.bo.entity.ContentsMaster;
import com.ge.bo.entity.ContentsVersion;
import com.ge.bo.entity.IfCatalogFileInfo;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.ContentsBatchLogRepository;
import com.ge.bo.repository.ContentsCategoryRepository;
import com.ge.bo.repository.ContentsFileRepository;
import com.ge.bo.repository.ContentsIfFailRowRepository;
import com.ge.bo.repository.ContentsMasterRepository;
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
import java.util.TreeSet;

/**
 * CATALOG 소스 콘텐츠 통합배치 오케스트레이터.
 * 흐름: 배치락 획득 → IF 조회(그룹화) → 문서별 변환 → 문서별 REQUIRES_NEW 트랜잭션 저장(ContentsWriter, 별도 Bean)
 *       → IF_RESULT 갱신 → 삭제후보 조회(리포트만, 실삭제는 기본 비활성화) → 배치로그 마무리
 * 문서 A 처리 실패가 문서 B·C 처리를 막지 않도록, 문서 단위 예외는 이 오케스트레이터에서 잡고 계속 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogContentsBatchService {

    private static final String SOURCE_SYSTEM = "CATALOG";

    private final CatalogContentsReader reader;
    private final CatalogContentsConverter converter;
    private final ContentsWriter writer;
    private final ContentsJsonSupport jsonSupport;
    private final ContentsBatchLogRepository batchLogRepository;
    private final ContentsIfFailRowRepository failRowRepository;
    private final ContentsCategoryRepository categoryRepository;
    private final ContentsVersionRepository versionRepository;
    private final ContentsFileRepository fileRepository;
    private final ContentsMasterRepository masterRepository;

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
            .orElseThrow(() -> new IllegalStateException("CATALOG 배치로그를 찾을 수 없습니다. batchId=" + batchId));
        BatchTally tally = new BatchTally();

        try {
            markStep(batchLog, "CLEANSE");
            quarantineDuplicateKeys(batchId, tally);
            Map<String, List<CatalogHeaderRow>> headerGroups = reader.loadPendingHeaderGroups();
            Map<String, List<IfCatalogFileInfo>> fileGroups = reader.loadPendingFileGroups();
            tally.receivedRowCount = headerGroups.values().stream().mapToInt(List::size).sum()
                + fileGroups.values().stream().mapToInt(List::size).sum();

            Set<String> allCtlgCodes = new TreeSet<>();
            allCtlgCodes.addAll(headerGroups.keySet());
            allCtlgCodes.addAll(fileGroups.keySet());
            tally.receivedDocumentCount = allCtlgCodes.size();

            markStep(batchLog, "UPSERT");
            Set<Long> cleanSuccessContentsIds = new LinkedHashSet<>();

            for (String ctlgCode : allCtlgCodes) {
                processDocument(ctlgCode, headerGroups.get(ctlgCode), fileGroups.get(ctlgCode), batchId, tally,
                    cleanSuccessContentsIds);
            }

            markStep(batchLog, "DELETE_CHECK");
            int deletedCount = deleteStaleRows(cleanSuccessContentsIds, batchId, tally);
            applyLatestVersionOnly(tally);

            markStep(batchLog, "REPORT");
            String status = (tally.failedDocCount == 0 && tally.partialDocCount == 0) ? "SUCCESS" : "PARTIAL_SUCCESS";
            batchLog.complete(status, jsonSupport.toJson(tally.toRowCounts()), jsonSupport.toJson(tally.toReport(deletedCount)));
            batchLogRepository.save(batchLog);
            log.info("CATALOG 콘텐츠 배치 완료 batchId={} status={} success={} partial={} failed={}",
                batchId, status, tally.successDocCount, tally.partialDocCount, tally.failedDocCount);
        } catch (RuntimeException e) {
            log.error("CATALOG 콘텐츠 배치 시스템 오류 batchId={}", batchId, e);
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

    private void processDocument(String ctlgCode, List<CatalogHeaderRow> headers, List<IfCatalogFileInfo> files,
                                  long batchId, BatchTally tally, Set<Long> cleanSuccessContentsIds) {
        List<IfCatalogFileInfo> fileRows = files != null ? files : List.of();

        if (headers == null) {
            // 파일 IF만 있고 헤더 IF가 없음 — EAI는 문서가 갱신될 때 헤더와 파일을 항상 함께 재전송하므로
            // 지금 헤더 없이 파일만 있는 이 행들은 격리해도 헤더가 정상적으로 오면 그때 파일도 새 물리 행으로 함께 재전송되어 다시 처리된다.
            for (IfCatalogFileInfo fileRow : fileRows) {
                saveFailRow(batchId, "if_r_catalog_file_info", ctlgCode, "ctlg_code=" + ctlgCode + ", data_code=" + fileRow.getDataCode(),
                    "CONVERT", "HEADER_MISSING", "헤더 IF(if_r_catalog_info)가 아직 도착하지 않아 파일만으로는 문서를 만들 수 없음",
                    Map.of("ctlgCode", ctlgCode, "dataCode", String.valueOf(fileRow.getDataCode())));
            }
            reader.markFileResultOnly(ctlgCode, "E");
            tally.failedDocCount++;
            tally.quarantineCount += fileRows.size();
            return;
        }

        ConversionResult result;
        try {
            result = converter.convert(ctlgCode, headers, fileRows);
        } catch (RuntimeException e) {
            log.warn("CATALOG 문서 변환 중 예기치 못한 오류 ctlgCode={}", ctlgCode, e);
            saveFailRow(batchId, "if_r_catalog_info", ctlgCode, "ctlg_code=" + ctlgCode, "CONVERT", "UNEXPECTED_ERROR",
                e.getMessage(), Map.of("ctlgCode", ctlgCode));
            reader.markIfResult(ctlgCode, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        if (!result.hasDocument()) {
            for (RowFailure f : result.rowFailures()) {
                saveFailRow(batchId, f.sourceTable(), ctlgCode, f.sourceRowKey(), "CONVERT", f.failCode(), f.failDetail(), f.rawData());
            }
            reader.markIfResult(ctlgCode, "E");
            tally.failedDocCount++;
            tally.quarantineCount += result.rowFailures().size();
            return;
        }

        WriteCounts counts;
        try {
            counts = writer.writeDocument(result.document(), batchId);
        } catch (RuntimeException e) {
            log.warn("CATALOG 문서 저장 실패 ctlgCode={}", ctlgCode, e);
            saveFailRow(batchId, "contents_master", ctlgCode, "ctlg_code=" + ctlgCode, "UPSERT", "UPSERT_ERROR",
                e.getMessage(), Map.of("ctlgCode", ctlgCode, "docType", result.document().getDocType()));
            reader.markIfResult(ctlgCode, "E");
            tally.failedDocCount++;
            tally.quarantineCount++;
            return;
        }

        for (RowFailure f : result.rowFailures()) {
            saveFailRow(batchId, f.sourceTable(), ctlgCode, f.sourceRowKey(), "CONVERT", f.failCode(), f.failDetail(), f.rawData());
        }
        tally.reportNotes.addAll(result.reportNotes());
        tally.accumulate(counts);
        reader.markIfResult(ctlgCode, "P");

        if (result.hasRowFailures()) {
            tally.partialDocCount++;
            tally.quarantineCount += result.rowFailures().size();
        } else {
            tally.successDocCount++;
            cleanSuccessContentsIds.add(counts.contentsId());
        }
    }

    /**
     * 하위 행 삭제 처리(문서 스코프) — 완전 정상 처리된 문서에 한해, 이번 배치 도장을 못 받은 카테고리·버전·파일을
     * 소스에서 삭제된 것으로 간주해 즉시 soft delete한다. CATALOG는 카테고리·버전·파일 모두 삭제 여부를 알려주는
     * 상태 컬럼이 따로 없어(문서 자체의 USE_YN='D'만 명시적 삭제 신호) 행 부재 자체를 삭제로 판단한다.
     */
    private int deleteStaleRows(Set<Long> cleanSuccessContentsIds, long batchId, BatchTally tally) {
        int deletedCount = 0;
        for (Long contentsId : cleanSuccessContentsIds) {
            List<ContentsCategory> staleCategories =
                categoryRepository.findStale(contentsId, batchId);
            for (ContentsCategory category : staleCategories) {
                category.markDeleted();
                categoryRepository.save(category);
            }

            List<ContentsVersion> staleVersions =
                versionRepository.findStale(contentsId, batchId);
            for (ContentsVersion version : staleVersions) {
                version.markDeleted();
                versionRepository.save(version);
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
            tally.reportNotes.add("소스 재전송에서 빠진 하위 행 " + deletedCount + "건을 삭제 처리함(카테고리/버전/파일)");
        }
        return deletedCount;
    }

    /**
     * 동일 자료코드 프리픽스(CTLG_CODE 첫 "-" 앞)를 공유하는 문서 중 최신 버전만 노출 유지 — 실삭제가 아니라
     * expose=false 처리(파일 삭제 대상 체크 기준표, 2026-07-24 확인). 최신 판단은 PRT_VER 우선, 동률이면 PRT_YYMM.
     */
    private void applyLatestVersionOnly(BatchTally tally) {
        Map<String, List<ContentsMaster>> byPrefix = new LinkedHashMap<>();
        for (ContentsMaster doc : masterRepository.findBySourceSystemAndIsDeletedFalse(SOURCE_SYSTEM)) {
            String prefix = doc.getSourceDocKey().split("-", 2)[0];
            byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(doc);
        }

        int hiddenCount = 0;
        for (List<ContentsMaster> group : byPrefix.values()) {
            if (group.size() < 2) {
                continue;
            }
            ContentsMaster winner = null;
            int[] winnerKey = null;
            for (ContentsMaster doc : group) {
                int[] key = versionRankKey(doc.getId());
                if (winnerKey == null || compareRankKey(key, winnerKey) > 0) {
                    winner = doc;
                    winnerKey = key;
                }
            }
            for (ContentsMaster doc : group) {
                if (doc != winner && Boolean.TRUE.equals(doc.getExpose())) {
                    doc.hideAsSupersededVersion();
                    masterRepository.save(doc);
                    hiddenCount++;
                }
            }
        }
        if (hiddenCount > 0) {
            tally.reportNotes.add("동일 자료코드 프리픽스 내 구버전 " + hiddenCount
                + "건 비노출 처리함(최신 PRT_VER 우선, 동률 시 PRT_YYMM)");
        }
    }

    /** 문서의 유일 버전(source_version_key="PRT_YYMM|PRT_VER")에서 비교용 순위 키를 뽑는다 — [PRT_VER, PRT_YYMM] 순 */
    private int[] versionRankKey(long contentsId) {
        List<ContentsVersion> versions = versionRepository.findByContentsId(contentsId);
        if (versions.isEmpty()) {
            return new int[] {0, 0};
        }
        String[] parts = versions.get(0).getSourceVersionKey().split("\\|", 2);
        if (parts.length != 2) {
            return new int[] {0, 0};
        }
        return new int[] {parseIntSafe(parts[1]), parseIntSafe(parts[0])};
    }

    private int compareRankKey(int[] a, int[] b) {
        if (a[0] != b[0]) {
            return Integer.compare(a[0], b[0]);
        }
        return Integer.compare(a[1], b[1]);
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 진행 단계를 즉시 DB에 반영 — 장애 시 어디까지 진행됐는지 바로 파악하기 위함 */
    private void markStep(ContentsBatchLog batchLog, String step) {
        batchLog.updateStep(step);
        batchLogRepository.save(batchLog);
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
            throw BusinessException.conflict("이미 실행 중인 CATALOG 콘텐츠 배치가 있습니다.");
        }
    }

    /**
     * 원천에 동일 복합키(ctlg_code, nahp_level_seq)로 중복 수신된 행이 있으면, Hibernate가 같은 엔티티로 인식해
     * 값이 조용히 유실될 위험이 있다. loadPendingHeaderGroups() 호출 전에 먼저 실행해
     * if_date가 가장 이른 1건만 남기고 나머지(나중 도착한 중복분)를 'E'로 격리한다.
     */
    private void quarantineDuplicateKeys(long batchId, BatchTally tally) {
        for (Object[] row : reader.findDuplicateKeyRows()) {
            String ctlgCode = String.valueOf(row[0]);
            Object levelSeq = row[1];
            Object keptIfDate = row[2];
            saveFailRow(batchId, "if_r_catalog_info", ctlgCode, "ctlg_code=" + ctlgCode + ", nahp_level_seq=" + levelSeq,
                "CLEANSE", "DUPLICATE_KEY",
                "동일 복합키(ctlg_code+nahp_level_seq)로 중복 수신된 행 — 나중 도착분 격리, 최초 수신분만 처리"
                    + (keptIfDate != null ? " (채택된 행 if_date=" + keptIfDate + ")" : ""),
                Map.of("ctlgCode", ctlgCode, "nahpLevelSeq", String.valueOf(levelSeq), "keptIfDate", String.valueOf(keptIfDate)));
            tally.duplicateKeyCount++;
        }
        int quarantined = reader.quarantineDuplicateKeys();
        if (quarantined > 0) {
            log.warn("CATALOG IF 복합키 중복 {}건 격리(E) 처리", quarantined);
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

    /** 배치 1회 실행 동안의 집계 — contents_batch_log.row_counts/report 생성에 사용 */
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

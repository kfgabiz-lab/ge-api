package com.ge.bo.batch.contents.ssq;

import com.ge.bo.entity.IfSsqDocument;
import com.ge.bo.repository.IfSsqDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IF_R_SSQ_DOCUMENT / IF_R_SSQ_FILE_INFO 원천 조회 및 IF_RESULT 갱신 Reader.
 * if_r_ssq_file_info는 물리 PK가 없어 JPA 엔티티로 다루지 않고 JdbcTemplate으로 직접 읽고 갱신한다.
 * CatalogContentsReader와 동일한 이유로 오케스트레이터(SsqContentsBatchService)와 다른 Bean으로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class SsqContentsReader {

    private static final String PENDING = "N";

    private static final RowMapper<SsqFileInfoRow> FILE_ROW_MAPPER = (rs, rowNum) -> new SsqFileInfoRow(
        rs.getInt("doc_id"), rs.getString("doc_type"), rs.getString("doc_title"),
        rs.getInt("version_id"), rs.getString("version_name"), rs.getString("version_desc"),
        (Boolean) rs.getObject("version_expose"), (Integer) rs.getObject("file_id"), rs.getString("file_key"),
        rs.getString("file_name"), rs.getString("file_lang"), (Long) rs.getObject("file_size"),
        (Boolean) rs.getObject("file_expose"), rs.getString("size_flag"),
        rs.getString("version_update_datetime"), rs.getString("file_upsert_datetime"));

    private final IfSsqDocumentRepository ifSsqDocumentRepository;
    private final JdbcTemplate jdbcTemplate;

    /** 미처리 행 중 복합키(doc_id, spec_group, level_1~4) 중복 목록 조회(로그용) — quarantineDuplicateKeys()와 짝 */
    @Transactional(readOnly = true)
    public List<Object[]> findDuplicateKeyRows() {
        return ifSsqDocumentRepository.findDuplicateKeyRows();
    }

    /** 복합키 중복 행(가장 이른 1건 제외) 격리(E) — loadPendingDocumentGroups() 호출 전에 먼저 실행해야 한다 */
    @Transactional
    public int quarantineDuplicateKeys() {
        return ifSsqDocumentRepository.quarantineDuplicateKeys();
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<IfSsqDocument>> loadPendingDocumentGroups() {
        Map<Integer, List<IfSsqDocument>> groups = new LinkedHashMap<>();
        for (IfSsqDocument row : ifSsqDocumentRepository.findPending(PENDING)) {
            // quarantineDuplicateKeys() 실행 직후에도 그 찰나에 동일 복합키 행이 새로 들어오면, Hibernate가
            // 그 행을 null로 반환하는 경우가 있다 — 다음 회차에 다시
            // 처리되므로 이번 회차에서는 건너뛴다.
            if (row == null) {
                continue;
            }
            groups.computeIfAbsent(row.getDocId(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    public Map<Integer, List<SsqFileInfoRow>> loadPendingFileGroups() {
        Map<Integer, List<SsqFileInfoRow>> groups = new LinkedHashMap<>();
        List<SsqFileInfoRow> rows = jdbcTemplate.query(
            "SELECT doc_id, doc_type, doc_title, version_id, version_name, version_desc, version_expose, "
                + "file_id, file_key, file_name, file_lang, file_size, file_expose, size_flag, "
                + "version_update_datetime, file_upsert_datetime "
                + "FROM if_r_ssq_file_info WHERE if_result = ? ORDER BY doc_id",
            FILE_ROW_MAPPER, PENDING);
        for (SsqFileInfoRow row : rows) {
            groups.computeIfAbsent(row.docId(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    /** 문서 처리 결과에 따라 문서·버전파일 IF의 if_result를 'P'(성공) 또는 'E'(격리)로 갱신 */
    @Transactional
    public void markIfResult(int docId, String ifResult) {
        ifSsqDocumentRepository.updateIfResultByDocId(docId, ifResult);
        jdbcTemplate.update("UPDATE if_r_ssq_file_info SET if_result = ? WHERE doc_id = ? AND if_result = ?",
            ifResult, docId, PENDING);
    }

    /** 문서 IF 없이 버전·파일 IF만 존재하는 doc_id의 행만 별도로 격리 표시할 때 사용 */
    @Transactional
    public void markFileResultOnly(int docId, String ifResult) {
        jdbcTemplate.update("UPDATE if_r_ssq_file_info SET if_result = ? WHERE doc_id = ? AND if_result = ?",
            ifResult, docId, PENDING);
    }

}

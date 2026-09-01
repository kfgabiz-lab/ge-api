package com.ge.bo.batch.contents.ssq;

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
 * 두 테이블 모두 JPA 엔티티가 아니라 JdbcTemplate으로 직접 읽고 갱신한다 — if_r_ssq_file_info는 물리 PK가
 * 없어서, if_r_ssq_document는 level_3/level_4(복합키 구성요소)가 거의 항상 NULL이라 JPA @IdClass로 엔티티
 * 식별자를 만들 수 없어서(2026-07-31 확인, 전량 null 반환) 같은 이유로 둘 다 이 방식을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SsqContentsReader {

    private static final String PENDING = "N";

    private static final RowMapper<SsqDocumentRow> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new SsqDocumentRow(
        (Integer) rs.getObject("doc_id"), rs.getString("spec_group"), rs.getString("level_1"), rs.getString("level_2"),
        rs.getString("level_3"), rs.getString("level_4"), rs.getString("doc_title"), rs.getString("doc_type"),
        (Boolean) rs.getObject("expose"), rs.getString("site_language"), rs.getString("create_datetime"),
        rs.getString("update_datetime"), rs.getString("delete_yn"), rs.getString("nahp_display_flag"),
        rs.getString("if_trc_id"), toLocalDateTime(rs.getTimestamp("if_date")), rs.getString("doc_description"));

    private static final RowMapper<SsqFileInfoRow> FILE_ROW_MAPPER = (rs, rowNum) -> new SsqFileInfoRow(
        rs.getInt("doc_id"), rs.getString("doc_type"), rs.getString("doc_title"),
        rs.getInt("version_id"), rs.getString("version_name"), rs.getString("version_desc"),
        (Boolean) rs.getObject("version_expose"), (Integer) rs.getObject("file_id"), rs.getString("file_key"),
        rs.getString("file_name"), rs.getString("file_lang"), (Long) rs.getObject("file_size"),
        (Boolean) rs.getObject("file_expose"), rs.getString("size_flag"),
        rs.getString("version_update_datetime"), rs.getString("file_upsert_datetime"),
        rs.getString("if_trc_id"), toLocalDateTime(rs.getTimestamp("if_date")));

    private final JdbcTemplate jdbcTemplate;

    private static java.time.LocalDateTime toLocalDateTime(java.sql.Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * 미처리 행 중 복합키(doc_id, spec_group, level_1~4) 중복 목록 조회(로그용) — quarantineDuplicateKeys()와 짝.
     * 격리 대상(extra) 행뿐 아니라 같은 키에서 채택돼 남는(kept) 행의 if_date도 같이 내려줘서, 어드민 화면에서
     * "이 키는 결국 어느 행이 처리됐는지" 바로 보여줄 수 있게 한다(level_3/level_4가 NULL인 행이 많아
     * NULL-safe 비교(IS NOT DISTINCT FROM)로 같은 키 그룹을 다시 찾는다 — quarantineDuplicateKeys()의
     * UPDATE 로직 자체는 건드리지 않고 이 조회 쿼리만 kept 행 조인을 추가했다).
     */
    @Transactional(readOnly = true)
    public List<Object[]> findDuplicateKeyRows() {
        return jdbcTemplate.query(
            "SELECT extra.doc_id, extra.spec_group, extra.level_1, extra.level_2, extra.level_3, extra.level_4, "
                + "extra.if_date, kept.if_date AS kept_if_date, extra.if_trc_id "
                + "FROM if_r_ssq_document extra "
                + "JOIN (SELECT DISTINCT ON (doc_id, spec_group, level_1, level_2, level_3, level_4) "
                + "        doc_id, spec_group, level_1, level_2, level_3, level_4, if_date, ctid "
                + "      FROM if_r_ssq_document WHERE if_result = 'N' "
                + "      ORDER BY doc_id, spec_group, level_1, level_2, level_3, level_4, if_date ASC NULLS LAST, ctid ASC"
                + "     ) kept "
                + "  ON kept.doc_id = extra.doc_id AND kept.spec_group IS NOT DISTINCT FROM extra.spec_group "
                + "  AND kept.level_1 IS NOT DISTINCT FROM extra.level_1 AND kept.level_2 IS NOT DISTINCT FROM extra.level_2 "
                + "  AND kept.level_3 IS NOT DISTINCT FROM extra.level_3 AND kept.level_4 IS NOT DISTINCT FROM extra.level_4 "
                + "WHERE extra.if_result = 'N' AND extra.ctid <> kept.ctid",
            (rs, rowNum) -> new Object[]{rs.getObject("doc_id"), rs.getObject("spec_group"), rs.getObject("level_1"),
                rs.getObject("level_2"), rs.getObject("level_3"), rs.getObject("level_4"), rs.getTimestamp("kept_if_date"),
                rs.getString("if_trc_id")});
    }

    /** 복합키 중복 행(가장 이른 1건 제외) 격리(E) — loadPendingDocumentGroups() 호출 전에 먼저 실행해야 한다 */
    @Transactional
    public int quarantineDuplicateKeys() {
        return jdbcTemplate.update(
            "UPDATE if_r_ssq_document SET if_result = 'E' "
                + "WHERE if_result = 'N' AND ctid NOT IN ("
                + "  SELECT DISTINCT ON (doc_id, spec_group, level_1, level_2, level_3, level_4) ctid FROM if_r_ssq_document"
                + "  WHERE if_result = 'N' ORDER BY doc_id, spec_group, level_1, level_2, level_3, level_4,"
                + "    if_date ASC NULLS LAST, ctid ASC)");
    }

    public Map<Integer, List<SsqDocumentRow>> loadPendingDocumentGroups() {
        Map<Integer, List<SsqDocumentRow>> groups = new LinkedHashMap<>();
        List<SsqDocumentRow> rows = jdbcTemplate.query(
            "SELECT doc_id, spec_group, level_1, level_2, level_3, level_4, doc_title, doc_type, expose, "
                + "site_language, create_datetime, update_datetime, delete_yn, nahp_display_flag, if_trc_id, if_date, "
                + "doc_description "
                + "FROM if_r_ssq_document WHERE if_result = ? ORDER BY doc_id",
            DOCUMENT_ROW_MAPPER, PENDING);
        for (SsqDocumentRow row : rows) {
            groups.computeIfAbsent(row.docId(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    public Map<Integer, List<SsqFileInfoRow>> loadPendingFileGroups() {
        Map<Integer, List<SsqFileInfoRow>> groups = new LinkedHashMap<>();
        List<SsqFileInfoRow> rows = jdbcTemplate.query(
            "SELECT doc_id, doc_type, doc_title, version_id, version_name, version_desc, version_expose, "
                + "file_id, file_key, file_name, file_lang, file_size, file_expose, size_flag, "
                + "version_update_datetime, file_upsert_datetime, if_trc_id, if_date "
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
        jdbcTemplate.update("UPDATE if_r_ssq_document SET if_result = ? WHERE doc_id = ? AND if_result = ?",
            ifResult, docId, PENDING);
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

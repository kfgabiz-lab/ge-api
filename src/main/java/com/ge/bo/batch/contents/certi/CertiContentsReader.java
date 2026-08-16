package com.ge.bo.batch.contents.certi;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IF_R_CERTI_MASTER 원천 조회 및 IF_RESULT 갱신 Reader — CATALOG/SSQ Reader와 동일한 이유로
 * 오케스트레이터(CertiContentsBatchService)와 다른 Bean으로 분리했다.
 * CATALOG/SSQ와 동일하게 JPA 엔티티 대신 JdbcTemplate + RowMapper로 읽는다(일관성 목적 — CERTI 자체는
 * 복합키 NULL 위험이 실측상 없었다, 2026-07-31 확인).
 * CERTI는 한 테이블에 문서·카테고리·부가정보가 모두 들어있어 별도 파일 IF가 없다(헤더-파일 불일치 케이스 자체가 없음).
 */
@Component
@RequiredArgsConstructor
public class CertiContentsReader {

    private static final String PENDING = "N";

    private static final RowMapper<CertiRow> ROW_MAPPER = (rs, rowNum) -> new CertiRow(
        rs.getString("certi_no"), rs.getString("bi"), rs.getObject("nahp_level_seq", Short.class), rs.getString("plant"),
        rs.getString("plant_name"), rs.getString("certi_type"), rs.getString("certi_typename"), rs.getString("certi_org"),
        rs.getString("certi_orgname"), toLocalDate(rs.getDate("certi_begin_date")),
        toLocalDate(rs.getDate("last_certi_renewal_date")), toLocalDate(rs.getDate("last_certi_exp_date")),
        rs.getString("last_certi_acq_no"), rs.getString("pdt_bigclass"), rs.getString("pdt_bigclassname"),
        rs.getString("pdt_middleclass"), rs.getString("pdt_middleclassname"), rs.getString("pdt_series"),
        rs.getString("pdt_series_name"), rs.getString("pdt_name"), rs.getString("certi_status"),
        toLocalDate(rs.getDate("certi_disuse_date")), rs.getString("last_certi_file"),
        toLocalDate(rs.getDate("update_date")), rs.getString("nahp_disp_flag"), rs.getString("cportal_disp_flag"),
        rs.getString("nahp_title"), rs.getString("nahp_lang"), rs.getString("nahp_level1_id"),
        rs.getString("nahp_level2_id"), rs.getString("nahp_level3_id"), rs.getString("if_trc_id"),
        toLocalDateTime(rs.getTimestamp("if_date")));

    private final JdbcTemplate jdbcTemplate;

    private static LocalDate toLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    private static java.time.LocalDateTime toLocalDateTime(java.sql.Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * 미처리 행 중 복합키(certi_no, bi, nahp_level_seq) 중복 목록 조회(로그용) — quarantineDuplicateKeys()와 짝.
     * 격리 대상(extra) 행과 함께 같은 키에서 채택돼 남는(kept) 행의 if_date도 내려줘서, 어드민 화면에서
     * "이 키는 결국 어느 행이 처리됐는지" 바로 보여줄 수 있게 한다(quarantineDuplicateKeys()의 UPDATE 로직
     * 자체는 건드리지 않고 이 조회 쿼리만 kept 행 조인을 추가했다).
     */
    @Transactional(readOnly = true)
    public List<Object[]> findDuplicateKeyRows() {
        return jdbcTemplate.query(
            "SELECT extra.certi_no, extra.bi, extra.nahp_level_seq, kept.if_date AS kept_if_date, extra.if_trc_id "
                + "FROM if_r_certi_master extra "
                + "JOIN (SELECT DISTINCT ON (certi_no, bi, nahp_level_seq) certi_no, bi, nahp_level_seq, if_date, ctid "
                + "      FROM if_r_certi_master WHERE if_result = 'N' "
                + "      ORDER BY certi_no, bi, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC"
                + "     ) kept "
                + "  ON kept.certi_no = extra.certi_no AND kept.bi = extra.bi "
                + "  AND kept.nahp_level_seq IS NOT DISTINCT FROM extra.nahp_level_seq "
                + "WHERE extra.if_result = 'N' AND extra.ctid <> kept.ctid",
            (rs, rowNum) -> new Object[]{rs.getObject("certi_no"), rs.getObject("bi"), rs.getObject("nahp_level_seq"),
                rs.getTimestamp("kept_if_date"), rs.getString("if_trc_id")});
    }

    /** 복합키 중복 행(가장 이른 1건 제외) 격리(E) — loadPendingGroups() 호출 전에 먼저 실행해야 한다 */
    @Transactional
    public int quarantineDuplicateKeys() {
        return jdbcTemplate.update(
            "UPDATE if_r_certi_master SET if_result = 'E' "
                + "WHERE if_result = 'N' AND ctid NOT IN ("
                + "  SELECT DISTINCT ON (certi_no, bi, nahp_level_seq) ctid FROM if_r_certi_master"
                + "  WHERE if_result = 'N' ORDER BY certi_no, bi, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC)");
    }

    /** key = "CERTI_NO|BI" — 인증서 자연키(source_doc_key)와 동일한 형식 */
    public Map<String, List<CertiRow>> loadPendingGroups() {
        Map<String, List<CertiRow>> groups = new LinkedHashMap<>();
        List<CertiRow> rows = jdbcTemplate.query(
            "SELECT certi_no, bi, nahp_level_seq, plant, plant_name, certi_type, certi_typename, certi_org, "
                + "certi_orgname, certi_begin_date, last_certi_renewal_date, last_certi_exp_date, last_certi_acq_no, "
                + "pdt_bigclass, pdt_bigclassname, pdt_middleclass, pdt_middleclassname, pdt_series, pdt_series_name, "
                + "pdt_name, certi_status, certi_disuse_date, last_certi_file, update_date, nahp_disp_flag, "
                + "cportal_disp_flag, nahp_title, nahp_lang, nahp_level1_id, nahp_level2_id, nahp_level3_id, "
                + "if_trc_id, if_date "
                + "FROM if_r_certi_master WHERE if_result = ? ORDER BY certi_no, bi",
            ROW_MAPPER, PENDING);
        for (CertiRow row : rows) {
            groups.computeIfAbsent(row.certiNo() + "|" + row.bi(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    @Transactional
    public void markIfResult(String certiNo, String bi, String ifResult) {
        jdbcTemplate.update(
            "UPDATE if_r_certi_master SET if_result = ? WHERE certi_no = ? AND bi = ? AND if_result = ?",
            ifResult, certiNo, bi, PENDING);
    }
}

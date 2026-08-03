package com.ge.bo.batch.contents.catalog;

import com.ge.bo.entity.IfCatalogFileInfo;
import com.ge.bo.repository.IfCatalogFileInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * IF_R_CATALOG_INFO / IF_R_CATALOG_FILE_INFO 원천 조회 및 IF_RESULT 갱신 Reader.
 *
 * if_r_catalog_info는 nahp_level_seq(복합키 구성요소)가 NULL인 행이 섞여 있어 JPA @IdClass로 엔티티 식별자를
 * 만들 수 없다(2026-07-31 확인, 대기행 전량 null 반환) — JPA 엔티티 대신 JdbcTemplate + RowMapper로 읽는다.
 * if_r_catalog_file_info는 물리 PK가 있어 그대로 JPA 엔티티로 다룬다.
 * CatalogContentsBatchService(오케스트레이터)와 다른 Bean으로 분리해 @Transactional이 프록시를 타도록 한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogContentsReader {

    private static final String PENDING = "N";

    private static final RowMapper<CatalogHeaderRow> HEADER_ROW_MAPPER = (rs, rowNum) -> new CatalogHeaderRow(
        rs.getString("ctlg_code"), rs.getObject("nahp_level_seq", Short.class), rs.getString("ctlg_name"),
        rs.getString("data_code"), rs.getString("use_yn"), rs.getString("prt_yymm"), rs.getString("prt_ver"),
        rs.getString("nahp_disp_yn"), rs.getString("ctp_disp_yn"), rs.getString("nahp_lang"), rs.getString("nahp_title"),
        toLocalDateTime(rs.getTimestamp("updated_date")), rs.getObject("nahp_video_prod_standard", Short.class),
        rs.getString("nahp_level1_id"), rs.getString("nahp_level2_id"), rs.getString("nahp_level3_id"));

    private final JdbcTemplate jdbcTemplate;
    private final IfCatalogFileInfoRepository ifCatalogFileInfoRepository;

    private static java.time.LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * 미처리 행 중 복합키(ctlg_code, nahp_level_seq) 중복 목록 조회(로그용) — quarantineDuplicateKeys()와 짝.
     * 격리 대상(extra) 행과 함께 같은 키에서 채택돼 남는(kept) 행의 if_date도 내려줘서, 어드민 화면에서
     * "이 키는 결국 어느 행이 처리됐는지" 바로 보여줄 수 있게 한다(nahp_level_seq가 NULL인 행이 있어
     * NULL-safe 비교(IS NOT DISTINCT FROM)로 같은 키 그룹을 다시 찾는다 — quarantineDuplicateKeys()의
     * UPDATE 로직 자체는 건드리지 않고 이 조회 쿼리만 kept 행 조인을 추가했다).
     */
    @Transactional(readOnly = true)
    public List<Object[]> findDuplicateKeyRows() {
        return jdbcTemplate.query(
            "SELECT extra.ctlg_code, extra.nahp_level_seq, kept.if_date AS kept_if_date "
                + "FROM if_r_catalog_info extra "
                + "JOIN (SELECT DISTINCT ON (ctlg_code, nahp_level_seq) ctlg_code, nahp_level_seq, if_date, ctid "
                + "      FROM if_r_catalog_info WHERE if_result = 'N' "
                + "      ORDER BY ctlg_code, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC"
                + "     ) kept "
                + "  ON kept.ctlg_code = extra.ctlg_code AND kept.nahp_level_seq IS NOT DISTINCT FROM extra.nahp_level_seq "
                + "WHERE extra.if_result = 'N' AND extra.ctid <> kept.ctid",
            (rs, rowNum) -> new Object[]{rs.getObject("ctlg_code"), rs.getObject("nahp_level_seq"), rs.getTimestamp("kept_if_date")});
    }

    /** 복합키 중복 행(가장 이른 1건 제외) 격리(E) — loadPendingHeaderGroups() 호출 전에 먼저 실행해야 한다 */
    @Transactional
    public int quarantineDuplicateKeys() {
        return jdbcTemplate.update(
            "UPDATE if_r_catalog_info SET if_result = 'E' "
                + "WHERE if_result = 'N' AND ctid NOT IN ("
                + "  SELECT DISTINCT ON (ctlg_code, nahp_level_seq) ctid FROM if_r_catalog_info"
                + "  WHERE if_result = 'N' ORDER BY ctlg_code, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC)");
    }

    public Map<String, List<CatalogHeaderRow>> loadPendingHeaderGroups() {
        Map<String, List<CatalogHeaderRow>> groups = new LinkedHashMap<>();
        List<CatalogHeaderRow> rows = jdbcTemplate.query(
            "SELECT ctlg_code, nahp_level_seq, ctlg_name, data_code, use_yn, prt_yymm, prt_ver, nahp_disp_yn, "
                + "ctp_disp_yn, nahp_lang, nahp_title, updated_date, nahp_video_prod_standard, nahp_level1_id, "
                + "nahp_level2_id, nahp_level3_id FROM if_r_catalog_info WHERE if_result = ? ORDER BY ctlg_code",
            HEADER_ROW_MAPPER, PENDING);
        for (CatalogHeaderRow row : rows) {
            groups.computeIfAbsent(row.ctlgCode(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public Map<String, List<IfCatalogFileInfo>> loadPendingFileGroups() {
        Map<String, List<IfCatalogFileInfo>> groups = new LinkedHashMap<>();
        try (Stream<IfCatalogFileInfo> stream = ifCatalogFileInfoRepository.streamPending(PENDING)) {
            stream.forEach(row -> groups.computeIfAbsent(row.getCtlgCode(), k -> new ArrayList<>()).add(row));
        }
        return groups;
    }

    /** 문서 처리 결과에 따라 헤더·파일 IF의 if_result를 'P'(성공) 또는 'E'(격리)로 갱신 */
    @Transactional
    public void markIfResult(String ctlgCode, String ifResult) {
        jdbcTemplate.update("UPDATE if_r_catalog_info SET if_result = ? WHERE ctlg_code = ? AND if_result = ?",
            ifResult, ctlgCode, PENDING);
        ifCatalogFileInfoRepository.updateIfResultByCtlgCode(ctlgCode, ifResult);
    }

    /** 헤더 없이 파일 IF만 존재하는 ctlg_code의 파일 행만 별도로 격리 표시할 때 사용 */
    @Transactional
    public void markFileResultOnly(String ctlgCode, String ifResult) {
        ifCatalogFileInfoRepository.updateIfResultByCtlgCode(ctlgCode, ifResult);
    }
}

package com.ge.bo.batch.contents.catalog;

import java.time.LocalDateTime;

/**
 * IF_R_CATALOG_INFO 원천 행 — nahp_level_seq(복합키 구성요소)가 NULL인 행이 섞여 있어(2026-07-31 확인,
 * 현재 대기행 전량 NULL) JPA @IdClass로는 엔티티 식별자를 만들 수 없어 JPA 엔티티 대신 JdbcTemplate +
 * RowMapper로 읽는 순수 데이터 캐리어로 전환했다.
 */
public record CatalogHeaderRow(String ctlgCode, Short nahpLevelSeq, String ctlgName, String dataCode, String useYn,
                                String prtYymm, String prtVer, String nahpDispYn, String ctpDispYn, String nahpLang,
                                String nahpTitle, LocalDateTime updatedDate, Short nahpVideoProdStandard,
                                String nahpLevel1Id, String nahpLevel2Id, String nahpLevel3Id) {
}

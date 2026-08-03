package com.ge.bo.batch.contents.ssq;

/**
 * IF_R_SSQ_DOCUMENT 원천 행 — level_3/level_4(복합키 구성요소)가 거의 항상 NULL이라 JPA @IdClass로는
 * 엔티티 식별자를 만들 수 없어(모든 행이 null로 깨짐, 2026-07-31 확인) JPA 엔티티 대신 JdbcTemplate +
 * RowMapper로 읽는 순수 데이터 캐리어로 전환했다.
 */
public record SsqDocumentRow(Integer docId, String specGroup, String level1, String level2, String level3,
                              String level4, String docTitle, String docType, Boolean expose, String siteLanguage,
                              String createDatetime, String updateDatetime, String deleteYn, String nahpDisplayFlag) {
}

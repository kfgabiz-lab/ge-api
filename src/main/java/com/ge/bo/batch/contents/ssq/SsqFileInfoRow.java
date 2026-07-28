package com.ge.bo.batch.contents.ssq;

/**
 * IF_R_SSQ_FILE_INFO 원천 행 — 실제 물리 테이블에 PK가 없어 JPA 엔티티 대신 JdbcTemplate + RowMapper로 읽는
 * 순수 데이터 캐리어. contents_version/contents_file 저장에 필요한 컬럼만 담는다.
 */
public record SsqFileInfoRow(Integer docId, String docType, String docTitle, Integer versionId, String versionName,
                              String versionDesc, Boolean versionExpose, Integer fileId, String fileKey,
                              String fileName, String fileLang, Long fileSize, Boolean fileExpose, String sizeFlag,
                              String versionUpdateDatetime, String fileUpsertDatetime) {
}

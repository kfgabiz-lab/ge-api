package com.ge.bo.batch.contents.certi;

import java.time.LocalDate;

/**
 * IF_R_CERTI_MASTER 원천 행 — CATALOG/SSQ와 동일한 이유(nahp_level_seq 등 복합키 구성요소 NULL 위험)를
 * 원천 차단하기 위해 JPA 엔티티 대신 JdbcTemplate + RowMapper로 읽는 순수 데이터 캐리어로 통일했다
 * (CERTI 자체는 실측상 NULL 위험이 없었으나, 다른 두 소스와 일관된 방식으로 맞춘다).
 */
public record CertiRow(String certiNo, String bi, Short nahpLevelSeq, String plant, String plantName,
                        String certiType, String certiTypeName, String certiOrg, String certiOrgName,
                        LocalDate certiBeginDate, LocalDate lastCertiRenewalDate, LocalDate lastCertiExpDate,
                        String lastCertiAcqNo, String pdtBigclass, String pdtBigclassName, String pdtMiddleclass,
                        String pdtMiddleclassName, String pdtSeries, String pdtSeriesName, String pdtName,
                        String certiStatus, LocalDate certiDisuseDate, String lastCertiFile, LocalDate updateDate,
                        String nahpDispFlag, String cportalDispFlag, String nahpTitle, String nahpLang,
                        String nahpLevel1Id, String nahpLevel2Id, String nahpLevel3Id) {
}

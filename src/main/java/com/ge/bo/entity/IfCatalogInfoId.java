package com.ge.bo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IF_R_CATALOG_INFO 복합 PK — 실제 개발DB 물리 스키마 기준(ctlg_code, nahp_level_seq)
 * 이 IF의 1행 = '문서 하나가 카테고리 한 곳에 등록된 것'이라 카테고리 등록 순번까지 PK에 포함된다
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IfCatalogInfoId implements Serializable {
    private String ctlgCode;
    private Short nahpLevelSeq;
}

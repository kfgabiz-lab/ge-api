package com.ge.bo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IF_R_CATALOG_FILE_INFO 복합 PK — 실제 개발DB 물리 스키마 기준(ctlg_code, data_code)
 * 주의: file_seq는 PK에 포함되지 않음 — DATA_CODE당 파일 1건만 물리적으로 허용되는 구조로 보임(체크리스트 참고)
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IfCatalogFileInfoId implements Serializable {
    private String ctlgCode;
    private String dataCode;
}

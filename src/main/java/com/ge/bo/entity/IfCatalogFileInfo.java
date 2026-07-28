package com.ge.bo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IF_R_CATALOG_FILE_INFO(카탈로그 파일 IF) 읽기 전용 엔티티 — EAI가 적재하는 원천 테이블(if_r_catalog_file_info)
 * 이 프로젝트는 스키마를 소유하지 않음(EAI 관리) — DDL 미포함, 실제 물리 스키마를 조회해 정확히 매핑함(2026-07-14 확인).
 * ★ 실물 확인 결과 매핑정의서 03시트의 USE_YN 컬럼이 이 테이블에 물리적으로 존재하지 않음(컬럼 순번 4~6 결번 —
 *   과거 삭제된 것으로 추정). file_expose는 헤더(if_r_catalog_info) USE_YN 값을 그대로 사용한다(2026-07-24 확정 —
 *   CatalogContentsConverter 참고).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "if_r_catalog_file_info")
@IdClass(IfCatalogFileInfoId.class)
public class IfCatalogFileInfo {

    @Id
    @Column(name = "ctlg_code", length = 40)
    private String ctlgCode;

    @Id
    @Column(name = "data_code", length = 2)
    private String dataCode;

    @Column(name = "file_seq")
    private Short fileSeq;

    @Column(name = "file_name", length = 2000)
    private String fileName;

    @Column(name = "file_ori", length = 2000)
    private String fileOri;

    @Column(name = "file_src", length = 8000)
    private String fileSrc;

    @Column(name = "file_size", length = 100)
    private String fileSize;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "if_result", length = 1)
    private String ifResult;

    @Column(name = "if_trc_id", length = 36)
    private String ifTrcId;

    @Column(name = "if_date")
    private LocalDateTime ifDate;

    @Builder
    public IfCatalogFileInfo(String ctlgCode, String dataCode, Short fileSeq, String fileName, String fileOri,
                             String fileSrc, String fileSize, LocalDateTime createdDate, String ifResult,
                             String ifTrcId, LocalDateTime ifDate) {
        this.ctlgCode = ctlgCode;
        this.dataCode = dataCode;
        this.fileSeq = fileSeq;
        this.fileName = fileName;
        this.fileOri = fileOri;
        this.fileSrc = fileSrc;
        this.fileSize = fileSize;
        this.createdDate = createdDate;
        this.ifResult = ifResult;
        this.ifTrcId = ifTrcId;
        this.ifDate = ifDate;
    }
}

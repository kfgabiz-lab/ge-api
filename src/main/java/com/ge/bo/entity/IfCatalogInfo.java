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
 * IF_R_CATALOG_INFO(카탈로그 헤더 IF) 읽기 전용 엔티티 — EAI가 적재하는 원천 테이블(if_r_catalog_info)
 * 이 프로젝트는 스키마를 소유하지 않음(EAI 관리) — DDL 미포함, 실제 물리 스키마를 조회해 정확히 매핑함(2026-07-14 확인).
 * 배치는 if_result='N' 행만 조회하고, 처리 후 if_result를 'P'(성공) 또는 'E'(격리)로만 갱신한다.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "if_r_catalog_info")
@IdClass(IfCatalogInfoId.class)
public class IfCatalogInfo {

    @Id
    @Column(name = "ctlg_code", length = 40)
    private String ctlgCode;

    @Id
    @Column(name = "nahp_level_seq")
    private Short nahpLevelSeq;

    @Column(name = "ctlg_name", length = 600)
    private String ctlgName;

    @Column(name = "data_code", length = 2)
    private String dataCode;

    @Column(name = "use_yn", length = 1)
    private String useYn;

    @Column(name = "prt_yymm", length = 20)
    private String prtYymm;

    @Column(name = "prt_ver", length = 4)
    private String prtVer;

    @Column(name = "nahp_disp_yn", length = 1)
    private String nahpDispYn;

    /** 고객포탈(CTP) 노출 플래그 — CTP_LINK_YN(파일 삭제 대상 체크 기준표)에 대응, 2026-07-24 매핑 추가 */
    @Column(name = "ctp_disp_yn", length = 1)
    private String ctpDispYn;

    @Column(name = "nahp_lang", length = 4)
    private String nahpLang;

    @Column(name = "nahp_title", length = 800)
    private String nahpTitle;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "nahp_video_prod_standard")
    private Short nahpVideoProdStandard;

    @Column(name = "nahp_level1_id", length = 200)
    private String nahpLevel1Id;

    @Column(name = "nahp_level2_id", length = 200)
    private String nahpLevel2Id;

    @Column(name = "nahp_level3_id", length = 200)
    private String nahpLevel3Id;

    @Column(name = "if_result", length = 1)
    private String ifResult;

    @Column(name = "if_trc_id", length = 36)
    private String ifTrcId;

    @Column(name = "if_date")
    private LocalDateTime ifDate;

    @Builder
    public IfCatalogInfo(String ctlgCode, Short nahpLevelSeq, String ctlgName, String dataCode, String useYn,
                         String prtYymm, String prtVer, String nahpDispYn, String ctpDispYn, String nahpLang, String nahpTitle,
                         LocalDateTime updatedDate, Short nahpVideoProdStandard, String nahpLevel1Id,
                         String nahpLevel2Id, String nahpLevel3Id, String ifResult, String ifTrcId, LocalDateTime ifDate) {
        this.ctlgCode = ctlgCode;
        this.nahpLevelSeq = nahpLevelSeq;
        this.ctlgName = ctlgName;
        this.dataCode = dataCode;
        this.useYn = useYn;
        this.prtYymm = prtYymm;
        this.prtVer = prtVer;
        this.nahpDispYn = nahpDispYn;
        this.ctpDispYn = ctpDispYn;
        this.nahpLang = nahpLang;
        this.nahpTitle = nahpTitle;
        this.updatedDate = updatedDate;
        this.nahpVideoProdStandard = nahpVideoProdStandard;
        this.nahpLevel1Id = nahpLevel1Id;
        this.nahpLevel2Id = nahpLevel2Id;
        this.nahpLevel3Id = nahpLevel3Id;
        this.ifResult = ifResult;
        this.ifTrcId = ifTrcId;
        this.ifDate = ifDate;
    }
}

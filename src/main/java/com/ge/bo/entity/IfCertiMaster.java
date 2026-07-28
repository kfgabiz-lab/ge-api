package com.ge.bo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IF_R_CERTI_MASTER(인증서 IF) 읽기 전용 엔티티 — EAI가 적재하는 원천 테이블(if_r_certi_master)
 * 이 프로젝트는 스키마를 소유하지 않음(EAI 관리) — DDL 미포함, 실제 물리 스키마를 조회해 정확히 매핑함(2026-07-14 확인).
 * input_mail/input_date/update_mail(개인정보 성격)은 매핑정의서상 미사용 컬럼이라 매핑하지 않는다.
 * cportal_disp_flag(고객포탈 노출 플래그)는 파일 삭제 대상 체크 기준표(CPORTAL_VIEW_FLAG) 대응으로 2026-07-24 매핑 추가.
 * CERTI는 최근 변경분만 보내는 델타 방식이라 문서 삭제는 감지하지 않는다(ContentsWriter에서도 null 보존 정책 적용).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "if_r_certi_master")
@IdClass(IfCertiMasterId.class)
public class IfCertiMaster {

    @Id
    @Column(name = "certi_no", length = 22)
    private String certiNo;

    @Id
    @Column(name = "bi", length = 10)
    private String bi;

    @Id
    @Column(name = "nahp_level_seq")
    private Short nahpLevelSeq;

    @Column(name = "plant", length = 8)
    private String plant;

    @Column(name = "plant_name", length = 100)
    private String plantName;

    @Column(name = "certi_type", length = 6)
    private String certiType;

    @Column(name = "certi_typename", length = 100)
    private String certiTypeName;

    @Column(name = "certi_org", length = 8)
    private String certiOrg;

    @Column(name = "certi_orgname", length = 100)
    private String certiOrgName;

    @Column(name = "certi_begin_date")
    private LocalDate certiBeginDate;

    @Column(name = "last_certi_renewal_date")
    private LocalDate lastCertiRenewalDate;

    @Column(name = "last_certi_exp_date")
    private LocalDate lastCertiExpDate;

    @Column(name = "last_certi_acq_no", length = 100)
    private String lastCertiAcqNo;

    @Column(name = "pdt_bigclass", length = 20)
    private String pdtBigclass;

    @Column(name = "pdt_bigclassname", length = 200)
    private String pdtBigclassName;

    @Column(name = "pdt_middleclass", length = 20)
    private String pdtMiddleclass;

    @Column(name = "pdt_middleclassname", length = 200)
    private String pdtMiddleclassName;

    @Column(name = "pdt_series", length = 20)
    private String pdtSeries;

    @Column(name = "pdt_series_name", length = 200)
    private String pdtSeriesName;

    @Column(name = "pdt_name", columnDefinition = "TEXT")
    private String pdtName;

    @Column(name = "certi_status", length = 100)
    private String certiStatus;

    @Column(name = "certi_disuse_date")
    private LocalDate certiDisuseDate;

    @Column(name = "last_certi_file", length = 4000)
    private String lastCertiFile;

    @Column(name = "update_date")
    private LocalDate updateDate;

    @Column(name = "nahp_disp_flag", length = 1)
    private String nahpDispFlag;

    @Column(name = "cportal_disp_flag", length = 1)
    private String cportalDispFlag;

    @Column(name = "nahp_title", length = 400)
    private String nahpTitle;

    @Column(name = "nahp_lang", length = 10)
    private String nahpLang;

    @Column(name = "nahp_level1_id", length = 50)
    private String nahpLevel1Id;

    @Column(name = "nahp_level2_id", length = 50)
    private String nahpLevel2Id;

    @Column(name = "nahp_level3_id", length = 50)
    private String nahpLevel3Id;

    @Column(name = "if_result", length = 1)
    private String ifResult;

    @Column(name = "if_trc_id", length = 36)
    private String ifTrcId;

    @Column(name = "if_date")
    private LocalDateTime ifDate;

    @Builder
    public IfCertiMaster(String certiNo, String bi, Short nahpLevelSeq, String plant, String plantName,
                        String certiType, String certiTypeName, String certiOrg, String certiOrgName,
                        LocalDate certiBeginDate, LocalDate lastCertiRenewalDate, LocalDate lastCertiExpDate,
                        String lastCertiAcqNo, String pdtBigclass, String pdtBigclassName, String pdtMiddleclass,
                        String pdtMiddleclassName, String pdtSeries, String pdtSeriesName, String pdtName,
                        String certiStatus, LocalDate certiDisuseDate, String lastCertiFile, LocalDate updateDate,
                        String nahpDispFlag, String cportalDispFlag, String nahpTitle, String nahpLang, String nahpLevel1Id,
                        String nahpLevel2Id, String nahpLevel3Id, String ifResult, String ifTrcId, LocalDateTime ifDate) {
        this.certiNo = certiNo;
        this.bi = bi;
        this.nahpLevelSeq = nahpLevelSeq;
        this.plant = plant;
        this.plantName = plantName;
        this.certiType = certiType;
        this.certiTypeName = certiTypeName;
        this.certiOrg = certiOrg;
        this.certiOrgName = certiOrgName;
        this.certiBeginDate = certiBeginDate;
        this.lastCertiRenewalDate = lastCertiRenewalDate;
        this.lastCertiExpDate = lastCertiExpDate;
        this.lastCertiAcqNo = lastCertiAcqNo;
        this.pdtBigclass = pdtBigclass;
        this.pdtBigclassName = pdtBigclassName;
        this.pdtMiddleclass = pdtMiddleclass;
        this.pdtMiddleclassName = pdtMiddleclassName;
        this.pdtSeries = pdtSeries;
        this.pdtSeriesName = pdtSeriesName;
        this.pdtName = pdtName;
        this.certiStatus = certiStatus;
        this.certiDisuseDate = certiDisuseDate;
        this.lastCertiFile = lastCertiFile;
        this.updateDate = updateDate;
        this.nahpDispFlag = nahpDispFlag;
        this.cportalDispFlag = cportalDispFlag;
        this.nahpTitle = nahpTitle;
        this.nahpLang = nahpLang;
        this.nahpLevel1Id = nahpLevel1Id;
        this.nahpLevel2Id = nahpLevel2Id;
        this.nahpLevel3Id = nahpLevel3Id;
        this.ifResult = ifResult;
        this.ifTrcId = ifTrcId;
        this.ifDate = ifDate;
    }
}

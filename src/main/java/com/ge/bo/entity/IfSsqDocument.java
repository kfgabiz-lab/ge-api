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
 * IF_R_SSQ_DOCUMENT(SSQ 문서 IF) 읽기 전용 엔티티 — EAI가 적재하는 원천 테이블(if_r_ssq_document)
 * 이 프로젝트는 스키마를 소유하지 않음(EAI 관리) — DDL 미포함, 실제 물리 스키마를 조회해 정확히 매핑함(2026-07-14 확인).
 * ctp_display_flag는 매핑정의서상 미사용 컬럼이라 매핑하지 않는다.
 * create_datetime/update_datetime은 이미 UTC 문자열(자릿수 유동 포맷)이라 시간대 변환 없이 관대한 파싱만 적용한다.
 *
 * ※ ctp_display_flag/nahp_display_flag는 원래 bit였으나 EAI 쪽에서 varchar(1)(실측 character(1), 값 't'/'f')로
 * 변경됨(2026-07-16 통보) — nahp_display_flag를 Boolean이 아닌 String으로 매핑한다. ctp_display_flag는 여전히
 * 미사용이라 이 타입 변경이 우리 코드에 영향 없다.
 *
 * ※ 실제 물리 PK는 (doc_id, spec_group, level_1, level_2, level_3, level_4) 6개 컬럼이다(2026-07-15 재확인).
 * spec_group만으로는 같은 doc_id 안에서 유일하지 않다(예: doc_id=3103의 spec_group='XGB'가 32건) — level_1~4까지
 * 전부 @Id로 선언해야 Hibernate가 서로 다른 행을 별개 엔티티로 인식한다(누락 시 세션 1차 캐시가 행을 뭉갬).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "if_r_ssq_document")
@IdClass(IfSsqDocumentId.class)
public class IfSsqDocument {

    @Id
    @Column(name = "doc_id")
    private Integer docId;

    @Id
    @Column(name = "spec_group", columnDefinition = "TEXT")
    private String specGroup;

    @Id
    @Column(name = "level_1", length = 255)
    private String level1;

    @Id
    @Column(name = "level_2", length = 255)
    private String level2;

    @Id
    @Column(name = "level_3", length = 255)
    private String level3;

    @Id
    @Column(name = "level_4", length = 255)
    private String level4;

    @Column(name = "doc_title", length = 255)
    private String docTitle;

    @Column(name = "doc_type", length = 255)
    private String docType;

    @Column(nullable = false)
    private Boolean expose;

    @Column(name = "site_language", length = 255)
    private String siteLanguage;

    @Column(name = "create_datetime", length = 24)
    private String createDatetime;

    @Column(name = "update_datetime", length = 24)
    private String updateDatetime;

    @Column(name = "delete_yn", length = 1)
    private String deleteYn;

    @Column(name = "nahp_display_flag", length = 1)
    private String nahpDisplayFlag;

    @Column(name = "if_result", length = 1)
    private String ifResult;

    @Column(name = "if_trc_id", length = 36)
    private String ifTrcId;

    @Column(name = "if_date")
    private LocalDateTime ifDate;

    @Builder
    public IfSsqDocument(Integer docId, String specGroup, String level1, String level2, String level3, String level4,
                        String docTitle, String docType, Boolean expose, String siteLanguage, String createDatetime,
                        String updateDatetime, String deleteYn, String nahpDisplayFlag, String ifResult,
                        String ifTrcId, LocalDateTime ifDate) {
        this.docId = docId;
        this.specGroup = specGroup;
        this.level1 = level1;
        this.level2 = level2;
        this.level3 = level3;
        this.level4 = level4;
        this.docTitle = docTitle;
        this.docType = docType;
        this.expose = expose;
        this.siteLanguage = siteLanguage;
        this.createDatetime = createDatetime;
        this.updateDatetime = updateDatetime;
        this.deleteYn = deleteYn;
        this.nahpDisplayFlag = nahpDisplayFlag;
        this.ifResult = ifResult;
        this.ifTrcId = ifTrcId;
        this.ifDate = ifDate;
    }
}

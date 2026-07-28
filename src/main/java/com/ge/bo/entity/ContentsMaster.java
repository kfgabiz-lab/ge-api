package com.ge.bo.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

/**
 * 콘텐츠 원장 엔티티 — contents_master 테이블 매핑
 * 소스 3곳(CATALOG/SSQ/CERTI)의 문서를 통합 관리하는 기준 테이블
 * (NAHP_콘텐츠테이블_명세서_v1.5 01_contents_master 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_master",
    uniqueConstraints = @UniqueConstraint(name = "uk_contents_master_source_doc", columnNames = {"source_system", "source_doc_key"}),
    indexes = {
        @Index(name = "idx_contents_master_active_type", columnList = "doc_type, source_system, updated_at"),
        @Index(name = "idx_contents_master_delete_check", columnList = "source_system, delete_check_batch_id")
    })
public class ContentsMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CATALOG / SSQ / CERTI */
    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** 소스의 문서 식별자: CATALOG=CTLG_CODE, SSQ=doc_id, CERTI=CERTI_NO|BI */
    @Column(name = "source_doc_key", nullable = false, length = 100, updatable = false)
    private String sourceDocKey;

    /** C(카탈로그)/M(매뉴얼)/D(CAD)/S(소프트웨어)/V(교육영상)/R(인증서)/O(OS/Firmware)/T(기술자료)/Z(기타) */
    @Column(name = "doc_type", nullable = false, length = 2)
    private String docType;

    @Column(name = "doc_title", length = 800)
    private String docTitle;

    @Column(name = "nahp_title", length = 800)
    private String nahpTitle;

    @Column(name = "nahp_lang", length = 10)
    private String nahpLang;

    @Column(nullable = false)
    private Boolean expose;

    /** 소스별 고유 속성(JSON 텍스트) — CERTI 인증정보, CATALOG video_prod_standard 등 */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String attrs;

    @Column(name = "source_created_at")
    private OffsetDateTime sourceCreatedAt;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** 삭제여부검증 도장 — contents_batch_log.batch_id */
    @Column(name = "delete_check_batch_id", nullable = false)
    private Long deleteCheckBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public ContentsMaster(Long id, String sourceSystem, String sourceDocKey, String docType, String docTitle,
                          String nahpTitle, String nahpLang, Boolean expose, String attrs,
                          OffsetDateTime sourceCreatedAt, OffsetDateTime sourceUpdatedAt, Boolean isDeleted,
                          OffsetDateTime deletedAt, Long deleteCheckBatchId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.sourceDocKey = sourceDocKey;
        this.docType = docType;
        this.docTitle = docTitle;
        this.nahpTitle = nahpTitle;
        this.nahpLang = nahpLang;
        this.expose = expose;
        this.attrs = attrs;
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.isDeleted = isDeleted != null ? isDeleted : Boolean.FALSE;
        this.deletedAt = deletedAt;
        this.deleteCheckBatchId = deleteCheckBatchId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 동일 자료코드 프리픽스 내 더 최신 버전에 밀린 문서를 비노출 처리(삭제 아님 — is_deleted는 건드리지 않음) */
    public void hideAsSupersededVersion() {
        this.expose = false;
        this.updatedAt = OffsetDateTime.now();
    }
}

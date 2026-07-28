package com.ge.bo.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

/**
 * 적재 실패 행 보관 엔티티 — contents_if_fail_row 테이블 매핑
 * 정제·변환·저장 중 실패한 IF 행을 원본·사유와 함께 보관 (status로 재처리 대기함+이력 겸용)
 * (NAHP_콘텐츠테이블_명세서_v1.5 06_contents_if_fail_row 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_if_fail_row",
    indexes = {
        @Index(name = "ix_fail_row_pending", columnList = "source_system, status"),
        @Index(name = "ix_fail_row_doc", columnList = "batch_id, source_doc_key")
    })
public class ContentsIfFailRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** 실패 행이 있던 IF 테이블명 (예: if_r_ssq_file_info) */
    @Column(name = "source_table", nullable = false, length = 50, updatable = false)
    private String sourceTable;

    @Column(name = "source_doc_key", length = 100)
    private String sourceDocKey;

    @Column(name = "source_row_key", length = 200)
    private String sourceRowKey;

    /** CLEANSE / CONVERT / UPSERT */
    @Column(name = "fail_step", nullable = false, length = 30, updatable = false)
    private String failStep;

    /** NULL_KEY / PARSE_DATE / UNKNOWN_DOC_TYPE / VALUE_CONFLICT / DOC_NOT_FOUND 등 */
    @Column(name = "fail_code", nullable = false, length = 30, updatable = false)
    private String failCode;

    @Column(name = "fail_detail", columnDefinition = "TEXT")
    private String failDetail;

    /** 실패한 IF 행 전체를 컬럼명:값으로 통째 보관(허용 필드만 — 민감정보 마스킹은 호출 측 책임) */
    @Type(JsonType.class)
    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String rawData;

    /** PENDING / RESOLVED / IGNORED */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "resolved_note", length = 500)
    private String resolvedNote;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ContentsIfFailRow(Long id, Long batchId, String sourceSystem, String sourceTable, String sourceDocKey,
                             String sourceRowKey, String failStep, String failCode, String failDetail, String rawData,
                             String status, String resolvedNote, OffsetDateTime resolvedAt, OffsetDateTime createdAt) {
        this.id = id;
        this.batchId = batchId;
        this.sourceSystem = sourceSystem;
        this.sourceTable = sourceTable;
        this.sourceDocKey = sourceDocKey;
        this.sourceRowKey = sourceRowKey;
        this.failStep = failStep;
        this.failCode = failCode;
        this.failDetail = failDetail;
        this.rawData = rawData;
        this.status = status != null ? status : "PENDING";
        this.resolvedNote = resolvedNote;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
    }
}

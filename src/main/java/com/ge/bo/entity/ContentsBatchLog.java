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
 * 배치 실행 이력 엔티티 — contents_batch_log 테이블 매핑
 * 콘텐츠 4테이블의 delete_check_batch_id 도장이 이 batch_id를 참조한다
 * (NAHP_콘텐츠테이블_명세서_v1.5 05_contents_batch_log 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_batch_log",
    indexes = @Index(name = "ix_batch_log_source", columnList = "source_system, started_at DESC"))
public class ContentsBatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long batchId;

    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    /** CLEANSE / UPSERT / DELETE_CHECK / REPORT */
    @Column(name = "current_step", length = 30)
    private String currentStep;

    @Type(JsonType.class)
    @Column(name = "row_counts", columnDefinition = "jsonb")
    private String rowCounts;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String report;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Builder
    public ContentsBatchLog(Long batchId, String sourceSystem, String status, String currentStep, String rowCounts,
                            String report, String errorMessage, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        this.batchId = batchId;
        this.sourceSystem = sourceSystem;
        this.status = status;
        this.currentStep = currentStep;
        // null 대신 항상 "{}"를 저장 — jsonb 컬럼에 빈 값을 표현할 때 null보다 빈 객체가 다루기 쉬움.
        this.rowCounts = rowCounts != null ? rowCounts : "{}";
        this.report = report != null ? report : "{}";
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public void updateStep(String step) {
        this.currentStep = step;
    }

    public void complete(String status, String rowCounts, String report) {
        this.status = status;
        this.rowCounts = rowCounts;
        this.report = report;
        this.finishedAt = OffsetDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.finishedAt = OffsetDateTime.now();
    }
}

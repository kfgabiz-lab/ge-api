package com.ge.bo.entity;

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

import java.time.OffsetDateTime;

/**
 * 콘텐츠 버전 엔티티 — contents_version 테이블 매핑
 * (NAHP_콘텐츠테이블_명세서_v1.5 03_contents_version 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_version",
    uniqueConstraints = @UniqueConstraint(name = "uk_contents_version_source_key", columnNames = {"contents_id", "source_version_key"}),
    indexes = {
        @Index(name = "idx_contents_version_active_sort", columnList = "contents_id, sort_key, id"),
        @Index(name = "idx_contents_version_delete_check", columnList = "source_system, delete_check_batch_id")
    })
public class ContentsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contents_id", nullable = false, updatable = false)
    private Long contentsId;

    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** SSQ=version_id, CATALOG=PRT_YYMM|PRT_VER, CERTI='DEFAULT' */
    @Column(name = "source_version_key", nullable = false, length = 50, updatable = false)
    private String sourceVersionKey;

    @Column(name = "version_name", length = 255)
    private String versionName;

    @Column(name = "version_desc", columnDefinition = "TEXT")
    private String versionDesc;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "version_expose", nullable = false)
    private Boolean versionExpose;

    @Column(name = "sort_key", nullable = false)
    private Integer sortKey;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "delete_check_batch_id", nullable = false)
    private Long deleteCheckBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public ContentsVersion(Long id, Long contentsId, String sourceSystem, String sourceVersionKey, String versionName,
                           String versionDesc, String videoUrl, Boolean versionExpose, Integer sortKey,
                           OffsetDateTime sourceUpdatedAt, Boolean isDeleted, OffsetDateTime deletedAt,
                           Long deleteCheckBatchId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.contentsId = contentsId;
        this.sourceSystem = sourceSystem;
        this.sourceVersionKey = sourceVersionKey;
        this.versionName = versionName;
        this.versionDesc = versionDesc;
        this.videoUrl = videoUrl;
        this.versionExpose = versionExpose;
        this.sortKey = sortKey != null ? sortKey : 0;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.isDeleted = isDeleted != null ? isDeleted : Boolean.FALSE;
        this.deletedAt = deletedAt;
        this.deleteCheckBatchId = deleteCheckBatchId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 소스 재전송에서 이 버전 행이 더 이상 오지 않아(행 부재) 삭제된 것으로 판단될 때 소프트 삭제 처리 */
    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}

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
 * 콘텐츠 파일 엔티티 — contents_file 테이블 매핑
 * 파일 실체는 타 시스템이 관리 — 파일명·크기·원본 경로 정보만 보관
 * (NAHP_콘텐츠테이블_명세서_v1.5 04_contents_file 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_file",
    uniqueConstraints = @UniqueConstraint(name = "uk_contents_file_source_key", columnNames = {"contents_version_id", "source_file_key"}),
    indexes = {
        @Index(name = "idx_contents_file_active", columnList = "contents_version_id, file_lang, file_ext, id"),
        @Index(name = "idx_contents_file_delete_check", columnList = "source_system, delete_check_batch_id")
    })
public class ContentsFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contents_version_id", nullable = false, updatable = false)
    private Long contentsVersionId;

    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** SSQ=file_id, CATALOG=DATA_CODE|FILE_SEQ, CERTI='DEFAULT' */
    @Column(name = "source_file_key", nullable = false, length = 100, updatable = false)
    private String sourceFileKey;

    @Column(name = "file_name", nullable = false, length = 2000)
    private String fileName;

    @Column(name = "file_ext", length = 20)
    private String fileExt;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_lang", length = 10)
    private String fileLang;

    @Column(name = "source_file_path", nullable = false, columnDefinition = "TEXT")
    private String sourceFilePath;

    /** blob storage 파일 서빙 경로 — "CTP/{doc_type 폴더}/{파일명}" 형식(ContentsNormalizer#buildBlobFilePath) */
    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String attrs;

    @Column(name = "file_expose", nullable = false)
    private Boolean fileExpose;

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
    public ContentsFile(Long id, Long contentsVersionId, String sourceSystem, String sourceFileKey, String fileName,
                        String fileExt, Long fileSize, String fileLang, String sourceFilePath, String filePath, String attrs,
                        Boolean fileExpose, OffsetDateTime sourceUpdatedAt, Boolean isDeleted, OffsetDateTime deletedAt,
                        Long deleteCheckBatchId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.contentsVersionId = contentsVersionId;
        this.sourceSystem = sourceSystem;
        this.sourceFileKey = sourceFileKey;
        this.fileName = fileName;
        this.fileExt = fileExt;
        this.fileSize = fileSize;
        this.fileLang = fileLang;
        this.sourceFilePath = sourceFilePath;
        this.filePath = filePath;
        this.attrs = attrs;
        this.fileExpose = fileExpose;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.isDeleted = isDeleted != null ? isDeleted : Boolean.FALSE;
        this.deletedAt = deletedAt;
        this.deleteCheckBatchId = deleteCheckBatchId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 소스 재전송에서 이 파일 행이 더 이상 오지 않아(행 부재) 삭제된 것으로 판단될 때 소프트 삭제 처리 */
    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}

package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 파일 메타정보 엔티티
 * Slug Entity 시스템의 FILE 타입 필드가 참조하는 범용 파일 메타데이터를 저장
 * - PageFile과 달리 owner 컬럼(template_slug/data_id/field_key)이 없는 독립 메타 테이블
 */
@Entity
@Table(name = "file_meta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class FileMeta {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 사용자가 업로드한 원본 파일명 (예: report.pdf) */
  @Column(name = "original_name", nullable = false, length = 255)
  private String originalName;

  /** 서버 저장명 — UUID + 원본 확장자 (예: a3f2c1d4.pdf), UNIQUE */
  @Column(name = "save_name", nullable = false, unique = true, length = 255)
  private String saveName;

  /** 저장 디렉토리 경로 (예: /uploads/file-meta/2026/07/) */
  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  /** Azure blob storage 불러오기 url — local에서는 null */
  @Column(name = "blob_url", nullable = true, length = 500)
  private String blobUrl;

  /** 파일 크기 (bytes) */
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  /** MIME 타입 (예: application/pdf) */
  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  /** 업로드한 관리자 이메일 — JPA Auditing이 자동 설정 */
  @CreatedBy
  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  /** 업로드일시 — JPA Auditing이 자동 설정 */
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

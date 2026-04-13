package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 페이지 파일 엔티티
 * 레이어 팝업 빌더에서 생성된 폼의 파일 첨부 메타데이터를 저장
 * - data_id = NULL: 폼 저장 전 임시 업로드 상태
 * - data_id = 값: 폼 저장 완료 후 page_data와 연결된 상태
 */
@Entity
@Table(
    name = "page_file",
    indexes = {
        @Index(name = "idx_page_file_data_id",    columnList = "data_id"),
        @Index(name = "idx_page_file_slug_field", columnList = "template_slug, field_key")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class PageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어떤 페이지 템플릿의 파일인지 (예: user-register) */
    @Column(name = "template_slug", nullable = false, length = 255)
    private String templateSlug;

    /** page_data.id 논리 참조 — 폼 저장 전에는 NULL */
    @Column(name = "data_id")
    private Long dataId;

    /** 어떤 필드의 파일인지 (예: attachFiles) */
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    /** 사용자가 업로드한 원본 파일명 (예: report.pdf) */
    @Column(name = "orig_name", nullable = false, length = 255)
    private String origName;

    /** 서버 저장명 — UUID + 원본 확장자 (예: a3f2c1d4.pdf), UNIQUE */
    @Column(name = "save_name", nullable = false, unique = true, length = 255)
    private String saveName;

    /** 저장 디렉토리 경로 (예: /uploads/page-files/2026/03/) */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** 파일 크기 (bytes) */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME 타입 (예: application/pdf) */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** 업로드한 관리자 이메일 */
    @CreatedBy
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 업로드일시 — JPA Auditing이 자동 설정 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

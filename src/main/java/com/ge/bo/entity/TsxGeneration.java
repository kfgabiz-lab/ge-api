package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * TSX 생성 이력 엔티티
 * - [생성] 버튼 클릭 시 TSX 파일 생성과 동시에 이력을 저장
 * - 생성 전용 테이블이므로 updated_at 없음
 */
@Entity
@Table(name = "tsx_generation",
        indexes = {
                @Index(name = "idx_tsx_gen_type",    columnList = "template_type"),
                @Index(name = "idx_tsx_gen_created", columnList = "created_at DESC")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class TsxGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이력 식별 이름 (ex: 게시판 목록) */
    @Column(nullable = false, length = 100)
    private String name;

    /** 생성된 파일의 폴더 경로 (ex: admin/board) */
    @Column(name = "folder_name", nullable = false, length = 100)
    private String folderName;

    /** 생성된 파일명 (ex: page.tsx) */
    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    /** 템플릿 유형 (LIST 또는 LAYER) */
    @Column(name = "template_type", nullable = false, length = 20)
    private String templateType;

    /** 빌더 설정 JSON 전문 — 재편집 기반 */
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    /** 실제 생성된 TSX 코드 전문 */
    @Column(name = "tsx_code", nullable = false, columnDefinition = "TEXT")
    private String tsxCode;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

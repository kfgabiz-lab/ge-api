package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 페이지 템플릿 엔티티
 * 페이지 메이커 설정(configJson)과 생성된 TSX 파일 경로(filePath)를 관리
 */
@Entity
@Table(name = "page_template",
        uniqueConstraints = {
                /* 같은 templateType 안에서만 이름/slug 중복 불가 — 타입이 다르면 동일 이름/slug 허용 */
                @UniqueConstraint(name = "uq_page_template_name_type", columnNames = {"name", "template_type"}),
                @UniqueConstraint(name = "uq_page_template_slug_type", columnNames = {"slug", "template_type"})
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class PageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 템플릿 표시 이름 */
    @Column(nullable = false, length = 100)
    private String name;

    /** URL + 파일경로 식별자 (kebab-case, 소문자+숫자+하이픈) */
    @Column(nullable = false, length = 100)
    private String slug;

    /** 설명 */
    @Column(length = 200)
    private String description;

    /** 템플릿 유형 (현재는 LIST 고정) */
    @Column(name = "template_type", nullable = false, length = 20)
    @Builder.Default
    private String templateType = "LIST";

    /** 재편집용 JSON 설정 (FE SearchFieldConfig / TableColumn 직렬화) */
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    /** 검색폼 접기 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean collapsible = false;

    /** 생성된 TSX 파일 절대경로 (삭제/갱신 시 활용) */
    @Column(name = "file_path", nullable = false, length = 300)
    private String filePath;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

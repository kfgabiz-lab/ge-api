package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 검증 규칙 엔티티 — slug_registry(dataSlug)에 종속된 저장 시점 검증 규칙
 * type=unique: 복합키 중복 방지 (fields 필요, condition 선택)
 * type=maxCount: 조건부 최대 등록 건수 제한 (maxCount 필요, condition 선택)
 */
@Entity
@Table(name = "validation_rule",
    indexes = {
        @Index(name = "idx_validation_rule_slug_registry", columnList = "slug_registry_id")
    }
)
@SQLRestriction("is_deleted = false")
@SQLDelete(sql = "UPDATE validation_rule SET is_deleted = true, deleted_at = now() WHERE id = ?")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class ValidationRule {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종속된 slug 레지스트리 */
  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slug_registry_id", nullable = false)
    private SlugRegistry slugRegistry;

    /** 규칙 유형 — unique(중복방지) | maxCount(최대건수) */
  @Column(nullable = false, length = 20)
    private String type;

    /** unique 전용 — 콤마구분 필드목록 (예: "email,userId") */
  @Column(length = 500)
    private String fields;

    /** unique/maxCount 공통 — 콤마구분 key=value 조건, 암묵적 AND (예: "status='active',type='001'") */
  @Column(length = 500)
    private String condition;

    /** maxCount 전용 — 최대 등록 건수 */
  @Column(name = "max_count")
    private Integer maxCount;

  @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

  @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

  @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

  @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

  @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

  @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

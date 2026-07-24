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
 * Slug 레지스트리 엔티티 — 위젯 빌더 연동용 slug 사전 등록 관리
 */
@Entity
@Table(name = "slug_registry",
    indexes = {
        @Index(name = "idx_slug_registry_type", columnList = "type")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_slug_registry_slug", columnNames = "slug")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SlugRegistry {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** slug 값 — 시스템 내 유일 (등록 후 수정 불가) */
  @Column(nullable = false, length = 100)
    private String slug;

    /** slug 별칭/설명명 */
  @Column(nullable = false, length = 100)
    private String name;

    /** 용도 구분 (PAGE_DATA / PAGE_TEMPLATE / ETC) */
  @Column(nullable = false, length = 20)
    private String type;

    /** 상세 설명 */
  @Column(columnDefinition = "TEXT")
    private String description;

    /** 사용여부 */
  @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** 연결된 Slug Entity (PAGE_DATA 타입 선택 연동, nullable) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private SlugEntity slugEntity;

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

package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Slug Entity — 빌더 연동용 엔티티 구조(필드) 정의 관리
 */
@Entity
@Table(name = "slug_entity",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_slug_entity_slug", columnNames = "slug")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SlugEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 식별자 — 등록 후 수정 불가 */
    @Column(nullable = false, length = 100)
    private String slug;

    /** 표시명 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 매핑되는 DB 테이블명 (entity 생성 기능 추가 시 사용) */
    @Column(name = "table_name", length = 100)
    private String tableName;

    /** 상세 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 사용여부 */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** 마스터(부모) Entity — 이 Entity가 부모 Entity의 PK를 참조하는 자식임을 나타낸다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_entity_id")
    private SlugEntity parentEntity;

    /** 필드 목록 — cascade ALL + orphanRemoval로 일괄 저장/삭제 처리 */
    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<SlugEntityField> fields = new ArrayList<>();

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

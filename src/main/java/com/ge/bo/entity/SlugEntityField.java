package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Slug Entity 필드 정의 — slug_entity의 하위 필드 목록
 */
@Entity
@Table(name = "slug_entity_field",
    indexes = {
        @Index(name = "idx_slug_entity_field_entity", columnList = "entity_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SlugEntityField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 entity */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private SlugEntity entity;

    /** 빌더 참조 키 (예: memberId) — 빌더에서 필드 매핑 시 사용 */
    @Column(name = "key", length = 100)
    private String key;

    /** 화면 표시명 (예: 회원 ID) */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** DB 컬럼명 (snake_case, 예: member_name) — entity 생성 기능 추가 시 사용 */
    @Column(name = "column_name", length = 100)
    private String columnName;

    /** Java 타입 — DB 타입에서 자동 매핑, entity 생성 기능 추가 시 사용 */
    @Column(name = "java_type", length = 50)
    private String javaType;

    /** DB 타입 (VARCHAR / BIGINT / INT / BOOLEAN / TIMESTAMP / NUMERIC 등) */
    @Column(name = "column_type", length = 50)
    private String columnType;

    /** 컬럼 길이 (VARCHAR 등 길이 있는 타입에만 사용) */
    @Column(name = "column_length")
    private Integer columnLength;

    /** PK 여부 — entity 생성 기능 추가 시 사용 */
    @Column(name = "is_pk")
    private Boolean isPk;

    /** NULL 허용 여부 */
    @Column(name = "is_nullable", nullable = false)
    @Builder.Default
    private Boolean isNullable = true;

    /** 기본값 — entity 생성 기능 추가 시 사용 */
    @Column(name = "default_value", length = 200)
    private String defaultValue;

    /** 빌더 필드 타입 (예: input, textarea, date, checkbox 등) — 빌더 자동 매핑 시 columnType보다 우선 적용 */
    @Column(name = "field_type", length = 50)
    private String fieldType;

    /** 공통코드 그룹 코드 — 빌더 select/radio/checkbox 옵션 자동 연결용 */
    @Column(name = "code_group_code", length = 50)
    private String codeGroupCode;

    /** 컬럼 설명 */
    @Column(length = 500)
    private String description;

    /** 필드 표시 순서 */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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

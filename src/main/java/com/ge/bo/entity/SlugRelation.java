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
 * 슬러그 관계 매핑 엔티티
 * 서로 다른 slug에 저장된 데이터 간의 관계를 설정/조회에 활용
 * - FILTER: slave 조건으로 master 목록을 필터링
 * - FETCH:  master 조회 시 관련 slave 데이터를 함께 반환
 */
@Entity
@Table(name = "slug_relation", indexes = {
    @Index(name = "idx_slug_relation_master", columnList = "master_slug"),
    @Index(name = "idx_slug_relation_slave",  columnList = "slave_slug")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SlugRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기준 슬러그 (조회 대상) */
    @Column(name = "master_slug", nullable = false, length = 200)
    private String masterSlug;

    /** 관계 슬러그 (필터링 또는 fetch 기준) */
    @Column(name = "slave_slug", nullable = false, length = 200)
    private String slaveSlug;

    /** master에서 비교할 필드 경로 (예: id, rel.product-data) */
    @Column(name = "master_key", nullable = false, length = 200)
    @Builder.Default
    private String masterKey = "id";

    /** slave에서 master를 참조하는 필드 경로 (예: product.id) */
    @Column(name = "slave_key", nullable = false, length = 200)
    private String slaveKey;

    /** EQ: 단순 동등 / ARRAY_CONTAINS: 배열 포함 */
    @Column(name = "join_type", nullable = false, length = 30)
    @Builder.Default
    private String joinType = "EQ";

    /** slave 조회 시 고정 조건 (예: depth=3), null이면 조건 없음 */
    @Column(name = "slave_filter", length = 200)
    private String slaveFilter;

    /** FILTER: slave→master 필터링 / FETCH: master 조회 시 slave 포함 */
    @Column(name = "relation_dir", nullable = false, length = 20)
    @Builder.Default
    private String relationDir = "FILTER";

    /** FETCH 방향 시 표시할 slave 필드 경로 (예: product.title) */
    @Column(name = "fetch_fields", length = 500)
    private String fetchFields;

    /** FETCH 구분자 (기본 ,) */
    @Column(name = "fetch_separator", nullable = false, length = 10)
    @Builder.Default
    private String fetchSeparator = ",";

    /** FETCH 조회 유형: TABLE=slave에서 직접 추출 / CATEGORY=상위 계층 거슬러 추출 */
    @Column(name = "slave_type", nullable = false, length = 20)
    @Builder.Default
    private String slaveType = "TABLE";

    /** CATEGORY 유형 FETCH 시 표시할 계층 depth (끝 depth, 기본 1) */
    @Column(name = "category_depth", nullable = false)
    @Builder.Default
    private Integer categoryDepth = 1;

    /** CATEGORY 유형 FETCH 시 표시할 계층 시작 depth — null이면 categoryDepth와 동일(단일 depth만 표시), 값이 있으면 startDepth~categoryDepth 범위를 fetchSeparator로 합쳐 표시 */
    @Column(name = "category_depth_from")
    private Integer categoryDepthFrom;

    /** 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;

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

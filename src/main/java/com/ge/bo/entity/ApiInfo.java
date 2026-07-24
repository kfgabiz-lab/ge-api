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
 * API 정보 엔티티 — 백엔드 API 엔드포인트 목록 관리
 */
@Entity
@Table(name = "api_info", indexes = {
    @Index(name = "idx_api_info_category", columnList = "category"),
    @Index(name = "idx_api_info_method", columnList = "method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ApiInfo {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카테고리 코드 — 공통코드 API_CATEGORY 참조 */
  @Column(length = 30)
    private String category;

    /** API 명칭 */
  @Column(nullable = false, length = 100)
    private String name;

    /** HTTP 메서드 (GET/POST/PUT/PATCH/DELETE) */
  @Column(nullable = false, length = 10)
    private String method;

    /** URL 패턴 (예: /api/v1/page-data/{slug}) */
  @Column(name = "url_pattern", nullable = false, length = 300)
    private String urlPattern;

    /** 연결된 엔티티 명 (선택) - JPA 엔티티와 연관 관계 */
  @Column(name = "connected_entity", length = 100)
    private String connectedEntity;

    /** 설명 및 메모 */
  @Column(columnDefinition = "TEXT")
    private String description;

    /** 사용여부 */
  @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

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

package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 메뉴-API 매핑 엔티티 (복합 PK) — 메뉴가 실제 사용하는 API를 관리자가 직접 등록
 */
@Entity
@Table(name = "menu_api", indexes = {
    @Index(name = "idx_menu_api_api_info_id", columnList = "api_info_id")
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(MenuApiId.class)
public class MenuApi {

  @Id
    @Column(name = "menu_id")
    private Long menuId;

  @Id
    @Column(name = "api_info_id")
    private Long apiInfoId;

  @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

  @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

  @PrePersist
    public void prePersist() {
    this.createdAt = LocalDateTime.now();
  }
}

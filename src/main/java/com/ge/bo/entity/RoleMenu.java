package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 역할-메뉴 매핑 엔티티 (복합 PK)
 */
@Entity
@Table(name = "role_menu")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(RoleMenuId.class)
public class RoleMenu {

  @Id
    @Column(name = "role_id")
    private Long roleId;

  @Id
    @Column(name = "menu_id")
    private Long menuId;

  @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

  @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void prePersist() {
    this.createdAt = LocalDateTime.now();
  }
}

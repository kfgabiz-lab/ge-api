package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 역할-메뉴 매핑 엔티티
 */
@Entity
@Table(name = "role_menu", indexes = {
    @Index(name = "idx_role_menu_role_site", columnList = "role_id, site_id"),
    @Index(name = "idx_role_menu_menu_site", columnList = "menu_id, site_id")
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleMenu {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(name = "role_id", nullable = false)
    private Long roleId;

  @Column(name = "menu_id", nullable = false)
    private Long menuId;

  @Column(name = "site_id")
    private Long siteId;

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

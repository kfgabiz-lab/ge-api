package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 역할-메뉴 매핑 엔티티 (복합 PK)
 */
@Entity
@Table(name = "role_menu")
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

  @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void prePersist() {
    this.createdAt = LocalDateTime.now();
  }
}

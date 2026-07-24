package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자-홈페이지 매핑 엔티티 (복합 PK)
 */
@Entity
@Table(name = "admin_user_site")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(AdminUserSiteId.class)
public class AdminUserSite {

  @Id
    @Column(name = "admin_user_id")
    private Long adminUserId;

  @Id
    @Column(name = "site_id")
    private Long siteId;

  @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void prePersist() {
    this.createdAt = LocalDateTime.now();
  }
}

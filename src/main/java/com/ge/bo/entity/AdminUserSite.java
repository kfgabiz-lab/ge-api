package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 관리자-홈페이지 매핑 엔티티 (복합 PK)
 */
@Entity
@Table(name = "admin_user_site")
@SQLRestriction("is_deleted = false")
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

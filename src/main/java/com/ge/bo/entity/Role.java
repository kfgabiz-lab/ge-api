package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Role {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(nullable = false, unique = true, length = 30)
    private String code; // e.g. SUPER_ADMIN, EDITOR

  @Column(nullable = false, length = 20)
    private String displayName;

  @Column(length = 100)
    private String description;

  @Column(nullable = false, length = 7)
    @Builder.Default
    private String color = "#6b7280";

  @Builder.Default
    @Column(nullable = false)
    private boolean isSystem = false; // 시스템 기본 역할은 삭제 불가

  @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

  @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

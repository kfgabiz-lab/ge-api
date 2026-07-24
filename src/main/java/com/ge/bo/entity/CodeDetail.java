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
 * 코드 상세 엔티티
 */
@Entity
@Table(name = "code_detail",
    uniqueConstraints = @UniqueConstraint(name = "uq_code_detail_group_code", columnNames = {"group_id", "code"}),
    indexes = @Index(name = "idx_code_detail_sort", columnList = "group_id, sort_order")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class CodeDetail {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CodeGroup group;

  @Column(nullable = false, length = 30)
    private String code;

  @Column(nullable = false, length = 50)
    private String name;

  @Column(name = "name_msg_key", length = 255)
    private String nameMsgKey;

  @Column(length = 200)
    private String description;

  @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

  @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /* 기타 항목 1~5 (선택값) */
  @Column(length = 100) private String extra1;
  @Column(length = 100) private String extra2;
  @Column(length = 100) private String extra3;
  @Column(length = 100) private String extra4;
  @Column(length = 100) private String extra5;

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

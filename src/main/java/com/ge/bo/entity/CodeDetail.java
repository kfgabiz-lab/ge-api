package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 코드 상세 엔티티
 */
@Entity
@Table(name = "code_detail",
    uniqueConstraints = @UniqueConstraint(name = "uq_code_detail_group_code", columnNames = {"group_id", "code"}),
    indexes = @Index(name = "idx_code_detail_sort", columnList = "group_id, sort_order")
)
@SQLRestriction("is_deleted = false")
@SQLDelete(sql = "UPDATE code_detail SET is_deleted = true, deleted_at = now() WHERE id = ?")
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

  /* 코드명 — nameMsgKey 지정 시 message_resource.ko 값이 저장된다.
     영문 원문을 ko/en 양쪽에 그대로 쓰는 코드(교육 신청 Step4 옵션 등)가 50자를 넘겨 100자로 확장. */
  @Column(nullable = false, length = 100)
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

  @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

  @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

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

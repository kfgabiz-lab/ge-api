package com.ge.bo.entity;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Entity — 테스트서브데이터
 * 생성일시: 2026-07-12T20:46:55.027515800+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.time.OffsetDateTime;

/**
 * 테스트서브데이터 엔티티
 */
@Entity
@Table(name = "test_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TestSubData {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 테스트데이터 ID */
  @Column(name = "test_data_id", nullable = false)
  private Long testDataId;

  /** 제품명 */
  @Column(name = "name", nullable = true)
  private String name;

  /** 게시시작일 */
  @Column(name = "disp_from", nullable = true)
  private OffsetDateTime dispFrom;

  /** 게시종료일 */
  @Column(name = "disp_to", nullable = true)
  private OffsetDateTime dispTo;

  /** 첨부파일1 */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "file1_id", nullable = false, columnDefinition = "bigint[]")
  private List<Long> file1Id;

  /** 첨부파일2 */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "file2_id", nullable = false, columnDefinition = "bigint[]")
  private List<Long> file2Id;

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false, length = 50)
  private String createdBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @LastModifiedBy
  @Column(name = "updated_by", nullable = false, length = 50)
  private String updatedBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

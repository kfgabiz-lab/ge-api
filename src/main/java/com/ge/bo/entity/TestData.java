package com.ge.bo.entity;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Entity — 테스트데이터
 * 생성일시: 2026-07-13T13:30:49.156393800+09:00
 * 원본 Slug Entity: id=18, tableName=test_data
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 테스트데이터 엔티티
 */
@Entity
@Table(name = "test_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TestData {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 타이틀 */
  @Column(name = "titme", nullable = true)
  private String titme;

  /** 내용 */
  @Column(name = "content", nullable = true)
  private String content;

  /** 공개여부 */
  @Column(name = "display", nullable = true)
  private String display;

  /** 주문방식 */
  @Column(name = "order_type", nullable = true)
  private String orderType;

  /** 게시시작시간 */
  @Column(name = "disp_from", nullable = true)
  private OffsetDateTime dispFrom;

  /** 게시종료시간 */
  @Column(name = "disp_to", nullable = true)
  private OffsetDateTime dispTo;

  /** 제품 */
  @Column(name = "products", nullable = true)
  private Long products;

  /** 담당자 */
  @Column(name = "persons", nullable = true)
  private Long persons;

  /** 정보1 */
  @Column(name = "info1", nullable = true)
  private String info1;

  /** 정보2 */
  @Column(name = "info2", nullable = true)
  private String info2;

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

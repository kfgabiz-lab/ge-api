package com.ge.bo.entity;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Entity — 배너
 * 생성일시: 2026-07-12T13:37:46.282663300+09:00
 * 원본 Slug Entity: id=1, tableName=banner
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
 * 배너 엔티티
 * 배너 등록/수정 폼(banner-detail)    필드 재사용 Entity
 */
@Entity
@Table(name = "banner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class BannerData {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 배너    위치 */
  @Column(name = "banner_position", nullable = false)
  private String bannerPosition;

  /** 제목 */
  @Column(name = "title", nullable = false)
  private String title;

  /** 게시기간시작일 */
  @Column(name = "post_date_from", nullable = false)
  private OffsetDateTime postDateFrom;

  /** prefix */
  @Column(name = "prefix", nullable = false)
  private String prefix;

  /** 타이틀 */
  @Column(name = "main_title", nullable = false)
  private String mainTitle;

  /** bottomText */
  @Column(name = "bottom_text", nullable = false)
  private String bottomText;

  /** 서브타이틀 */
  @Column(name = "sub_title", nullable = true)
  private String subTitle;

  /** url */
  @Column(name = "url", nullable = false)
  private String url;

  /** 이미지 */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "image_file_id", nullable = false, columnDefinition = "bigint[]")
  private List<Long> imageFileId;

  /** 정렬순서 */
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  /** 공개여부 */
  @Column(name = "is_visible", nullable = false)
  private String isVisible;

  /** infoSort */
  @Column(name = "info_sort", nullable = true)
  private String infoSort;

  /** 게시기간종료일 */
  @Column(name = "post_date_to", nullable = false)
  private OffsetDateTime postDateTo;

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

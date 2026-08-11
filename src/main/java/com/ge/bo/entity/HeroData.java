package com.ge.bo.entity;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Entity — 히어로
 * 생성일시: 2026-07-11T14:24:12.513193200+09:00
 * 원본 Slug Entity: id=5, tableName=hero_banner
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import jakarta.persistence.*;
import lombok.*;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 히어로 엔티티
 * 히어로 배너 데이터
 */
@Entity
@Table(name = "hero_banner")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class HeroData {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 타이틀 */
  @Column(name = "title", length = 100, nullable = false)
  private String title;

  /** 노출 기간 (시작~종료, _from/_to) */
  @Column(name = "post_date", nullable = false)
  private OffsetDateTime postDate;

  /** 타이틀 텍스트 */
  @Column(name = "title_text", length = 100, nullable = false)
  private String titleText;

  /** 서브 타이틀 */
  @Column(name = "sub", length = 100, nullable = true)
  private String sub;

  /** 버튼 텍스트 */
  @Column(name = "btn_text", length = 20, nullable = true)
  private String btnText;

  /** 버튼 클릭 시 이동 URL */
  @Column(name = "btn_url", length = 255, nullable = true)
  private String btnUrl;

  /** 정렬 순서 (숫자만) */
  @Column(name = "order_no", length = 2, nullable = true)
  private String orderNo;

  /** 이미지/동영상 콘텐츠 */
  @Type(JsonStringType.class)
  @Column(name = "content", nullable = false, columnDefinition = "jsonb")
  private String content;

  /** 항상 "-" 고정 저장되는 숨김 필드 */
  @Column(name = "default_val", length = 10, nullable = true)
  private String defaultVal;

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
  private OffsetDateTime createdAt;

  @LastModifiedBy
  @Column(name = "updated_by", nullable = false, length = 50)
  private String updatedBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 외부연동용 콘텐츠 엔티티
 * blog/articles/press/events(page_data) 등록·수정·삭제 시 동기화되는 연동 테이블
 * - content_id: 원본 page_data.id
 * - type: 'B' Blog, 'A' Article, 'P' Press, 'E' Event
 * - 삭제는 물리삭제 없이 is_visible=false 처리
 */
@Entity
@Table(name = "integration_contents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class IntegrationContents {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "type", nullable = false, length = 20)
  private String type;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "is_visible", nullable = false)
  private boolean visible;

  /** 컨텐츠 원본 id — page_data.id */
  @Column(name = "content_id", nullable = false, length = 20)
  private String contentId;

  /** 파일 id — data_json 내 image 배열의 첫 값 */
  @Column(name = "file_id")
  private Long fileId;

  /** 사이트 id — page_data.site_id */
  @Column(name = "site_id")
  private Long siteId;
}

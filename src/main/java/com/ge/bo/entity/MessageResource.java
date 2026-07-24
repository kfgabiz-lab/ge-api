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
 * 다국어 리소스 엔티티
 * - key: 번역 키 (영문·숫자·점만 허용, 유니크)
 * - ko: 한국어 텍스트 (필수)
 * - en: 영어 텍스트 (선택)
 * - active: 사용여부 (기본 true)
 */
@Entity
@Table(name = "message_resource")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MessageResource {

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 번역 키 — 유니크, 등록 후 변경 불가 */
  @Column(name = "`key`", nullable = false, unique = true, length = 255, updatable = false)
    private String key;

    /* 한국어 텍스트 */
  @Column(nullable = false, length = 500)
    private String ko;

    /* 영어 텍스트 (미입력 허용) */
  @Column(length = 500)
    private String en;

    /* 사용여부 — 기본 true */
  @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /* 유형 — WORD(단어) / SENTENCE(문장), 기본 WORD */
  @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, columnDefinition = "varchar(10) default 'WORD'")
    private MessageResourceType resourceType = MessageResourceType.WORD;

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

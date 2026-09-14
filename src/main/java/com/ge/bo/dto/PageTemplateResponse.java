package com.ge.bo.dto;

import com.ge.bo.entity.PageTemplate;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 페이지 템플릿 응답 DTO
 */
@Getter
@Builder
public class PageTemplateResponse {

  private Long id;
  private String name;
  private String slug;
  private String description;
  private String templateType;
  private String configJson;
  private boolean collapsible;

    /** 생성된 TSX 파일 절대경로 */
  private String filePath;

    /** 실제 접근 가능한 Next.js URL */
  private String pageUrl;

  private String createdBy;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

    /**
     * 엔티티 → 응답 DTO 변환 (정적 팩토리)
     */
  public static PageTemplateResponse from(PageTemplate entity) {
    return PageTemplateResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .description(entity.getDescription())
                .templateType(entity.getTemplateType())
                .configJson(entity.getConfigJson())
                .collapsible(entity.getCollapsible())
                .filePath(entity.getFilePath())
                .pageUrl("/admin/widgetSub/" + entity.getSlug())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
  }
}

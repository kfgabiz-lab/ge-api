package com.ge.bo.dto;

import com.ge.bo.entity.SlugRegistry;
import java.time.LocalDateTime;

public record SlugRegistryResponse(
    Long id,
    String slug,
    String name,
    String type,
    String description,
    Boolean active,
    Long entityId,
    String entitySlug,
    String entityName,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt
) {
  public static SlugRegistryResponse from(SlugRegistry e) {
    Long entityId     = e.getSlugEntity() != null ? e.getSlugEntity().getId()   : null;
    String entitySlug = e.getSlugEntity() != null ? e.getSlugEntity().getSlug() : null;
    String entityName = e.getSlugEntity() != null ? e.getSlugEntity().getName() : null;
    return new SlugRegistryResponse(
            e.getId(), e.getSlug(), e.getName(), e.getType(),
            e.getDescription(), e.getActive(),
            entityId, entitySlug, entityName,
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt()
        );
  }
}

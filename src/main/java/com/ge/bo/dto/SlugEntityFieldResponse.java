package com.ge.bo.dto;

import com.ge.bo.entity.SlugEntityField;
import java.time.OffsetDateTime;

/**
 * Slug Entity 필드 응답 DTO
 */
public record SlugEntityFieldResponse(
    Long id,
    String key,
    String label,
    String columnType,
    Integer columnLength,
    Long connectedEntityId,
    String fieldType,
    String codeGroupCode,
    String defaultValue,
    Boolean isNullable,
    String description,
    Integer sortOrder,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt
) {
    public static SlugEntityFieldResponse from(SlugEntityField f) {
        return new SlugEntityFieldResponse(
            f.getId(),
            f.getKey(),
            f.getLabel(),
            f.getColumnType(),
            f.getColumnLength(),
            f.getConnectedEntity() != null ? f.getConnectedEntity().getId() : null,
            f.getFieldType(),
            f.getCodeGroupCode(),
            f.getDefaultValue(),
            f.getIsNullable(),
            f.getDescription(),
            f.getSortOrder(),
            f.getCreatedBy(),
            f.getCreatedAt(),
            f.getUpdatedBy(),
            f.getUpdatedAt()
        );
    }
}

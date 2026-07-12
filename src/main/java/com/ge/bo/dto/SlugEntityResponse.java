package com.ge.bo.dto;

import com.ge.bo.entity.SlugEntity;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Slug Entity 응답 DTO
 * - 목록 조회 시: fields 빈 배열
 * - 단건/활성 목록 조회 시: fields 포함
 */
public record SlugEntityResponse(
    Long id,
    String slug,
    String name,
    String tableName,
    String description,
    Boolean active,
    Integer fieldCount,
    List<SlugEntityFieldResponse> fields,
    /** 마스터(부모) Entity ID — 없으면 독립 Entity */
    Long parentEntityId,
    /** 마스터(부모) Entity slug — 화면 표시용 */
    String parentEntitySlug,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt
) {
    /** 목록 조회용 — fields 빈 배열 */
    public static SlugEntityResponse fromList(SlugEntity e) {
        return new SlugEntityResponse(
            e.getId(), e.getSlug(), e.getName(), e.getTableName(),
            e.getDescription(), e.getActive(),
            e.getFields().size(), List.of(),
            e.getParentEntity() != null ? e.getParentEntity().getId() : null,
            e.getParentEntity() != null ? e.getParentEntity().getSlug() : null,
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt()
        );
    }

    /** 단건/활성 목록 — fields 포함 */
    public static SlugEntityResponse from(SlugEntity e) {
        List<SlugEntityFieldResponse> fieldList = e.getFields().stream()
            .map(SlugEntityFieldResponse::from)
            .toList();
        return new SlugEntityResponse(
            e.getId(), e.getSlug(), e.getName(), e.getTableName(),
            e.getDescription(), e.getActive(),
            fieldList.size(), fieldList,
            e.getParentEntity() != null ? e.getParentEntity().getId() : null,
            e.getParentEntity() != null ? e.getParentEntity().getSlug() : null,
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt()
        );
    }
}

package com.ge.bo.dto;

import com.ge.bo.entity.SlugRelation;
import java.time.OffsetDateTime;

public record SlugRelationResponse(
        Long id,
        String masterSlug,
        String slaveSlug,
        String masterKey,
        String slaveKey,
        String joinType,
        String slaveFilter,
        String relationDir,
        String fetchFields,
        String fetchSeparator,
        String description,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
    public static SlugRelationResponse from(SlugRelation e) {
        return new SlugRelationResponse(
                e.getId(),
                e.getMasterSlug(),
                e.getSlaveSlug(),
                e.getMasterKey(),
                e.getSlaveKey(),
                e.getJoinType(),
                e.getSlaveFilter(),
                e.getRelationDir(),
                e.getFetchFields(),
                e.getFetchSeparator(),
                e.getDescription(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt()
        );
    }
}

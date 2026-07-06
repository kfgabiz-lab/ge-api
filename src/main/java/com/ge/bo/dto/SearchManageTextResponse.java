package com.ge.bo.dto;

import com.ge.bo.entity.SearchManageText;

import java.time.OffsetDateTime;

/**
 * 검색관리 하위 검색텍스트 응답 DTO
 */
public record SearchManageTextResponse(
    Long id,
    String text,
    String createdBy,
    OffsetDateTime createdAt
) {
    public static SearchManageTextResponse from(SearchManageText t) {
        return new SearchManageTextResponse(
            t.getId(), t.getText(), t.getCreatedBy(), t.getCreatedAt()
        );
    }
}

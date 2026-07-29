package com.ge.bo.dto;

import com.ge.bo.entity.SearchManageText;

import java.time.OffsetDateTime;

/**
 * 검색관리 하위 검색텍스트 응답 DTO
 *
 * <p>title 은 nullable — 컬럼 추가 이전에 등록된 데이터는 NULL 로 내려간다.</p>
 */
public record SearchManageTextResponse(
    Long id,
    String title,
    String text,
    String createdBy,
    OffsetDateTime createdAt
) {
    public static SearchManageTextResponse from(SearchManageText t) {
        return new SearchManageTextResponse(
            t.getId(), t.getTitle(), t.getText(), t.getCreatedBy(), t.getCreatedAt()
        );
    }
}

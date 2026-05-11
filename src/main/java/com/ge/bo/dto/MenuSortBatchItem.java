package com.ge.bo.dto;

/**
 * 드래그 정렬 배치 변경 요청 DTO
 */
public record MenuSortBatchItem(
    Long id,
    Integer sortOrder,
    Long parentId
) {}

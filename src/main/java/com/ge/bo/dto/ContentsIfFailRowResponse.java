package com.ge.bo.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 콘텐츠 통합배치 격리 행(contents_if_fail_row) 조회 응답 DTO — 배치 실행 화면에서 어떤 원천 행이
 * 왜 걸렸는지 원본 데이터까지 확인할 수 있도록 그대로 내려준다.
 */
public record ContentsIfFailRowResponse(Long id, Long batchId, String sourceSystem, String sourceTable,
                                         String sourceDocKey, String sourceRowKey, String failStep, String failCode,
                                         String failDetail, JsonNode rawData, String status, String resolvedNote,
                                         OffsetDateTime resolvedAt, OffsetDateTime createdAt) {
}

package com.ge.bo.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 콘텐츠 통합배치 실행 이력(contents_batch_log) 조회 응답 DTO
 */
public record ContentsBatchLogResponse(Long batchId, String sourceSystem, String status, String currentStep,
                                        JsonNode rowCounts, JsonNode report, String errorMessage,
                                        OffsetDateTime startedAt, OffsetDateTime finishedAt) {
}

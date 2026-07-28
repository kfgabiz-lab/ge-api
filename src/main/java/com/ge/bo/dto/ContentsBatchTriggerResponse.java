package com.ge.bo.dto;

/**
 * 콘텐츠 통합배치 수동 실행 응답 DTO
 */
public record ContentsBatchTriggerResponse(Long batchId, String message) {

    public static ContentsBatchTriggerResponse of(Long batchId) {
        return new ContentsBatchTriggerResponse(batchId, "배치가 완료되었습니다. batch_id=" + batchId);
    }
}

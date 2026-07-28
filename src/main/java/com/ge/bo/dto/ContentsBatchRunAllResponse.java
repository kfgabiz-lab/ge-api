package com.ge.bo.dto;

/**
 * 콘텐츠 통합배치(카탈로그→SSQ→인증서) 수동 트리거(run-all) 응답 DTO.
 * 배치는 비동기로 시작되므로 이 응답은 완료 여부가 아니라 배치로그 3건의 batchId만 담는다 —
 * 실제 진행 상황/결과는 GET /contents-batch/{batchId}를 폴링해 확인한다. batchId가 null이면 해당
 * 소스는 이미 실행 중인 배치가 있어 이번 트리거에서 건너뛴 것이다.
 */
public record ContentsBatchRunAllResponse(Long catalogBatchId, Long ssqBatchId, Long certiBatchId) {
}

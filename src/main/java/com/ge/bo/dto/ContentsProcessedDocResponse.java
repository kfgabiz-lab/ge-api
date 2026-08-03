package com.ge.bo.dto;

/**
 * 배치 1회 실행에서 실제로 적재된 문서 응답 DTO — 격리/실패 행 목록과 짝을 이뤄, "무엇이 성공했는지"도
 * 어드민 화면에서 바로 확인할 수 있게 한다.
 */
public record ContentsProcessedDocResponse(Long id, String sourceDocKey, String docType, String docTitle,
                                            Boolean expose, Boolean isDeleted, Long categoryCount, Long versionCount,
                                            Long fileCount) {
}

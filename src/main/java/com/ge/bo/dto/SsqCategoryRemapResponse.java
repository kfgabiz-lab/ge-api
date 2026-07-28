package com.ge.bo.dto;

/**
 * SSQ 미매핑 카테고리 재처리(remap) 결과 응답 DTO
 */
public record SsqCategoryRemapResponse(int remappedCount, String message) {

    public static SsqCategoryRemapResponse of(int remappedCount) {
        return new SsqCategoryRemapResponse(remappedCount, remappedCount + "건의 카테고리가 새로 매핑되었습니다.");
    }
}

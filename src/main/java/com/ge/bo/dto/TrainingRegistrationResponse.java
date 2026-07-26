package com.ge.bo.dto;

/**
 * FO 트레이닝 세션 등록 저장 결과 응답
 * ContactUsInquiryResponse 와 동일 구조({success, id, message}).
 */
public record TrainingRegistrationResponse(boolean success, Long id, String message) {

    /** 저장 성공 응답 생성 */
    public static TrainingRegistrationResponse success(Long id) {
        return new TrainingRegistrationResponse(true, id, "등록이 정상적으로 접수되었습니다.");
    }
}

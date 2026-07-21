package com.ge.bo.dto;

/**
 * FO Contact Us 문의 접수 저장 결과 응답
 */
public record ContactUsInquiryResponse(boolean success, Long id, String message) {

    /** 저장 성공 응답 생성 */
    public static ContactUsInquiryResponse success(Long id) {
        return new ContactUsInquiryResponse(true, id, "문의가 정상적으로 접수되었습니다.");
    }
}

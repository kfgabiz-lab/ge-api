package com.ge.bo.dto;

/**
 * Contact Us(문의 접수) 결과 응답
 * status: CTP 응답 Status 코드 그대로 전달 (S=성공, X=예외처리, D=중복, F=실패, E=오류)
 */
public record ContactUsResponse(boolean success, String status, String message) {

    public static ContactUsResponse of(String ctpStatus, String message) {
        boolean success = "S".equals(ctpStatus) || "X".equals(ctpStatus);
        return new ContactUsResponse(success, ctpStatus, message);
    }
}

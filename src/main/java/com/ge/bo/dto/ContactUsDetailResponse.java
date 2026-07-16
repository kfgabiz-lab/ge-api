package com.ge.bo.dto;

/** 문의 결과 조회 응답 (프론트 전달용) */
public record ContactUsDetailResponse(
        String type,
        String productCategory,
        String status,
        String description,
        String reply,
        String inquiryDate,
        String replyDate) {

    public static ContactUsDetailResponse from(CtpContactUsDetailResult result) {
        CtpContactUsDetailResult.Data data = result.data();
        return new ContactUsDetailResponse(
                data.inquiryType(),
                data.productCategory(),
                data.inquiryStatus(),
                data.inquiryDetails(),
                data.responseDetails(),
                data.inquiryDate(),
                data.responseDateTime());
    }
}

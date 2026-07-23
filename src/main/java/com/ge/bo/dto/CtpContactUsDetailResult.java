package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connect Portal(CTP) 문의 결과 조회 응답 — IF_SRR_NAHP_CTP_0002 Output(Case) 필드 매핑
 * 문의 상세 필드는 data 객체 하위에 내려오고, Status/ReturnMessage/ReturnCode 는 최상위(CTP 호출 성공 여부)에 위치한다.
 * 실제 응답 확인 결과(2026-07-23) data 하위 필드는 PascalCase로 내려온다(최상위는 이미 PascalCase로 일치).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CtpContactUsDetailResult(
        @JsonProperty("Status") String status,
        @JsonProperty("ReturnMessage") String returnMessage,
        @JsonProperty("ReturnCode") String returnCode,
        @JsonProperty("data") Data data) {

    /** inquiryStatus: Submitted / In-Progress / Closed */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("InquiryType") String inquiryType,
            @JsonProperty("ProductCategory") String productCategory,
            @JsonProperty("InquiryStatus") String inquiryStatus,
            @JsonProperty("InquiryDetails") String inquiryDetails,
            @JsonProperty("ResponseDetails") String responseDetails,
            @JsonProperty("InquiryDate") String inquiryDate,
            @JsonProperty("ResponseDateTime") String responseDateTime) {
    }
}

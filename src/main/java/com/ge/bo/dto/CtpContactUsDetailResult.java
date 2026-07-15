package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connect Portal(CTP) 문의 결과 조회 응답 — IF_SRR_NAHP_CTP_0002 Output(Case) 필드 매핑
 * 실제 Postman 테스트(SearchNAHPCase) 확인 결과, 문의 상세 필드는 data 객체 하위에 내려오고
 * Status/ReturnMessage/ReturnCode 는 최상위(CTP 호출 성공 여부)에 위치한다
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
            @JsonProperty("inquiryType") String inquiryType,
            @JsonProperty("productCategory") String productCategory,
            @JsonProperty("inquiryStatus") String inquiryStatus,
            @JsonProperty("inquiryDetails") String inquiryDetails,
            @JsonProperty("responseDetails") String responseDetails,
            @JsonProperty("inquiryDate") String inquiryDate,
            @JsonProperty("responseDateTime") String responseDateTime) {
    }
}

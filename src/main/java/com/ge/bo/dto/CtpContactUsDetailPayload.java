package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connect Portal(CTP) 문의 결과 조회 요청 페이로드 — IF_SRR_NAHP_CTP_0002 Target(S2 Case) 필드 매핑
 * 실제 Postman 테스트(SearchNAHPCase) 확인 결과 필드명은 소문자(inquiryNumber/password)
 */
public record CtpContactUsDetailPayload(
        @JsonProperty("inquiryNumber") String caseNumber,
        @JsonProperty("password") String password) {
}

package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connect Portal(CTP) 응답 — IF_SRR_NAHP_CTP_0001 Response(R1) 필드 매핑
 * Status: E=오류 / F=실패 / S=성공 / D=중복 / X=예외처리
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CtpContactUsResult(
        @JsonProperty("Status") String status,
        @JsonProperty("code") String returnCode,
        @JsonProperty("ReturnMessage") String returnMessage) {
}
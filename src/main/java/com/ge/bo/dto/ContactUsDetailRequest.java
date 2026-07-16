package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 문의 결과 조회 요청 (프론트 "문의 확인" 폼)
 * IF_SRR_NAHP_CTP_0002 — CaseNumber + Password(평문, 서버에서 암호화 후 CTP 전송)
 */
public record ContactUsDetailRequest(

        @NotBlank(message = "접수번호를 입력해주세요.") String caseNumber,

        @NotBlank(message = "비밀번호를 입력해주세요.") String password) {
}

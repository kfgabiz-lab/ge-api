package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * FO Contact Us 문의 접수 요청 (POST /api/v1/fo/contact-us)
 * ※ 기존 CTP 전송용 ContactUsRequest와는 별개 도메인
 * type/country는 여기서 정적 검증(@Pattern/@Size min)을 하지 않고,
 *   ContactUsInquiryService에서 공통코드(code_detail) 실시간 존재·활성 검증으로 전환한다.
 */
public record ContactUsInquiryRequest(

        @NotBlank(message = "문의 유형을 선택해주세요.")
        @Size(max = 30)
        String type,

        /** 제품 카테고리 — "카테고리1 | 카테고리2 | 카테고리3" 형태로 결합된 문자열 */
        @Size(max = 1000) String productCategory,

        /** Lv3(제품)의 product-data PK — CTP ProductInformationInquiryType(담당자 이메일) 조회용 */
        Long productId,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255) String email,

        @NotBlank(message = "이름을 입력해주세요.") @Size(max = 255) String firstName,

        @NotBlank(message = "성을 입력해주세요.") @Size(max = 255) String lastName,

        @NotBlank(message = "업체명을 입력해주세요.") @Size(max = 100) String companyName,

        @NotBlank(message = "국가를 선택해주세요.")
        @Size(max = 2) String country,

        @NotBlank(message = "문의 내용을 입력해주세요.") String description,

        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 100) String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.") String confirmPassword,

        @NotNull(message = "마케팅 활용동의 여부를 선택해주세요.") Boolean marketingOptInFlag,

        @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.") Boolean privacyConsentFlag) {
}

package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * Contact Us(문의 접수) 요청 (프론트 Contact Us 폼)
 * Product Category 는 Inquiry Type 이 "Others" 가 아닌 경우에만 필수 — ContactUsService 에서 교차 검증
 */
public record ContactUsRequest(

        @NotBlank(message = "문의 유형을 선택해주세요.")
        @Pattern(regexp = "^(ProductInformation|QuotationRequest|Purchase|Others)$",
                message = "올바른 문의 유형이 아닙니다.")
        String type,

        String productCategoryLv1,

        String productCategoryLv2,

        String productCategoryLv3,

        /** Lv1 카테고리 코드 (예: "L06") — 문의 예외 처리 라우팅 판정용 */
        String productCategoryLv1Id,

        /** Lv2 카테고리 코드 (예: "L06-01") — 문의 예외 처리 라우팅 판정용 */
        String productCategoryLv2Id,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255) String email,

        @NotBlank(message = "이름을 입력해주세요.") @Size(max = 255) String firstName,

        @NotBlank(message = "성을 입력해주세요.") @Size(max = 255) String lastName,

        @NotBlank(message = "업체명을 입력해주세요.") @Size(max = 100) String companyName,

        @NotBlank(message = "국가를 선택해주세요.")
        @Size(min = 2, max = 2, message = "국가코드는 ISO 3166-1 ALPHA-2 형식(2자리)이어야 합니다.")
        String country,

        @NotBlank(message = "문의 내용을 입력해주세요.") String description,

        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 100) String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.") String confirmPassword,

        @NotNull(message = "마케팅 활용동의 여부를 선택해주세요.") Boolean marketingOptInFlag,

        @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.") Boolean privacyConsentFlag) {
}
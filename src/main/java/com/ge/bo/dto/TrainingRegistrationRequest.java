package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * FO 트레이닝 세션 등록 요청 (POST /api/v1/fo/training/registrations)
 * ContactUsInquiryRequest 패턴을 본떠 작성.
 * - Student Name / Job Title / Company Name 은 영문 전용 정규식 제약 없이 글자수(최대 100자)만 검증한다
 *   (실제 회사명에 숫자/기호가 포함될 수 있어 사용자 확정으로 영문 전용 제약 제거).
 * - typeOfBusiness 는 선택 필드 — 값이 있을 때만 서비스에서 공통코드(BUSINESSTYPE) 실시간 검증.
 */
public record TrainingRegistrationRequest(

        @NotNull(message = "커리큘럼 정보가 필요합니다.")
        Long curriculumId,

        @NotNull(message = "세션 정보가 필요합니다.")
        Long sessionId,

        @NotBlank(message = "수강자 이름을 입력해주세요.") @Size(max = 100)
        String studentName,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255) String email,

        @NotBlank(message = "직함을 입력해주세요.") @Size(max = 100)
        String jobTitle,

        @NotBlank(message = "전화번호를 입력해주세요.") @Size(max = 20)
        String phone,

        @NotBlank(message = "회사명을 입력해주세요.") @Size(max = 100)
        String companyName,

        /** 참석 세션 시작일("yyyy-MM-dd") — 서버에서 LocalDate 파싱 */
        @NotBlank(message = "참석 일자가 필요합니다.")
        String eventDate,

        @Size(max = 255) String streetAddress,

        @Size(max = 255) String address2,

        @Size(max = 100) String apartment,

        @Size(max = 100) String city,

        @Size(max = 100) String stateProvince,

        @Size(max = 20) String zipCode,

        /** 비즈니스 유형 코드값(공통코드 BUSINESSTYPE, 선택) */
        @Size(max = 30) String typeOfBusiness,

        @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.") Boolean privacyConsentFlag,

        @NotBlank(message = "캡차 인증을 완료해주세요.") String captchaCode,

        /** 자체 캡차 발급 시 받은 암호화 토큰 (FE가 그대로 되돌려줌, stateless 검증용) */
        @NotBlank(message = "캡차 인증을 완료해주세요.") String captchaToken) {
}

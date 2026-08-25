package com.ge.bo.dto;

import com.ge.bo.common.validation.MaxByteLength;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * FO Training Request(비정기 교육 신청) 제출 요청 (POST /api/v1/fo/training/requests)
 * TrainingRegistrationRequest 패턴을 본떠 작성 — FE Step1~4 입력값을 한 번에 받는다.
 * - Step3 의 교육장소/현장담당자 항목은 In-Person 일 때만 입력되는 값이라 서버 필수 검증 대상이 아니다
 *   (필수 여부는 FE Step3 에서 trainingFormat 에 따라 조건부 검증).
 * - selectedProducts(객체 배열)는 서비스에서 JSON 문자열로 직렬화해 JSONB 컬럼에 저장하고,
 *   jobTitles/studentInvolvement/vfdUnderstandingTopics(단순 문자열 배열)는 그대로 text[] 컬럼에 저장한다.
 */
public record TrainingRequestSubmitRequest(

        // ===== Step1: Basic Information =====

        @Size(max = 50) String trainingTrack,

        @NotBlank(message = "이름을 입력해주세요.") @Size(max = 200)
        String firstName,

        @Size(max = 200) String lastName,

        @NotBlank(message = "회사명을 입력해주세요.") @Size(max = 200)
        String company,

        @NotBlank(message = "주소를 입력해주세요.") @Size(max = 255)
        String streetAddress,

        @Size(max = 255) String address2,

        @NotBlank(message = "도시를 입력해주세요.") @Size(max = 100)
        String city,

        @NotBlank(message = "주(State)를 입력해주세요.") @Size(max = 100)
        String state,

        @NotBlank(message = "우편번호를 입력해주세요.") @Size(max = 20)
        String zip,

        @NotBlank(message = "전화번호를 입력해주세요.") @Size(max = 20)
        String phone,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255) String email,

        @Size(max = 200) String title,

        @Size(max = 20) String cellPhone,

        @Size(max = 20) String salesContact,

        // ===== Step2: Requested Date(s) of Training =====

        @NotBlank(message = "요청 교육 횟수를 입력해주세요.") @Size(max = 200)
        String sessionCount,

        @NotBlank(message = "교육 기간을 입력해주세요.") @Size(max = 20)
        String sessionDays,

        /** 희망 교육 시작일("yyyy-MM-dd") — 서버에서 LocalDate 파싱 */
        @NotBlank(message = "교육 시작일을 선택해주세요.")
        String scheduleStart,

        /** 희망 교육 종료일("yyyy-MM-dd") — 서버에서 LocalDate 파싱 */
        @NotBlank(message = "교육 종료일을 선택해주세요.")
        String scheduleEnd,

        @NotBlank(message = "참가 인원을 입력해주세요.") @Size(max = 20)
        String studentCount,

        // ===== Step3: Training Class Location =====

        @NotBlank(message = "교육 형태를 선택해주세요.") @Size(max = 20)
        String trainingFormat,

        @Size(max = 200) String locationName,

        @Size(max = 255) String locationStreetAddress,

        @Size(max = 255) String locationAddress2,

        @Size(max = 100) String locationCity,

        @Size(max = 100) String locationState,

        @Size(max = 20) String locationZip,

        @Size(max = 50) String contactPerson,

        @Size(max = 400) String contactDetails,

        // ===== Step4: Training Class Details =====

        /** 선택 제품 목록 — 최소 1건 필수 */
        @NotEmpty(message = "교육받을 제품을 선택해주세요.")
        List<SelectedProduct> selectedProducts,

        List<String> jobTitles,

        List<String> studentInvolvement,

        @Size(max = 10) String vfdUnderstanding,

        List<String> vfdUnderstandingTopics,

        /** 기타 요청사항 — dc 규정상 최대 2000byte (문자 수가 아니라 UTF-8 byte 기준) */
        @MaxByteLength(value = 2000, message = "요청사항은 최대 2000byte까지 입력할 수 있습니다.")
        String comments,

        @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.") Boolean consentChecked,

        /** 자체 캡차 인증번호 — 검증에만 사용하고 저장하지 않는다 */
        @NotBlank(message = "캡차 인증을 완료해주세요.") String captchaCode,

        /** 자체 캡차 발급 시 받은 암호화 토큰 (FE가 그대로 되돌려줌, stateless 검증용) — 검증에만 사용하고 저장하지 않는다 */
        @NotBlank(message = "캡차 인증을 완료해주세요.") String captchaToken) {

    /**
     * Step4 제품 선택 항목 — FE RequestForTrainingSelectedProduct 구조와 1:1 대응
     * (GET /api/v1/fo/training/product-tree 응답에서 고른 제품)
     */
    public record SelectedProduct(
            Long id,
            String name,
            /** 트리 구분 — P(Power) / A(Automation) */
            String type,
            /** 어느 그룹(2번째 드롭다운) 아래에서 고른 제품인지 */
            Long groupId,
            String groupTitle) {
    }
}

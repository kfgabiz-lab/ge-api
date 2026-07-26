package com.ge.bo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Training 신청 내역(관리자 조회) 상세 응답 DTO — flat record
 * TrainingRegistrationListResponse 전체 필드 + training_registration 자체 컬럼 전부(설계서 9절 참고)
 */
public record TrainingRegistrationDetailResponse(
        Long id,
        Long curriculumId,
        Long sessionId,
        String trainingScheduleType,
        String trainingType,
        String trainingCourse,
        String curriculumTitle,
        String title,
        LocalDate trainingDateFrom,
        LocalDate trainingDateTo,
        OffsetDateTime createdAt,
        String email,
        String applicant,
        String jobTitle,
        String phone,
        String companyName,
        LocalDate eventDate,
        String streetAddress,
        String address2,
        String apartment,
        String city,
        String stateProvince,
        String zipCode,
        String typeOfBusiness,
        Boolean privacyConsentFlag,
        String createdIp) {
}

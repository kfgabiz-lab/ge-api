package com.ge.bo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Training 신청 내역(관리자 조회) 목록 응답 DTO — flat record
 * training_registration(자체 컬럼) + curriculum(currMgmt-data)/session(currDtlMgmt-data) 2단계 조인 표시값
 * (설계서 8절 참고, docs/pages/training-registration/be_training-registration.md)
 * ※ FO 제출 응답 DTO(com.ge.bo.dto.TrainingRegistrationResponse, success/id/message 구조)와는 이름 충돌 방지를 위해
 *   TrainingRegistrationListResponse로 명명(설계서 협의 반영).
 */
public record TrainingRegistrationListResponse(
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
        String applicant) {
}

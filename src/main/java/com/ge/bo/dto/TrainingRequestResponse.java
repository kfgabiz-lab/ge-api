package com.ge.bo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Training Request(비정기 교육 신청, 관리자 조회) 목록 응답 DTO
 * - training_request 네이티브 쿼리 결과로 직접 생성한다 (TrainingRegistrationListResponse 와 동일 패턴).
 */
public record TrainingRequestResponse(
        Long id,
        String trainingTrack,
        String firstName,
        String lastName,
        String company,
        String email,
        String phone,
        String trainingFormat,
        LocalDate scheduleStart,
        LocalDate scheduleEnd,
        String studentCount,
        OffsetDateTime createdAt) {
}

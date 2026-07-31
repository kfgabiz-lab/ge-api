package com.ge.bo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Training Request(비정기 교육 신청, 관리자 조회) 목록 응답 DTO
 * - curriculumTitle/sessionTitle 은 training_request 자체 컬럼이 아니라 curriculum_id/session_id 로
 *   page_data(currMgmt-data / currDtlMgmt-data)를 LEFT JOIN 해서 얻는 값이라 entity 변환(from) 없이
 *   TrainingRequestAdminService 의 네이티브 쿼리 결과로 직접 생성한다
 *   (TrainingRegistrationListResponse 와 동일 패턴).
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
        String curriculumTitle,
        String sessionTitle,
        LocalDate scheduleStart,
        LocalDate scheduleEnd,
        String studentCount,
        OffsetDateTime createdAt) {
}

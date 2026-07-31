package com.ge.bo.dto;

import com.ge.bo.entity.TrainingRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Training Request(비정기 교육 신청, 관리자 조회) 상세 응답 DTO
 * - selectedProductNames: entity의 jsonb(selectedProducts) 배열에서 name만 추출한 값
 *   (jsonb 원본 파싱은 TrainingRequestAdminService에서 처리 후 전달, DTO는 파싱 의존성 없음)
 * - jobTitles/studentInvolvement/vfdUnderstandingTopics: entity의 text[] 그대로 전달, 콤마 join 표시는 FE 담당
 * - curriculumTitle/sessionTitle: entity의 curriculumId/sessionId로 page_data를 조회해 얻은 표시용 제목
 *   (조회/파싱은 TrainingRequestAdminService에서 처리 후 전달, 매칭 데이터 없으면 null)
 */
public record TrainingRequestDetailResponse(
        Long id,
        String trainingTrack,
        String curriculumTitle,
        String sessionTitle,
        String firstName,
        String lastName,
        String company,
        String streetAddress,
        String address2,
        String city,
        String state,
        String zip,
        String phone,
        String email,
        String title,
        String cellPhone,
        String salesContact,
        String sessionCount,
        String sessionDays,
        LocalDate scheduleStart,
        LocalDate scheduleEnd,
        String studentCount,
        String trainingFormat,
        String locationName,
        String locationStreetAddress,
        String locationAddress2,
        String locationCity,
        String locationState,
        String locationZip,
        String contactPerson,
        String contactDetails,
        List<String> selectedProductNames,
        List<String> jobTitles,
        List<String> studentInvolvement,
        String vfdUnderstanding,
        List<String> vfdUnderstandingTopics,
        String comments,
        Boolean consentChecked,
        String createdIp,
        OffsetDateTime createdAt) {

    public static TrainingRequestDetailResponse from(TrainingRequest e, List<String> selectedProductNames,
                                                      String curriculumTitle, String sessionTitle) {
        return new TrainingRequestDetailResponse(
                e.getId(),
                e.getTrainingTrack(),
                curriculumTitle,
                sessionTitle,
                e.getFirstName(),
                e.getLastName(),
                e.getCompany(),
                e.getStreetAddress(),
                e.getAddress2(),
                e.getCity(),
                e.getState(),
                e.getZip(),
                e.getPhone(),
                e.getEmail(),
                e.getTitle(),
                e.getCellPhone(),
                e.getSalesContact(),
                e.getSessionCount(),
                e.getSessionDays(),
                e.getScheduleStart(),
                e.getScheduleEnd(),
                e.getStudentCount(),
                e.getTrainingFormat(),
                e.getLocationName(),
                e.getLocationStreetAddress(),
                e.getLocationAddress2(),
                e.getLocationCity(),
                e.getLocationState(),
                e.getLocationZip(),
                e.getContactPerson(),
                e.getContactDetails(),
                selectedProductNames,
                e.getJobTitles(),
                e.getStudentInvolvement(),
                e.getVfdUnderstanding(),
                e.getVfdUnderstandingTopics(),
                e.getComments(),
                e.getConsentChecked(),
                e.getCreatedIp(),
                e.getCreatedAt());
    }
}

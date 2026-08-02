package com.ge.bo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TrainingApplicationResponse(
        String rowKey,
        Long id,
        String scheduleType,
        String trainingCourse,
        String trainingType,
        String curriculumTitle,
        String sessionTitle,
        LocalDate dateFrom,
        LocalDate dateTo,
        OffsetDateTime createdAt,
        String email,
        String applicant) {
}

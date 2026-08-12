package com.ge.bo.dto;

public record TrainingApplicationSummaryResponse(
        CourseCounts regular,
        CourseCounts irregular) {

    public record CourseCounts(
            long total,
            long engineering,
            long service,
            long sales) {
    }
}

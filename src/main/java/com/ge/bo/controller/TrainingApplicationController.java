package com.ge.bo.controller;

import com.ge.bo.dto.TrainingApplicationResponse;
import com.ge.bo.service.TrainingApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/training-applications")
@RequiredArgsConstructor
public class TrainingApplicationController {

    private final TrainingApplicationService trainingApplicationService;

    @GetMapping
    public ResponseEntity<Page<TrainingApplicationResponse>> getList(
            @RequestParam(required = false) String trainingScheduleType,
            @RequestParam(required = false) String trainingCourse,
            @RequestParam(required = false) String trainingType,
            @RequestParam(required = false) String curriculumTitle,
            @RequestParam(required = false) String sessionTitle,
            @RequestParam(required = false) String searchPeriodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                trainingApplicationService.getList(trainingScheduleType, trainingCourse, trainingType,
                        curriculumTitle, sessionTitle, searchPeriodType, startDate, endDate, pageable));
    }
}

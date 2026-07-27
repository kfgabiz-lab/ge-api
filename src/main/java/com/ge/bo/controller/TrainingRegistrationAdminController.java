package com.ge.bo.controller;

import com.ge.bo.dto.TrainingRegistrationDetailResponse;
import com.ge.bo.dto.TrainingRegistrationPageResponse;
import com.ge.bo.service.TrainingRegistrationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Training 신청 내역(관리자 조회) REST API
 * - 대상 화면: bo `/admin/widget/trainingApplHis-list` (빌더 PAGE_TEMPLATE)
 * - 조회 전용 (training_registration은 이력성 append-only 테이블, 수정/삭제 없음)
 * - 인증된 사용자면 role 무관 접근 가능 (SecurityConfig의 anyRequest().authenticated()에 의존, LoginLogController와 동일)
 * - FO 제출 API(TrainingRegistrationController, POST /api/v1/fo/training/registrations)와는 별개
 *   (이름 충돌 방지를 위해 컨트롤러/서비스에 Admin 접미 부여, 설계서 10절 참고)
 */
@RestController
@RequestMapping("/api/v1/training-registrations")
@RequiredArgsConstructor
public class TrainingRegistrationAdminController {

    private final TrainingRegistrationAdminService trainingRegistrationAdminService;

    /**
     * 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param createdFrom          신청일시 dateRange 시작
     * @param createdTo            신청일시 dateRange 종료
     * @param trainingDateFrom     시작일 dateRange 시작
     * @param trainingDateTo       시작일 dateRange 종료
     * @param trainingDateToFrom   종료일 dateRange 시작
     * @param trainingDateToTo     종료일 dateRange 종료
     * @param trainingScheduleType 구분 — "01" 또는 미지정이면 전체, 그 외 값이면 0건 응답
     * @param trainingType         교육방식 코드(CSV 부분일치)
     * @param trainingCourse       Training 코드(정확일치)
     * @param curriculum           커리큘럼 제목 키워드(부분일치)
     * @param title                제목 키워드(부분일치)
     * @param page                 페이지 번호(0-based, 기본 0)
     * @param size                 페이지 크기(기본 20)
     */
    @GetMapping
    public ResponseEntity<TrainingRegistrationPageResponse> getList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDateToFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDateToTo,
            @RequestParam(required = false) String trainingScheduleType,
            @RequestParam(required = false) String trainingType,
            @RequestParam(required = false) String trainingCourse,
            @RequestParam(required = false) String curriculum,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(trainingRegistrationAdminService.getList(
                createdFrom, createdTo, trainingDateFrom, trainingDateTo,
                trainingDateToFrom, trainingDateToTo, trainingScheduleType,
                trainingType, trainingCourse, curriculum, title, page, size));
    }

    /**
     * 단건 상세 조회 — 자체 컬럼 전체 + curriculum/session 조인 표시값
     */
    @GetMapping("/{id}")
    public ResponseEntity<TrainingRegistrationDetailResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(trainingRegistrationAdminService.getOne(id));
    }
}

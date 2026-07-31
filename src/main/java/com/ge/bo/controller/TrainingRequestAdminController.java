package com.ge.bo.controller;

import com.ge.bo.dto.TrainingRequestDetailResponse;
import com.ge.bo.dto.TrainingRequestResponse;
import com.ge.bo.service.TrainingRequestAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Training Request(비정기 교육 신청, 관리자 조회) REST API
 * - 대상 화면: bo `/admin/manage/training-request` (페이지 빌더 미사용, logs/access와 동일하게 코드로 직접 구성)
 * - 조회 전용 (training_request는 이력성 append-only 테이블, 수정/삭제 없음)
 * - 인증된 사용자면 role 무관 접근 가능 (SecurityConfig의 anyRequest().authenticated()에 의존, LoginLogController와 동일)
 * - FO 제출 API(FoTrainingController, POST /api/v1/fo/training/requests)와는 별개
 */
@RestController
@RequestMapping("/api/v1/training-requests")
@RequiredArgsConstructor
public class TrainingRequestAdminController {

    private final TrainingRequestAdminService trainingRequestAdminService;

    /**
     * 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param trainingScheduleType 분류 필터 코드(TRAININGSCHEDULETYPE: 01=정기/02=비정기, 01이면 항상 0건)
     * @param trainingTrack    Training 필터(training_track 원본값: engineering/sales)
     * @param trainingFormat   교육 형태 필터 코드(TRAININGTYPE: 001/002)
     * @param curriculumTitle  Course명 필터(curriculum_id로 조인한 커리큘럼 제목 부분일치)
     * @param sessionTitle     제목 필터(session_id로 조인한 세션 제목 부분일치)
     * @param searchPeriodType 검색 기간 구분(SEARCHPERIODTYPE: 01=접수일시/02=희망시작일/03=희망종료일)
     * @param startDate        기간 시작 필터
     * @param endDate          기간 종료 필터
     * @param pageable         페이지 정보 (기본: createdAt DESC, 20건)
     */
    @GetMapping
    public ResponseEntity<Page<TrainingRequestResponse>> getList(
            @RequestParam(required = false) String trainingScheduleType,
            @RequestParam(required = false) String trainingTrack,
            @RequestParam(required = false) String trainingFormat,
            @RequestParam(required = false) String curriculumTitle,
            @RequestParam(required = false) String sessionTitle,
            @RequestParam(required = false) String searchPeriodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                trainingRequestAdminService.getList(trainingScheduleType, trainingTrack, trainingFormat,
                        curriculumTitle, sessionTitle, searchPeriodType, startDate, endDate, pageable));
    }

    /**
     * 단건 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<TrainingRequestDetailResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(trainingRequestAdminService.getOne(id));
    }

    /**
     * 전체 데이터 CSV 다운로드 — 동적 필터 + 공통코드/날짜포맷 변환은 공통 서비스에서 처리(TestDataController와 동일 패턴)
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String headers,
            @RequestParam(required = false) String keys,
            @RequestParam(required = false) String dateFormats,
            @RequestParam(required = false) String codeMaps,
            @RequestParam(required = false) String reason,
            @RequestParam Map<String, String> allParams,
            HttpServletRequest request) {
        return trainingRequestAdminService.exportCsv(allParams, headers, keys, dateFormats, codeMaps, reason, request);
    }
}

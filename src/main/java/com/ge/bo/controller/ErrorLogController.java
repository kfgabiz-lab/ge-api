package com.ge.bo.controller;

import com.ge.bo.dto.ErrorLogDetailResponse;
import com.ge.bo.dto.ErrorLogResponse;
import com.ge.bo.service.ErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 오류로그 조회 REST API
 * - 조회 전용 (이력성 테이블이므로 수정/삭제 없음)
 * - 인증된 사용자면 role 무관 접근 가능 (SecurityConfig의 anyRequest().authenticated()에 의존)
 */
@RestController
@RequestMapping("/api/v1/error-logs")
@RequiredArgsConstructor
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    /**
     * 오류로그 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param httpStatus 상태코드 필터 (예: 500)
     * @param startDate  시작일시 필터
     * @param endDate    종료일시 필터
     * @param errorCode  에러코드 키워드
     * @param loginUser  사용자 키워드
     * @param pageable   페이지 정보 (기본: createdAt DESC, 20건)
     */
    @GetMapping
    public ResponseEntity<Page<ErrorLogResponse>> getList(
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) String loginUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                errorLogService.getList(httpStatus, startDate, endDate, errorCode, loginUser, pageable));
    }

    /**
     * 오류로그 단건 상세 조회 — stackTrace 포함
     */
    @GetMapping("/{id}")
    public ResponseEntity<ErrorLogDetailResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(errorLogService.getOne(id));
    }
}

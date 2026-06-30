package com.ge.bo.controller;

import com.ge.bo.dto.LoginLogDetailResponse;
import com.ge.bo.dto.LoginLogResponse;
import com.ge.bo.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 접속이력 조회 REST API
 * - 조회 전용 (이력성 테이블이므로 수정/삭제 없음)
 */
@RestController
@RequestMapping("/api/v1/login-logs")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
public class LoginLogController {

    private final LoginLogService loginLogService;

    /**
     * 접속이력 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param status     로그인 결과 필터 (SUCCESS / FAIL)
     * @param loginEmail 이메일 키워드 필터
     * @param startDate  시작일시 필터
     * @param endDate    종료일시 필터
     * @param pageable   페이지 정보 (기본: createdAt DESC, 20건)
     */
    @GetMapping
    public ResponseEntity<Page<LoginLogResponse>> getList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                loginLogService.getList(status, loginEmail, startDate, endDate, pageable));
    }

    /**
     * 접속이력 단건 상세 조회 — userAgent 포함
     */
    @GetMapping("/{id}")
    public ResponseEntity<LoginLogDetailResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(loginLogService.getOne(id));
    }
}

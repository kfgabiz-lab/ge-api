package com.ge.bo.controller;

import com.ge.bo.dto.TransactionLogDetailResponse;
import com.ge.bo.dto.TransactionLogResponse;
import com.ge.bo.service.TransactionLogService;
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
 * 트랜잭션 로그 조회 REST API
 * - 조회 전용 (이력성 테이블이므로 수정/삭제 없음)
 */
@RestController
@RequestMapping("/api/v1/transaction-logs")
@RequiredArgsConstructor
public class TransactionLogController {

    private final TransactionLogService transactionLogService;

    /**
     * 트랜잭션 로그 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param httpStatus 상태코드 필터 (예: 200)
     * @param startDate  시작일시 필터
     * @param endDate    종료일시 필터
     * @param actionType 변경유형 키워드 (CREATE / UPDATE / DELETE)
     * @param loginUser  사용자 키워드
     * @param siteId     사이트 ID (X-Site-Id 헤더, 없으면 전체)
     * @param pageable   페이지 정보 (기본: createdAt DESC, 20건)
     */
    @GetMapping
    public ResponseEntity<Page<TransactionLogResponse>> getList(
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String loginUser,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                transactionLogService.getList(httpStatus, startDate, endDate, actionType, loginUser, siteId, pageable));
    }

    /**
     * 트랜잭션 로그 단건 상세 조회 — requestBody 포함
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionLogDetailResponse> getOne(
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(transactionLogService.getOne(id, siteId));
    }
}

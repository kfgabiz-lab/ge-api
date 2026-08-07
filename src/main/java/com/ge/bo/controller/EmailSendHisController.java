package com.ge.bo.controller;

import com.ge.bo.dto.EmailSendHisDetailResponse;
import com.ge.bo.dto.EmailSendHisResponse;
import com.ge.bo.service.EmailSendHisService;
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
import java.util.Map;

/**
 * 이메일 발송 이력 조회/재발송 REST API
 * - 조회 + 재발송 (이력성 테이블이므로 수정/삭제 없음)
 * - 시스템관리자(role.is_system=true)만 접근 가능 — error/login/transaction 로그와 동일 정책
 */
@RestController
@RequestMapping("/api/v1/email-send-his")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
public class EmailSendHisController {

    private final EmailSendHisService emailSendHisService;

    /**
     * 이메일 발송 이력 목록 조회 (동적 필터 + 서버 페이징)
     *
     * @param sendStatus          발송상태(공통코드 SENDSTATUS, S/F)
     * @param emailSendType       분류(공통코드 EMAILSENDTYPE)
     * @param emailSendDetailType 상세분류(공통코드 TRAININGCOURSE)
     * @param recipientEmail      수신 이메일 키워드
     * @param startDate           시작일시 필터(created_at 기준)
     * @param endDate             종료일시 필터
     * @param pageable            페이지 정보 (기본: created_at DESC, 20건)
     */
    @GetMapping
    public ResponseEntity<Page<EmailSendHisResponse>> getList(
            @RequestParam(required = false) String sendStatus,
            @RequestParam(required = false) String emailSendType,
            @RequestParam(required = false) String emailSendDetailType,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                emailSendHisService.getList(sendStatus, emailSendType, emailSendDetailType, recipientEmail,
                        startDate, endDate, pageable));
    }

    /**
     * 이메일 발송 이력 단건 상세 조회 — subject/content 포함
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailSendHisDetailResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(emailSendHisService.getOne(id));
    }

    /**
     * 재발송 — 저장된 subject/content/recipientEmail 그대로 다시 발송(원본 이력행의 발송상태/최근재발송일시를 갱신, 새 행 생성 없음)
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<Map<String, String>> resend(@PathVariable Long id) {
        String sendStatus = emailSendHisService.resend(id);
        return ResponseEntity.ok(Map.of("sendStatus", sendStatus));
    }
}

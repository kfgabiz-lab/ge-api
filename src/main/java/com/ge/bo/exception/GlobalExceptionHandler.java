package com.ge.bo.exception;

import com.ge.bo.service.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러 - 모든 API 에러를 일관된 형식으로 응답
 * 예외 발생 시 응답 반환 후 오류로그를 비동기로 DB에 저장한다
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

    /**
     * JSON 파싱 실패 등 HttpMessageNotReadableException 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.error("JSON 파싱 에러 (잘못된 요청 데이터): ", ex);

        String message = "요청 데이터 형식이 올바르지 않습니다.";

        Map<String, Object> body = new HashMap<>();
        body.put("status", 400);
        body.put("error", "MALFORMED_JSON");
        body.put("message", message);
        body.put("detail", ex.getMessage());
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, 400, "MALFORMED_JSON", message, null);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 입력값 유효성 검증 실패 (예: @NotBlank, @Size 위반)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String message = "입력값 유효성 검증에 실패했습니다.";

        Map<String, Object> body = new HashMap<>();
        body.put("status", 400);
        body.put("error", "VALIDATION_FAILED");
        body.put("message", message);
        body.put("fieldErrors", fieldErrors);
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, 400, "VALIDATION_FAILED", message, null);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 커스텀 비즈니스 예외
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), null);

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * 권한 없음 (403 Forbidden)
     */
    @ExceptionHandler({ org.springframework.security.access.AccessDeniedException.class,
            org.springframework.security.authorization.AuthorizationDeniedException.class })
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            Exception ex,
            HttpServletRequest request) {
        log.warn("권한 거부됨: {}", ex.getMessage());

        String message = "이 리소스에 접근할 권한이 없습니다.";

        Map<String, Object> body = new HashMap<>();
        body.put("status", 403);
        body.put("error", "FORBIDDEN");
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, 403, "FORBIDDEN", message, null);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * DB 제약조건 위반 (UNIQUE 등)
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.error("데이터 무결성 위반: {}", ex.getMessage());

        String message = "데이터 무결성 제약조건 위반이 발생했습니다.";

        Map<String, Object> body = new HashMap<>();
        body.put("status", 409);
        body.put("error", "DATA_INTEGRITY");
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, 409, "DATA_INTEGRITY", message, null);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * 그 외 예상치 못한 서버 에러
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            Exception ex,
            HttpServletRequest request) {
        log.error("서버 내부 오류 발생: ", ex);

        String message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

        Map<String, Object> body = new HashMap<>();
        body.put("status", 500);
        body.put("error", "INTERNAL_SERVER_ERROR");
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        // 오류로그 비동기 저장 (500 — 스택트레이스 포함)
        errorLogService.saveAsync(request, 500, "INTERNAL_SERVER_ERROR", message, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

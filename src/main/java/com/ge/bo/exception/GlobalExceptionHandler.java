package com.ge.bo.exception;

import com.ge.bo.service.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
   * request 스레드에서 현재 로그인 사용자 이메일 추출
   * @Async 별도 스레드에서는 SecurityContext가 없으므로 호출 전에 미리 추출해서 파라미터로 전달해야 함
   */
  private String extractLoginUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    return auth.getName();
  }

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
    errorLogService.saveAsync(request, 400, "MALFORMED_JSON", message, null, extractLoginUser());

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
    errorLogService.saveAsync(request, 400, "VALIDATION_FAILED", message, null, extractLoginUser());

    return ResponseEntity.badRequest().body(body);
  }

  /**
   * 메서드 파라미터 유효성 검증 실패 (예: @Pattern 위반 — record/파라미터 레벨 검증)
   */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<Map<String, Object>> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpServletRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getAllValidationResults().forEach(result -> {
      String field = result.getMethodParameter().getParameterName();
      String msg = result.getResolvableErrors().isEmpty()
          ? "유효하지 않은 값입니다."
          : result.getResolvableErrors().get(0).getDefaultMessage();
      fieldErrors.put(field != null ? field : "parameter", msg);
    });

    String message = "입력값 유효성 검증에 실패했습니다.";

    Map<String, Object> body = new HashMap<>();
    body.put("status", 400);
    body.put("error", "VALIDATION_FAILED");
    body.put("message", message);
    body.put("fieldErrors", fieldErrors);
    body.put("timestamp", LocalDateTime.now().toString());

    // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
    errorLogService.saveAsync(request, 400, "VALIDATION_FAILED", message, null, extractLoginUser());

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
    errorLogService.saveAsync(
        request, ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), null, extractLoginUser());

    return ResponseEntity.status(ex.getStatus()).body(body);
  }

  /**
   * 권한 없음 (403 Forbidden)
   */
  @ExceptionHandler({
    org.springframework.security.access.AccessDeniedException.class,
    org.springframework.security.authorization.AuthorizationDeniedException.class
  })
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
    errorLogService.saveAsync(request, 403, "FORBIDDEN", message, null, extractLoginUser());

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
    errorLogService.saveAsync(request, 409, "DATA_INTEGRITY", message, null, extractLoginUser());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  /**
   * 경로/쿼리 파라미터 타입 불일치 (예: @PathVariable Long id 에 숫자가 아닌 값 전달)
   * MethodArgumentTypeMismatchException은 ErrorResponse를 구현하지 않으므로 별도 처리 필요
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException ex,
      HttpServletRequest request) {
    log.warn("파라미터 타입 불일치: {}", ex.getMessage());

    String message = String.format("'%s' 파라미터 값이 올바르지 않습니다.", ex.getName());

    Map<String, Object> body = new HashMap<>();
    body.put("status", 400);
    body.put("error", "INVALID_PARAMETER_TYPE");
    body.put("message", message);
    body.put("timestamp", LocalDateTime.now().toString());

    // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
    errorLogService.saveAsync(request, 400, "INVALID_PARAMETER_TYPE", message, null, extractLoginUser());

    return ResponseEntity.badRequest().body(body);
  }

  /**
   * 그 외 예상치 못한 서버 에러
   * Spring MVC 표준 예외 중 ErrorResponse를 구현하는 것들
   * (HttpRequestMethodNotSupportedException→405, MissingServletRequestParameterException→400,
   *  NoResourceFoundException→404 등)은 프레임워크가 이미 올바른 HTTP 상태 코드를 담고 있으므로
   * 이를 무시하고 무조건 500으로 응답하면 안 된다 — ErrorResponse 여부를 먼저 확인해 그대로 응답한다.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneralException(
      Exception ex,
      HttpServletRequest request) {

    if (ex instanceof ErrorResponse errorResponse) {
      HttpStatusCode statusCode = errorResponse.getStatusCode();
      ProblemDetail problemDetail = errorResponse.getBody();
      // 원문 상세 메시지 — 로그에만 사용, 클라이언트 응답에는 노출하지 않음
      String originalDetail = (problemDetail != null && problemDetail.getDetail() != null)
          ? problemDetail.getDetail() : ex.getMessage();
      String errorCode = (statusCode instanceof HttpStatus httpStatus)
          ? httpStatus.name() : "HTTP_" + statusCode.value();
      // 클라이언트 응답용 한글 안내 메시지 (원문 영문 메시지 대신 상태코드별 매핑)
      String message = resolveStandardExceptionMessage(statusCode);

      Map<String, Object> body = new HashMap<>();
      body.put("status", statusCode.value());
      body.put("error", errorCode);
      body.put("message", message);
      body.put("timestamp", LocalDateTime.now().toString());

      if (statusCode.value() >= 500) {
        log.error("표준 웹 예외 발생 ({}): {}", statusCode.value(), originalDetail, ex);
        // 오류로그 비동기 저장 (5xx — 스택트레이스 포함)
        errorLogService.saveAsync(request, statusCode.value(), errorCode, message, ex, extractLoginUser());
      } else {
        log.warn("표준 웹 예외 발생 ({}): {}", statusCode.value(), originalDetail);
        // 오류로그 비동기 저장 (4xx — 스택트레이스 저장 안 함)
        errorLogService.saveAsync(request, statusCode.value(), errorCode, message, null, extractLoginUser());
      }

      return ResponseEntity.status(statusCode).body(body);
    }

    log.error("서버 내부 오류 발생: ", ex);

    String message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

    Map<String, Object> body = new HashMap<>();
    body.put("status", 500);
    body.put("error", "INTERNAL_SERVER_ERROR");
    body.put("message", message);
    body.put("timestamp", LocalDateTime.now().toString());

    // 오류로그 비동기 저장 (500 — 스택트레이스 포함)
    errorLogService.saveAsync(request, 500, "INTERNAL_SERVER_ERROR", message, ex, extractLoginUser());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  /**
   * Spring MVC 표준 웹 예외(ErrorResponse 구현체)의 상태코드를 한글 안내 메시지로 매핑
   * 원문(영문) 메시지는 클라이언트에 노출하지 않고 로그에만 남긴다
   */
  private String resolveStandardExceptionMessage(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 400 -> "잘못된 요청입니다.";
      case 401 -> "인증이 필요합니다.";
      case 403 -> "이 리소스에 접근할 권한이 없습니다.";
      case 404 -> "요청한 리소스를 찾을 수 없습니다.";
      case 405 -> "지원하지 않는 요청 방식입니다.";
      case 406 -> "지원하지 않는 응답 형식입니다.";
      case 415 -> "지원하지 않는 요청 데이터 형식입니다.";
      default -> statusCode.is5xxServerError()
          ? "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
          : "잘못된 요청입니다.";
    };
  }
}

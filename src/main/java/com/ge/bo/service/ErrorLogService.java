package com.ge.bo.service;

import com.ge.bo.common.util.ClientIpUtils;
import com.ge.bo.dto.ErrorLogDetailResponse;
import com.ge.bo.dto.ErrorLogResponse;
import com.ge.bo.entity.ErrorLog;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.ErrorLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 오류로그 서비스
 * - @Async: 메인 응답과 분리하여 비동기로 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

  private final ErrorLogRepository errorLogRepository;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     *
     * @param httpStatus 상태코드 (null이면 전체)
     * @param startDate  시작일시 (null이면 전체)
     * @param endDate    종료일시 (null이면 전체)
     * @param errorCode  에러코드 키워드 (null이면 전체)
     * @param loginUser  사용자 키워드 (null이면 전체)
     * @param pageable   페이지 정보
     */
    @Transactional(readOnly = true)
    public Page<ErrorLogResponse> getList(Integer httpStatus, OffsetDateTime startDate,
                                          OffsetDateTime endDate, String errorCode,
                                          String loginUser, Pageable pageable) {
        Specification<ErrorLog> spec = buildSpec(httpStatus, startDate, endDate, errorCode, loginUser);
        return errorLogRepository.findAll(spec, pageable).map(ErrorLogResponse::from);
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 오류로그 단건 상세 조회 — stackTrace 포함
     */
    @Transactional(readOnly = true)
    public ErrorLogDetailResponse getOne(Long id) {
        ErrorLog errorLog = errorLogRepository.findById(id)
                .orElseThrow(ErrorCode.ERROR_LOG_NOT_FOUND::toException);
        return ErrorLogDetailResponse.from(errorLog);
    }

    /* ══════════ 동적 필터 ══════════ */

    private Specification<ErrorLog> buildSpec(Integer httpStatus, OffsetDateTime startDate,
                                              OffsetDateTime endDate, String errorCode,
                                              String loginUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (httpStatus != null) {
                predicates.add(cb.equal(root.get("httpStatus"), httpStatus));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (errorCode != null && !errorCode.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("errorCode")),
                        "%" + errorCode.trim().toLowerCase() + "%"));
            }
            if (loginUser != null && !loginUser.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("loginUser")),
                        "%" + loginUser.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /* ══════════ 비동기 저장 ══════════ */

    /**
     * 오류로그 비동기 저장
     *
     * 사용법:
     *   errorLogService.saveAsync(request, 500, "INTERNAL_SERVER_ERROR", "서버 오류", exception);
     *
     * @param request    HttpServletRequest (URL, 메서드, IP 추출용)
     * @param httpStatus HTTP 상태코드
     * @param errorCode  에러코드 문자열
     * @param message    에러 메시지
     * @param ex         예외 객체 (500일 때만 스택트레이스 저장, 나머지는 null 전달)
     * @param siteId     요청 사이트 ID (요청 스레드에서 SiteContext.getSiteId()로 미리 추출 —
     *                   @Async 스레드에는 ThreadLocal이 전파되지 않으므로 반드시 파라미터로 전달)
     */
  @Async
    public void saveAsync(HttpServletRequest request, int httpStatus,
                          String errorCode, String message, Exception ex, String loginUser,
                          Long siteId) {
    try {
            // 변수로 분리하여 IDE null 안전성 경고 해소
      ErrorLog errorLog = ErrorLog.builder()
                    .httpStatus(httpStatus)
                    .errorCode(errorCode)
                    .method(request != null ? request.getMethod() : null)
                    .requestUrl(request != null ? getFullUrl(request) : null)
                    .message(message)
                    .stackTrace(httpStatus >= 500 ? getStackTrace(ex) : null) // 500 이상만 스택트레이스 저장
                    .clientIp(request != null ? ClientIpUtils.resolve(request) : null)
                    .loginUser(loginUser) // request 스레드에서 미리 추출한 이메일 사용 (@Async 스레드 SecurityContext 미전파 방지)
                    .siteId(siteId) // 위와 동일 — request 스레드에서 미리 추출한 SiteContext 값
                    .build();
      errorLogRepository.save(errorLog);
    } catch (Exception e) {
            // 로그 저장 실패가 메인 기능에 영향을 주지 않도록 예외를 삼킴
      log.warn("오류로그 저장 실패: {}", e.getMessage());
    }
  }

    /** 요청 전체 URL 조합 (쿼리스트링 포함) */
  private String getFullUrl(HttpServletRequest request) {
    String url = request.getRequestURI();
    String query = request.getQueryString();
        // URL이 500자를 초과할 경우 잘라냄
    String fullUrl = (query != null) ? url + "?" + query : url;
    return fullUrl.length() > 500 ? fullUrl.substring(0, 500) : fullUrl;
  }

    /** 예외 스택트레이스를 문자열로 변환 */
  private String getStackTrace(Exception ex) {
    if (ex == null) {
      return null;
    }
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}

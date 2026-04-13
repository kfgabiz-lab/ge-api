package com.ge.bo.service;

import com.ge.bo.entity.ErrorLog;
import com.ge.bo.repository.ErrorLogRepository;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 오류로그 서비스
 * - @Async: 메인 응답과 분리하여 비동기로 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

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
     */
    @Async
    public void saveAsync(HttpServletRequest request, int httpStatus,
                          String errorCode, String message, Exception ex) {
        try {
            // 변수로 분리하여 IDE null 안전성 경고 해소
            ErrorLog errorLog = ErrorLog.builder()
                    .httpStatus(httpStatus)
                    .errorCode(errorCode)
                    .method(request != null ? request.getMethod() : null)
                    .requestUrl(request != null ? getFullUrl(request) : null)
                    .message(message)
                    .stackTrace(httpStatus >= 500 ? getStackTrace(ex) : null) // 500 이상만 스택트레이스 저장
                    .clientIp(request != null ? getClientIp(request) : null)
                    .loginUser(getCurrentUser())
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

    /**
     * 실제 클라이언트 IP 추출
     * - Render 등 리버스 프록시 환경에서는 X-Forwarded-For 헤더에 실제 IP가 담김
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            // 여러 IP가 콤마로 연결된 경우 첫 번째가 실제 클라이언트 IP
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 현재 로그인 사용자 이메일 (비로그인 시 null) */
    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    /** 예외 스택트레이스를 문자열로 변환 */
    private String getStackTrace(Exception ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

package com.ge.bo.filter;

import com.ge.bo.security.JwtTokenProvider;
import com.ge.bo.service.TransactionLogService;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 트랜잭션 로그 필터 — POST / PUT / PATCH / DELETE 요청을 가로채 로그를 저장
 * ContentCachingRequestWrapper: 요청 바디는 1회만 읽을 수 있어 래핑 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLogFilter extends OncePerRequestFilter {

  private final TransactionLogService transactionLogService;
  private final JwtTokenProvider jwtTokenProvider;

  /** 로그 저장 대상 HTTP 메서드 */
  private static final Set<String> TARGET_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** 로그 저장 제외 URL 접두사 (로그인·인증 관련 민감 요청) */
  private static final String EXCLUDE_AUTH_PREFIX = "/api/v1/auth/";

  @Value("${ls.redis-enabled:false}")
  private boolean redisEnabled;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String method = request.getMethod().toUpperCase();
    String requestUri = request.getRequestURI();

    // 대상 메서드가 아니거나 제외 URL이면 로그 저장 없이 통과
    if (!TARGET_METHODS.contains(method) || requestUri.startsWith(EXCLUDE_AUTH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 요청 바디 재읽기를 위한 래핑
    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    long startTime = System.currentTimeMillis();

    String loginUser = null;
    if(redisEnabled){
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (authentication != null && authentication.isAuthenticated()) {
        loginUser = authentication.getName();
      }
    }else {
      // JWT 토큰에서 직접 추출 — SecurityContextHolder 순서 문제를 우회
      loginUser = extractLoginUserFromToken(request);
    }

    try {
      filterChain.doFilter(wrappedRequest, response);
    } finally {
      long durationMs = System.currentTimeMillis() - startTime;
      String requestBody = extractRequestBody(wrappedRequest);
      String requestUrl = buildFullUrl(request);
      String clientIp = extractClientIp(request);

      transactionLogService.saveAsync(
          method, requestUrl, requestBody,
          response.getStatus(), clientIp, durationMs, loginUser);
    }
  }

  /** 요청 바디 추출 (filterChain 통과 후 캐시에서 읽음) */
  private String extractRequestBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length == 0) {
      return null;
    }
    String body = new String(content, StandardCharsets.UTF_8);
    return StringUtils.isBlank(body) ? null : body;
  }

  /** 전체 URL 조합 (쿼리스트링 포함, 500자 초과 시 잘라냄) */
  private String buildFullUrl(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String query = request.getQueryString();
    String fullUrl = (query != null) ? uri + "?" + query : uri;
    return fullUrl.length() > 500 ? fullUrl.substring(0, 500) : fullUrl;
  }

  /**
   * Authorization 헤더의 Bearer 토큰에서 이메일 직접 추출
   * SecurityContextHolder 대신 JWT를 직접 파싱 — 필터 실행 순서와 무관하게 동작
   */
  private String extractLoginUserFromToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.isBlank(bearerToken) || !bearerToken.startsWith("Bearer ")) {
      return null;
    }
    String token = bearerToken.substring(7);
    try {
      if (jwtTokenProvider.validateToken(token)) {
        return jwtTokenProvider.getEmailFromToken(token);
      }
    } catch (Exception e) {
      // 토큰 파싱 실패 시 null 반환 (로그 저장은 계속)
    }
    return null;
  }

  /**
   * 실제 클라이언트 IP 추출
   * 리버스 프록시 환경에서는 X-Forwarded-For 헤더에 실제 IP가 담김
   */
  private String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (StringUtils.isNotBlank(forwarded)) {
      // 여러 IP가 콤마로 연결된 경우 첫 번째가 실제 클라이언트 IP
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

package com.ge.bo.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 엔드포인트(POST /api/v1/auth/login) IP 단위 Rate Limiting 필터
 * 계정별 실패 잠금이 막지 못하는 계정 스프레이(다수 계정 소량 시도)를 출처 단위로 차단한다.
 */
@Slf4j
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final String LOGIN_METHOD = "POST";
  private static final long MAX_ATTEMPTS_PER_MINUTE = 100L;

  private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!LOGIN_METHOD.equalsIgnoreCase(request.getMethod())
        || !LOGIN_PATH.equals(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientIp = request.getRemoteAddr();
    Bucket bucket = bucketsByIp.computeIfAbsent(clientIp, key -> createBucket());

    if (!bucket.tryConsume(1)) {
      log.warn("로그인 요청 Rate Limit 초과로 차단 — ip={}, limit={}/분", clientIp, MAX_ATTEMPTS_PER_MINUTE);
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType("application/json;charset=UTF-8");
      String body = "{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\","
          + "\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\","
          + "\"timestamp\":\"" + LocalDateTime.now() + "\"}";
      response.getWriter().write(body);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Bucket createBucket() {
    Bandwidth limit = Bandwidth.builder()
        .capacity(MAX_ATTEMPTS_PER_MINUTE)
        .refillGreedy(MAX_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1))
        .build();
    return Bucket.builder().addLimit(limit).build();
  }
}

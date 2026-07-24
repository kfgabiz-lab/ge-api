package com.ge.bo.filter;

import com.ge.bo.common.context.SiteContext;
import com.ge.bo.common.context.SiteTimeZoneResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 헤더 X-Site-Id로 사이트 timezone을 조회해 SiteContext(ThreadLocal)에 채워주는 공통 필터
 * - siteId 없음 / 사이트 조회 실패 / timezone 미설정 / 파싱 실패 시 아무것도 set 하지 않는다(기존 동작과 동일하게 폴백은 소비하는 쪽 책임)
 * - TransactionLogFilter와 동일하게 @Component로 등록되는 일반 서블릿 필터 — Spring Security 필터체인과 무관하게
 *   인증 여부와 상관없이 항상 동작한다 (permitAll 엔드포인트 포함)
 * - 스레드풀 재사용 시 값이 다른 요청으로 새는 것을 막기 위해 반드시 finally에서 SiteContext.clear() 호출
 */
@Component
@RequiredArgsConstructor
public class SiteContextFilter extends OncePerRequestFilter {

  private final SiteTimeZoneResolver siteTimeZoneResolver;

  private static final String HEADER_SITE_ID = "X-Site-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    try {
      Long siteId = parseSiteId(request.getHeader(HEADER_SITE_ID));
      if (siteId != null) {
        siteTimeZoneResolver.lookup(siteId).ifPresent(SiteContext::set);
      }
      filterChain.doFilter(request, response);
    } finally {
      SiteContext.clear();
    }
  }

  /** 헤더값 파싱 실패(숫자 아님)/없음 시 null — 예외를 던지지 않고 SiteContext 미설정으로 조용히 처리 */
  private Long parseSiteId(String header) {
    if (!StringUtils.hasText(header)) {
      return null;
    }
    try {
      return Long.parseLong(header.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

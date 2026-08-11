package com.ge.bo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 사이트 권한 + 메뉴 권한 통합 인가 인터셉터
 * - 미인증 요청(Authentication null 또는 AnonymousAuthenticationToken)은 그대로 통과시킨다.
 * - 부트스트랩 API(사이트/메뉴 목록 자체를 내려주는 API)는 사전에 사이트/메뉴 권한을 알 수 없으므로 GET 전용 경로로 예외 처리한다.
 * - SecurityConfig가 permitAll로 선언한 경로는 로그인 여부와 무관하게 항상 허용하기로 한 정책이므로,
 *   로그인된 상태에서 호출돼도 사이트체크 대상에서 제외해야 한다(SecurityConfig가 인증 자체는 요구하지 않지만
 *   로그인된 사용자도 해당 API를 호출할 수 있으므로 별도 예외가 필요하다).
 *   메서드 무관 permitAll은 EXEMPT_PATH_PATTERNS, GET만 permitAll인 경로는 PUBLIC_GET_PATTERNS로 반영한다.
 * - 인증된 요청은 AccessAuthorizationService로 위임해 사이트/메뉴 권한을 검증한다.
 */
@Component
@RequiredArgsConstructor
public class AccessValidationInterceptor implements HandlerInterceptor {

  private final AccessAuthorizationService accessAuthorizationService;

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 부트스트랩 API — 사이드바/사이트 선택 UI가 최초 렌더링 시 호출하는 조회 전용 API */
  private static final List<String> BOOTSTRAP_GET_PATTERNS = List.of(
            "/api/v1/admins/*/sites",
            "/api/v1/menus"
    );

    /** SecurityConfig가 GET만 permitAll로 선언한 경로 — 로그인 여부와 무관하게 항상 허용 */
  private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/api/v1/message-resources"
    );

    /** SecurityConfig가 메서드 무관 permitAll로 선언한 경로 — 로그인 여부와 무관하게 항상 허용 */
  private static final List<String> EXEMPT_PATH_PATTERNS = List.of(
            "/api/v1/auth/**",
            "/api/v1/health",
            "/api/v1/public/**",
            "/api/v1/fo/**",
            "/api/v1/contents-batch/run-all",
            "/api/v1/contents-batch/catalog/run",
            "/api/v1/contents-batch/ssq/run",
            "/api/v1/contents-batch/certi/run"
    );

  @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return true;
    }

    if (isBootstrapRequest(request.getMethod(), request.getRequestURI())) {
      return true;
    }

    if (isPublicGetRequest(request.getMethod(), request.getRequestURI())) {
      return true;
    }

    if (isExemptRequest(request.getRequestURI())) {
      return true;
    }

    accessAuthorizationService.validateAccess(request);
    return true;
  }

  private boolean isBootstrapRequest(String method, String path) {
    return matchesGetPattern(method, path, BOOTSTRAP_GET_PATTERNS);
  }

  private boolean isPublicGetRequest(String method, String path) {
    return matchesGetPattern(method, path, PUBLIC_GET_PATTERNS);
  }

  private boolean matchesGetPattern(String method, String path, List<String> patterns) {
    if (!"GET".equalsIgnoreCase(method)) {
      return false;
    }
    return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }

  private boolean isExemptRequest(String path) {
    return EXEMPT_PATH_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }
}

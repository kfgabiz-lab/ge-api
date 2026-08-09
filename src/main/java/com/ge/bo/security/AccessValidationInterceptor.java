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
 *   (permitAll 대상 여부는 SecurityConfig가 이미 결정했으므로, 경로 목록을 이 클래스에 별도로
 *   중복 유지하지 않고 인증 여부만으로 판단해 SecurityConfig와의 목록 드리프트를 방지한다)
 * - 부트스트랩 API(사이트/메뉴 목록 자체를 내려주는 API)는 사전에 사이트/메뉴 권한을 알 수 없으므로 경로로 예외 처리한다.
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

  @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return true;
    }

    if (isBootstrapRequest(request.getMethod(), request.getRequestURI())) {
      return true;
    }

    accessAuthorizationService.validateAccess(request);
    return true;
  }

  private boolean isBootstrapRequest(String method, String path) {
    if (!"GET".equalsIgnoreCase(method)) {
      return false;
    }
    return BOOTSTRAP_GET_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }
}

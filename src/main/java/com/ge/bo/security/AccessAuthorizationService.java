package com.ge.bo.security;

import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.AdminUserSiteId;
import com.ge.bo.entity.Menu;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.AdminUserSiteRepository;
import com.ge.bo.repository.MenuRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 사이트/메뉴 권한 인가 서비스
 * SYSTEM_ADMIN은 전체 통과, 그 외에는 X-Site-Id 헤더 기준으로 사이트 접근 권한을,
 * API_TO_MENU_URL에 등록된 경로에 한해 role_menu 매핑 기준으로 메뉴 접근 권한을 검증한다.
 * AccessValidationInterceptor에서 인증된 요청에 대해 호출된다.
 */
@Service
@RequiredArgsConstructor
public class AccessAuthorizationService {

  private static final String HEADER_SITE_ID = "X-Site-Id";

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  /** API 경로 → 메뉴 화면 경로(menu.url) 매핑. 여기 등록된 경로만 메뉴 검사 대상이다. */
  private static final Map<String, String> API_TO_MENU_URL = Map.ofEntries(
      Map.entry("/api/v1/admins/**", "/admin/settings/users"),
      Map.entry("/api/v1/roles/**", "/admin/settings/roles"),
      Map.entry("/api/v1/menus/**", "/admin/settings/menus"),
      Map.entry("/api/v1/codes/**", "/admin/settings/codes"),
      Map.entry("/api/v1/sites/**", "/admin/settings/sites")
  );

  private final AdminRepository adminRepository;
  private final AdminUserSiteRepository adminUserSiteRepository;
  private final MenuRepository menuRepository;
  private final SecurityService securityService;

    /**
     * 사이트/메뉴 권한을 검증한다.
     * SYSTEM_ADMIN(role.is_system=true)은 검증 없이 즉시 통과한다.
     */
  @Transactional(readOnly = true)
    public void validateAccess(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (securityService.isSystemAdmin(authentication)) {
      return;
    }

    String employeeId = authentication.getName();
    AdminUser admin = adminRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보를 확인할 수 없습니다."));

    validateSiteAccess(request, admin);
    validateMenuAccess(request, authentication);
  }

  /** API_TO_MENU_URL에 등록된 경로에 한해 role_menu 매핑 기준으로 메뉴 접근 권한을 검증한다. SUPER_ADMIN은 통과한다. */
  private void validateMenuAccess(HttpServletRequest request, Authentication authentication) {
    if (securityService.isSuperAdmin(authentication)) {
      return;
    }

    String menuUrl = matchMenuUrl(request.getRequestURI());
    if (menuUrl == null) {
      return;
    }

    List<Menu> candidates = menuRepository.findByUrl(menuUrl);
    boolean hasAccess = candidates.stream()
                .anyMatch(menu -> securityService.hasMenu(authentication, menu.getId()));

    if (!hasAccess) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "MENU_ACCESS_DENIED", "해당 화면에 대한 접근 권한이 없습니다.");
    }
  }

  private String matchMenuUrl(String requestUri) {
    return API_TO_MENU_URL.entrySet().stream()
                .filter(entry -> PATH_MATCHER.match(entry.getKey(), requestUri))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
  }

    /** X-Site-Id 헤더 기반 사이트 접근 권한 검증 — 헤더 필수, admin_user_site 매핑 존재 여부로 판단 */
  private void validateSiteAccess(HttpServletRequest request, AdminUser admin) {
    Long siteId = parseSiteId(request.getHeader(HEADER_SITE_ID));
    if (siteId == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SITE_ID_REQUIRED", "X-Site-Id 헤더가 필요합니다.");
    }

    boolean hasAccess = adminUserSiteRepository.existsById(new AdminUserSiteId(admin.getId(), siteId));
    if (!hasAccess) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "SITE_ACCESS_DENIED", "해당 홈페이지에 대한 접근 권한이 없습니다.");
    }
  }

    /** 헤더값 파싱 실패(숫자 아님)/없음 시 null */
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

package com.ge.bo.security;

import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.AdminUserSiteId;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.AdminUserSiteRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/**
 * 사이트/메뉴 권한 인가 서비스
 * SYSTEM_ADMIN은 전체 통과, 그 외에는 X-Site-Id 헤더 기준으로 사이트 접근 권한을,
 * menu_api에 등록된 API에 한해 role_menu 기준으로 메뉴 접근 권한을 검증한다.
 * AccessValidationInterceptor에서 인증된 요청에 대해 호출된다.
 */
@Service
@RequiredArgsConstructor
public class AccessAuthorizationService {

  private static final String HEADER_SITE_ID = "X-Site-Id";

  private final AdminRepository adminRepository;
  private final AdminUserSiteRepository adminUserSiteRepository;
  private final SecurityService securityService;
  private final MenuApiAuthorizationCache menuApiAuthorizationCache;

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

    /**
     * menu_api에 등록된 (메서드, 패턴) 조합에 한해 role_menu 매핑 기준으로 메뉴 접근 권한을 검증한다.
     * 등록되지 않은 API는 검사 대상이 아니므로 그대로 통과한다. SUPER_ADMIN은 통과한다.
     * 매칭 키는 Spring이 핸들러 결정 시 이미 확정한 BEST_MATCHING_PATTERN_ATTRIBUTE를 쓴다 —
     * 클라이언트 입력이 아니며, api_info.url_pattern과 같은 출처(RequestMappingHandlerMapping)에서 나온 값이다.
     */
  private void validateMenuAccess(HttpServletRequest request, Authentication authentication) {
    if (securityService.isSuperAdmin(authentication)) {
      return;
    }

    Object patternAttr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (patternAttr == null) {
      return;
    }
    String pattern = patternAttr.toString();

    if (menuApiAuthorizationCache.isAlwaysAllowed(request.getMethod(), pattern)) {
      return;
    }

    Set<Long> menuIds = menuApiAuthorizationCache.menuIdsFor(request.getMethod(), pattern);
    if (menuIds.isEmpty()) {
      return;
    }

    boolean hasAccess = menuIds.stream().anyMatch(menuId -> securityService.hasMenu(authentication, menuId));
    if (!hasAccess) {
      throw ErrorCode.MENU_ACCESS_DENIED.toException();
    }
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

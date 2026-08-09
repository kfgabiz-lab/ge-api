package com.ge.bo.security;

import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.AdminUserSiteId;
import com.ge.bo.entity.Menu;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.AdminUserSiteRepository;
import com.ge.bo.repository.MenuRepository;
import com.ge.bo.repository.RoleMenuRepository;
import com.ge.bo.repository.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 사이트 권한 + 메뉴 권한 통합 인가 서비스
 * SYSTEM_ADMIN은 전체 통과, 그 외에는 X-Site-Id / X-Menu-Path 헤더 기준으로 접근 권한을 검증한다.
 * AccessValidationInterceptor에서 인증된 요청에 대해 호출된다.
 */
@Service
@RequiredArgsConstructor
public class AccessAuthorizationService {

  private static final String HEADER_SITE_ID = "X-Site-Id";
  private static final String HEADER_MENU_PATH = "X-Menu-Path";
  private static final String WIDGET_SUB_PATH_PREFIX = "/admin/widgetSub/";

  private final AdminRepository adminRepository;
  private final AdminUserSiteRepository adminUserSiteRepository;
  private final MenuRepository menuRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final RoleRepository roleRepository;
  private final SecurityService securityService;

    /**
     * 사이트 권한 → 메뉴 권한 순으로 검증한다.
     * SYSTEM_ADMIN(role.is_system=true)은 검증 없이 즉시 통과한다.
     */
  @Transactional(readOnly = true)
    public void validateAccess(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (securityService.isSystemAdmin(authentication)) {
      return;
    }

    String email = authentication.getName();
    AdminUser admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보를 확인할 수 없습니다."));

    Long siteId = validateSiteAccess(request, admin);
    validateMenuAccess(request, authentication, siteId);
  }

    /** X-Site-Id 헤더 기반 사이트 접근 권한 검증 — 헤더 필수, admin_user_site 매핑 존재 여부로 판단 */
  private Long validateSiteAccess(HttpServletRequest request, AdminUser admin) {
    Long siteId = parseSiteId(request.getHeader(HEADER_SITE_ID));
    if (siteId == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SITE_ID_REQUIRED", "X-Site-Id 헤더가 필요합니다.");
    }

    boolean hasAccess = adminUserSiteRepository.existsById(new AdminUserSiteId(admin.getId(), siteId));
    if (!hasAccess) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "SITE_ACCESS_DENIED", "해당 홈페이지에 대한 접근 권한이 없습니다.");
    }
    return siteId;
  }

    /**
     * X-Menu-Path 헤더 기반 메뉴 접근 권한 검증 — 헤더 필수, 최장 접두어로 메뉴를 찾은 뒤 role_menu 매핑 확인
     * 예외: /admin/widgetSub/ 로 시작하는 경로(위젯 상세/등록/수정 화면)는 대응하는 menu 행이 존재하지 않는 구조라
     * 메뉴 체크만 건너뛴다 (사이트 체크는 validateAccess에서 이미 먼저 수행됨)
     */
  private void validateMenuAccess(HttpServletRequest request, Authentication authentication, Long siteId) {
    String menuPath = request.getHeader(HEADER_MENU_PATH);
    if (!StringUtils.hasText(menuPath)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "MENU_PATH_REQUIRED", "X-Menu-Path 헤더가 필요합니다.");
    }

    String trimmedMenuPath = menuPath.trim();
    if (trimmedMenuPath.startsWith(WIDGET_SUB_PATH_PREFIX)) {
      return;
    }

    Menu menu = resolveMenuByPath(trimmedMenuPath, siteId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "MENU_NOT_FOUND", "요청 경로에 해당하는 메뉴를 찾을 수 없습니다."));

    Long roleId = resolveRoleId(authentication)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "MENU_ACCESS_DENIED", "해당 메뉴에 대한 접근 권한이 없습니다."));

    boolean hasAccess = roleMenuRepository.existsByRoleIdAndMenuId(roleId, menu.getId());
    if (!hasAccess) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "MENU_ACCESS_DENIED", "해당 메뉴에 대한 접근 권한이 없습니다.");
    }
  }

    /**
     * 요청 경로와 최장 접두어로 일치하는 BO 메뉴 조회
     * 경로 세그먼트 경계를 지켜서 매칭한다 (예: url="/admin/settings/users"는 요청 경로가
     * 정확히 같거나 "/admin/settings/users/"로 시작할 때만 일치 — "/admin/settings/users2" 같은 오탐 방지)
     * 같은 site_id + url을 가진 메뉴 행이 중복 등록되어 최장 길이 후보가 2개 이상이면
     * 어느 쪽을 채택할지 모호하므로 임의 선택하지 않고 실패 처리한다(fail-safe: 모호하면 거부)
     */
  private Optional<Menu> resolveMenuByPath(String path, Long siteId) {
    List<Menu> candidates = menuRepository.findBoMenusWithUrlBySite(siteId);
    List<Menu> matched = candidates.stream()
                .filter(m -> matchesPath(path, m.getUrl()))
                .toList();
    if (matched.isEmpty()) {
      return Optional.empty();
    }

    int maxLength = matched.stream()
                .mapToInt(m -> m.getUrl().length())
                .max()
                .orElseThrow();

    List<Menu> longest = matched.stream()
                .filter(m -> m.getUrl().length() == maxLength)
                .toList();

    return longest.size() == 1 ? Optional.of(longest.get(0)) : Optional.empty();
  }

  private boolean matchesPath(String requestPath, String menuUrl) {
    return requestPath.equals(menuUrl) || requestPath.startsWith(menuUrl + "/");
  }

    /** 인증 principal의 authority(ROLE_{code})에서 role code를 추출해 role.id를 조회 — MenuService.resolveAllowedMenuIds()와 동일 패턴 */
  private Optional<Long> resolveRoleId(Authentication authentication) {
    String authority = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("");
    String roleCode = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
    return roleRepository.findByCode(roleCode).map(role -> role.getId());
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

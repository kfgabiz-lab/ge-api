package com.ge.bo.security;

import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * role.is_system 기반 시스템관리자 판별 서비스
 * @PreAuthorize 표현식에서 @securityService.isSystemAdmin(authentication)으로 사용
 */
@Component("securityService")
@RequiredArgsConstructor
public class SecurityService {

  private final RoleRepository roleRepository;
  private final AdminRepository adminRepository;

    /**
     * 현재 인증 사용자가 시스템관리자인지 판별
     * SecurityContext authority에서 role code 추출 → role.is_system 조회
     */
  public boolean isSystemAdmin(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    String roleCode = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> {
                  String auth = a.getAuthority();
                  return auth.startsWith("ROLE_") ? auth.substring(5) : auth;
                })
                .orElse("");

    return roleRepository.findByCode(roleCode)
                .map(role -> role.isSystem())
                .orElse(false);
  }

    /**
     * 현재 인증 사용자가 대상 관리자 본인인지 판별
     * JwtAuthenticationFilter가 principal로 email을 넣으므로 authentication.getName()은 email이다
     */
  public boolean isSelf(Authentication authentication, Long adminUserId) {
    if (authentication == null || !authentication.isAuthenticated() || adminUserId == null) {
      return false;
    }
    return adminRepository.findById(adminUserId)
        .map(a -> a.getEmail() != null && a.getEmail().equals(authentication.getName()))
        .orElse(false);
  }
}

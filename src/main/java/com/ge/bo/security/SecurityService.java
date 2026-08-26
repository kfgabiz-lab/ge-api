package com.ge.bo.security;

import com.ge.bo.entity.Menu;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.MenuRepository;
import com.ge.bo.repository.RoleMenuRepository;
import com.ge.bo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("securityService")
@RequiredArgsConstructor
public class SecurityService {

  private final RoleRepository roleRepository;
  private final AdminRepository adminRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final MenuRepository menuRepository;

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

  public boolean isSuperAdmin(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
  }

  public boolean hasMenu(Authentication authentication, Long menuId) {
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
                .map(role -> roleMenuRepository.existsByRoleIdAndMenuId(role.getId(), menuId))
                .orElse(false);
  }

  public boolean isSelf(Authentication authentication, Long adminUserId) {
    if (authentication == null || !authentication.isAuthenticated() || adminUserId == null) {
      return false;
    }
    return adminRepository.findById(adminUserId)
        .map(a -> a.getEmployeeId() != null && a.getEmployeeId().equals(authentication.getName()))
        .orElse(false);
  }

  public boolean canAccessWidgetMenu(Authentication authentication, String slug, Long menuId) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    String targetUrl = "/admin/widget/" + slug;
    List<Menu> candidates = menuRepository.findByUrl(targetUrl);
    if (candidates.isEmpty()) {
      return true;
    }

    List<Long> candidateIds = candidates.stream().map(Menu::getId).toList();
    if (menuId != null && candidateIds.contains(menuId) && hasMenu(authentication, menuId)) {
      return true;
    }

    return candidateIds.stream().anyMatch(id -> hasMenu(authentication, id));
  }
}

package com.ge.bo.common.context;

import com.ge.bo.repository.AdminUserSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EffectiveSiteResolver {

  private final AdminUserSiteRepository adminUserSiteRepository;

  public Long resolve(Long siteId) {
    return siteId != null ? siteId : resolveDefaultSiteId();
  }

  public Long resolveCurrent() {
    return resolve(SiteContext.getSiteId().orElse(null));
  }

  private Long resolveDefaultSiteId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    String employeeId = authentication.getName();
    if (employeeId == null || employeeId.isBlank()) {
      return null;
    }
    return adminUserSiteRepository.findDefaultSiteIdByEmployeeId(employeeId).orElse(null);
  }
}

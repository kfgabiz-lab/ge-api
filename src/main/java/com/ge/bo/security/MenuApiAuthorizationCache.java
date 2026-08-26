package com.ge.bo.security;

import com.ge.bo.repository.ApiInfoRepository;
import com.ge.bo.repository.MenuApiRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * "HTTP 메서드 + Spring이 확정한 매칭 패턴" → 등록된 menuId 집합 캐시.
 * menu_api에 등록되지 않은 API는 이 캐시에 없으므로 메뉴 인가 검사 대상에서 제외된다(그대로 통과).
 * api_info.access_type='ALL'인 API는 menu_api 등록 여부와 무관하게 항상 허용한다.
 * MenuService.updateMenuApiMapping / ApiInfoService의 등록·수정·삭제 시 reload()로 갱신된다.
 */
@Component
@RequiredArgsConstructor
public class MenuApiAuthorizationCache {

  private final MenuApiRepository menuApiRepository;
  private final ApiInfoRepository apiInfoRepository;

  private volatile Map<String, Set<Long>> cache = Map.of();
  private volatile Set<String> allowAllKeys = Set.of();

  @PostConstruct
    public void init() {
    reload();
  }

  public void reload() {
    Map<String, Set<Long>> next = new HashMap<>();
    for (MenuApiRepository.MenuApiAuthProjection row : menuApiRepository.findAllForAuthorizationCache()) {
      String key = key(row.getMethod(), row.getUrlPattern());
      next.computeIfAbsent(key, k -> new HashSet<>()).add(row.getMenuId());
    }
    this.cache = next;

    Set<String> nextAllowAll = new HashSet<>();
    apiInfoRepository.findByAccessTypeAndActiveTrue("ALL")
                .forEach(api -> nextAllowAll.add(key(api.getMethod(), api.getUrlPattern())));
    this.allowAllKeys = nextAllowAll;
  }

  public boolean isAlwaysAllowed(String method, String urlPattern) {
    return allowAllKeys.contains(key(method, urlPattern));
  }

  public Set<Long> menuIdsFor(String method, String urlPattern) {
    return cache.getOrDefault(key(method, urlPattern), Set.of());
  }

  private String key(String method, String urlPattern) {
    return method + " " + urlPattern;
  }
}

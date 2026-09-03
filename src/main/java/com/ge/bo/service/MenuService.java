package com.ge.bo.service;

import com.ge.bo.common.context.EffectiveSiteResolver;
import com.ge.bo.dto.FoGnbMenuResponse;
import com.ge.bo.dto.FoMenuMetaResponse;
import com.ge.bo.dto.MenuImportRequest;
import com.ge.bo.dto.MenuImportResponse;
import com.ge.bo.dto.MenuRequest;
import com.ge.bo.dto.MenuResponse;
import com.ge.bo.dto.MenuSortBatchItem;
import com.ge.bo.dto.RoleMenuResponse;
import com.ge.bo.entity.Menu;
import com.ge.bo.entity.MenuApi;
import com.ge.bo.entity.Role;
import com.ge.bo.entity.RoleMenu;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.ApiInfoRepository;
import com.ge.bo.repository.MenuApiRepository;
import com.ge.bo.repository.MenuRepository;
import com.ge.bo.repository.RoleMenuRepository;
import com.ge.bo.repository.RoleRepository;
import com.ge.bo.repository.SiteRepository;
import com.ge.bo.security.MenuApiAuthorizationCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 메뉴 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class MenuService {

  private final MenuRepository menuRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final RoleRepository roleRepository;
  private final MessageResourceService messageResourceService;
  private final MenuApiRepository menuApiRepository;
  private final ApiInfoRepository apiInfoRepository;
  private final MenuApiAuthorizationCache menuApiAuthorizationCache;
  private final EffectiveSiteResolver effectiveSiteResolver;
  private final SiteRepository siteRepository;

  private static final Pattern XSS_PATTERN = Pattern.compile("[<>\"']");

  private static final Set<Long> SUPER_ADMIN_ONLY_MENU_IDS = Set.of(
      212L, 31L, 70L, 29L, 74L, 33L, 92L, 214L, 215L, 226L);

  private static final String SYSTEM_ADMIN_CODE = "SYSTEM_ADMIN";

  private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /* ══════════════════════════════════════ */
    /*  조회                                  */
    /* ══════════════════════════════════════ */

    /**
     * 메뉴 트리 조회
     * - BO/FO 공통: 사이트별 분리 — siteId 필터링 적용 (siteId IS NULL = 공통 메뉴)
     * - 시스템관리자(role.is_system=true)가 아닌 경우 isSystem=true 메뉴 제외
     * @param forNav true = 사이드바 네비게이션용, false = 관리 페이지용
     */
  @Transactional(readOnly = true)
    public List<MenuResponse> getMenuTree(String menuType, Long siteId, boolean forNav) {
    validateMenuType(menuType);

    boolean isSystemAdmin = isCurrentUserSystemAdmin();

    if ("BO".equals(menuType)) {
      Long effectiveSiteId = resolveEffectiveSiteId(siteId);

      if (effectiveSiteId == null && !isSystemAdmin) {
        return List.of();
      }

      List<Menu> allMenus = effectiveSiteId != null
          ? menuRepository.findNavMenusByTypeAndSite(menuType, effectiveSiteId)
          : menuRepository.findByMenuTypeAndParentIsNullOrderBySortOrderAsc(menuType);

            /* 시스템관리자(role.is_system=true): 전체 반환 */
      if (isSystemAdmin) {
        return allMenus.stream().map(MenuResponse::from).toList();
      }

            /* 사이드바 네비게이션용 — role_menu 기반 필터링 적용 */
      if (forNav) {
        Set<Long> allowedMenuIds = resolveAllowedMenuIds(effectiveSiteId);
        return allMenus.stream()
                    .filter(m -> !m.isSystem())
                    .filter(m -> MenuResponse.isAllowed(m, allowedMenuIds))
                    .map(m -> MenuResponse.fromFiltered(m, allowedMenuIds))
                    .toList();
      }

            /* 관리 페이지용 — isSystem 필터링만 적용 (전체 메뉴 표시) */
      return allMenus.stream()
                .filter(m -> !m.isSystem())
                .map(MenuResponse::from).toList();
    }

        /* FO: 네비게이션용 — 공통(NULL) + 해당 사이트 메뉴 */
    if (siteId != null && forNav) {
      return menuRepository.findNavMenusByTypeAndSite(menuType, siteId)
                    .stream()
                    .filter(m -> isSystemAdmin || !m.isSystem())
                    .map(MenuResponse::from).toList();
    }

        /* FO: 관리 페이지용 — 해당 사이트 전용 메뉴만 */
    if (siteId != null) {
      return menuRepository.findByMenuTypeAndSiteIdAndParentIsNullOrderBySortOrderAsc(menuType, siteId)
                    .stream()
                    .filter(m -> isSystemAdmin || !m.isSystem())
                    .map(MenuResponse::from).toList();
    }

        /* fallback — 사이트 미선택 시 전체 */
    return menuRepository.findByMenuTypeAndParentIsNullOrderBySortOrderAsc(menuType)
                .stream()
                .filter(m -> isSystemAdmin || !m.isSystem())
                .map(MenuResponse::from).toList();
  }

  @Transactional(readOnly = true)
    public List<MenuResponse> getMenuTreeForSite(String menuType, Long siteId) {
    validateMenuType(menuType);
    if (siteId == null) {
      throw ErrorCode.SITE_NOT_FOUND.toException();
    }

    List<Menu> allMenus = "BO".equals(menuType)
            ? menuRepository.findNavMenusByTypeAndSite(menuType, siteId)
            : menuRepository.findByMenuTypeAndSiteIdAndParentIsNullOrderBySortOrderAsc(menuType, siteId);

    return allMenus.stream().map(MenuResponse::from).toList();
  }

    /**
     * FO GNB 메뉴 조회 — 비로그인 공개 API용
     * visible=true 루트 메뉴 + visible=true 자식 메뉴만 반환
     */
  @Transactional(readOnly = true)
    public List<FoGnbMenuResponse> getFoGnbMenus(Long siteId) {
    List<Menu> allMenus = menuRepository.findFoVisibleMenus(siteId);

    Map<Long, List<Menu>> childrenByParentId = allMenus.stream()
                .filter(m -> m.getParent() != null)
                .collect(Collectors.groupingBy(m -> m.getParent().getId()));

    List<Menu> rootMenus = allMenus.stream()
                .filter(m -> m.getParent() == null)
                .toList();

        /* 트리 전체를 재귀 순회하여 name/description msgKey를 수집 → en 배치 조회(단일 호출) */
    List<String> msgKeys = new ArrayList<>();
    rootMenus.forEach(m -> collectMsgKeys(m, childrenByParentId, msgKeys));
    Map<String, String> enMap = messageResourceService.resolveEnMap(msgKeys);

        /* 수집한 en 맵으로 트리 전체 치환 (children 재귀 치환은 DTO에서 처리) */
    return rootMenus.stream()
                .map(m -> FoGnbMenuResponse.from(m, childrenByParentId, enMap, siteId))
                .toList();
  }

    /** FO 정적 메뉴 페이지 SEO 메타 조회 (URL 기준 단건) */
  @Transactional(readOnly = true)
    public FoMenuMetaResponse getFoMenuMeta(String url, Long siteId) {
    return menuRepository.findFoMenuByUrl(url, siteId)
                .map(FoMenuMetaResponse::from)
                .orElse(FoMenuMetaResponse.EMPTY);
  }

    /** 메뉴 트리를 재귀 순회하며 name/description msgKey를 수집 (visible=true 자식만) */
  private void collectMsgKeys(Menu menu, Map<Long, List<Menu>> childrenByParentId, List<String> keys) {
    if (menu.getNameMsgKey() != null && !menu.getNameMsgKey().isBlank()) keys.add(menu.getNameMsgKey());
    if (menu.getDescriptionMsgKey() != null && !menu.getDescriptionMsgKey().isBlank()) keys.add(menu.getDescriptionMsgKey());
    childrenByParentId.getOrDefault(menu.getId(), List.of()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getVisible()))
                .forEach(c -> collectMsgKeys(c, childrenByParentId, keys));
  }

    /** 메뉴 단건 조회 */
  @Transactional(readOnly = true)
    public MenuResponse getMenu(Long id, Long siteId) {
    Menu menu = findMenuOrThrow(id, siteId);
    return MenuResponse.from(menu);
  }

    /* ══════════════════════════════════════ */
    /*  생성                                  */
    /* ══════════════════════════════════════ */

    /** 메뉴 생성 */
  @Transactional
    public MenuResponse createMenu(MenuRequest request, Long siteId) {
    String cleanUrl = sanitizeUrl(request.url());
    Menu parent = resolveParent(request.parentId(), request.menuType());

    validateChildUrl(parent, cleanUrl);
    validateUrlFormat(cleanUrl);

    /* name/nameMsgKey 검증 — 둘 중 하나는 필수 */
    boolean hasMsgKey = request.nameMsgKey() != null && !request.nameMsgKey().isBlank();
    boolean hasDirectName = request.name() != null && !request.name().isBlank();
    if (!hasMsgKey && !hasDirectName) {
      throw ErrorCode.MENU_NAME_REQUIRED.toException();
    }

    /* 다국어 모드 ON: msgKey로 ko 값 조회 / OFF: 직접 입력값 사용 */
    String nameKo = hasMsgKey
            ? messageResourceService.resolveKo(request.nameMsgKey())
            : request.name().trim();
    boolean hasDescMsgKey = request.descriptionMsgKey() != null && !request.descriptionMsgKey().isBlank();
    String descriptionKo = hasDescMsgKey
            ? messageResourceService.resolveKo(request.descriptionMsgKey())
            : (request.description() != null ? request.description().trim() : "");

    Menu menu = Menu.builder()
            .name(nameKo)
            .nameMsgKey(hasMsgKey ? request.nameMsgKey() : null)
            .description(descriptionKo.isEmpty() ? null : descriptionKo)
            .descriptionMsgKey(hasDescMsgKey ? request.descriptionMsgKey() : null)
            .url(cleanUrl)
            .metaTitle(normalizeMeta(request.metaTitle()))
            .metaDescription(normalizeMeta(request.metaDescription()))
            .icon(request.icon())
            .parent(parent)
            .menuType(request.menuType())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 1)
            .visible(request.visible() != null ? request.visible() : true)
            .siteId(siteId)
            .build();

    return MenuResponse.from(menuRepository.save(menu));
  }

  @Transactional
    public MenuImportResponse importMenus(MenuImportRequest request, Long targetSiteId) {
    if (targetSiteId == null) {
      throw ErrorCode.MENU_IMPORT_TARGET_SITE_REQUIRED.toException();
    }

    Long sourceSiteId = request.sourceSiteId();
    if (sourceSiteId.equals(targetSiteId)) {
      throw ErrorCode.MENU_IMPORT_SAME_SITE.toException();
    }
    if (!siteRepository.existsById(sourceSiteId)) {
      throw ErrorCode.SITE_NOT_FOUND.toException();
    }
    if (!siteRepository.existsById(targetSiteId)) {
      throw ErrorCode.SITE_NOT_FOUND.toException();
    }

    Map<Long, Menu> sourceMenusById = new LinkedHashMap<>();
    ArrayDeque<Long> queue = new ArrayDeque<>(new LinkedHashSet<>(request.menuIds()));
    while (!queue.isEmpty()) {
      Long id = queue.poll();
      if (sourceMenusById.containsKey(id)) {
        continue;
      }
      Menu menu = menuRepository.findById(id)
                .orElseThrow(ErrorCode.MENU_NOT_FOUND::toException);
      if (!"BO".equals(menu.getMenuType()) || !sourceSiteId.equals(menu.getSiteId())) {
        throw ErrorCode.MENU_IMPORT_INVALID_SOURCE.toException();
      }
      sourceMenusById.put(id, menu);
      if (menu.getParent() != null && !sourceMenusById.containsKey(menu.getParent().getId())) {
        queue.add(menu.getParent().getId());
      }
    }

    List<Menu> orderedSourceMenus = sourceMenusById.values().stream()
            .sorted(Comparator.comparingInt(this::depthOf))
            .toList();

    Map<Long, Long> oldIdToNewId = new HashMap<>();
    List<MenuResponse> importedMenus = new ArrayList<>();
    List<Long> skippedMenuIds = new ArrayList<>();

    for (Menu source : orderedSourceMenus) {
      Long originalParentId = source.getParent() != null ? source.getParent().getId() : null;

      if (originalParentId != null && !oldIdToNewId.containsKey(originalParentId)) {
        skippedMenuIds.add(source.getId());
        continue;
      }

      String url = source.getUrl();
      if (url != null && !url.isBlank()
                    && menuRepository.existsByMenuTypeAndSiteIdAndUrl(source.getMenuType(), targetSiteId, url)) {
        skippedMenuIds.add(source.getId());
        continue;
      }
      validateUrlFormat(url);

      Menu parent = originalParentId != null
                ? menuRepository.getReferenceById(oldIdToNewId.get(originalParentId))
                : null;

      Menu copy = Menu.builder()
                .name(source.getName())
                .nameMsgKey(source.getNameMsgKey())
                .description(source.getDescription())
                .descriptionMsgKey(source.getDescriptionMsgKey())
                .url(url)
                .metaTitle(source.getMetaTitle())
                .metaDescription(source.getMetaDescription())
                .icon(source.getIcon())
                .parent(parent)
                .menuType(source.getMenuType())
                .sortOrder(source.getSortOrder())
                .visible(source.getVisible())
                .siteId(targetSiteId)
                .isSystem(false)
                .build();

      Menu saved = menuRepository.save(copy);
      oldIdToNewId.put(source.getId(), saved.getId());
      importedMenus.add(MenuResponse.from(saved));
    }

    return new MenuImportResponse(importedMenus.size(), skippedMenuIds.size(), importedMenus, skippedMenuIds);
  }

  private int depthOf(Menu menu) {
    int depth = 0;
    Menu current = menu.getParent();
    while (current != null) {
      depth++;
      current = current.getParent();
    }
    return depth;
  }

    /* ══════════════════════════════════════ */
    /*  수정                                  */
    /* ══════════════════════════════════════ */

    /** 메뉴 수정 */
  @Transactional
    public MenuResponse updateMenu(Long id, MenuRequest request, Long siteId) {
    Menu menu = findMenuOrThrow(id, siteId);
    String cleanUrl = sanitizeUrl(request.url());

        /* menuType 변경 차단 */
    if (!menu.getMenuType().equals(request.menuType())) {
      throw ErrorCode.MENU_TYPE_CHANGE.toException();
    }

        /* parentId 변경 차단 */
    Long currentParentId = menu.getParent() != null ? menu.getParent().getId() : null;
    Long requestParentId = request.parentId();
    if (!Objects.equals(currentParentId, requestParentId)) {
      throw ErrorCode.MENU_PARENT_CHANGE.toException();
    }

    validateChildUrl(menu.getParent(), cleanUrl);
    validateUrlFormat(cleanUrl);

    /* name/nameMsgKey 검증 — 둘 중 하나는 필수 */
    boolean hasMsgKey = request.nameMsgKey() != null && !request.nameMsgKey().isBlank();
    boolean hasDirectName = request.name() != null && !request.name().isBlank();
    if (!hasMsgKey && !hasDirectName) {
      throw ErrorCode.MENU_NAME_REQUIRED.toException();
    }

    /* 다국어 모드 ON: msgKey로 ko 값 조회 / OFF: 직접 입력값 사용 */
    String nameKo = hasMsgKey
            ? messageResourceService.resolveKo(request.nameMsgKey())
            : request.name().trim();
    boolean hasDescMsgKey = request.descriptionMsgKey() != null && !request.descriptionMsgKey().isBlank();
    String descriptionKo = hasDescMsgKey
            ? messageResourceService.resolveKo(request.descriptionMsgKey())
            : (request.description() != null ? request.description().trim() : "");

    menu.setName(nameKo);
    menu.setNameMsgKey(hasMsgKey ? request.nameMsgKey() : null);
    menu.setDescription(descriptionKo.isEmpty() ? null : descriptionKo);
    menu.setDescriptionMsgKey(hasDescMsgKey ? request.descriptionMsgKey() : null);
    menu.setUrl(cleanUrl);
    menu.setMetaTitle(normalizeMeta(request.metaTitle()));
    menu.setMetaDescription(normalizeMeta(request.metaDescription()));
    menu.setIcon(request.icon());
    menu.setSortOrder(request.sortOrder() != null ? request.sortOrder() : menu.getSortOrder());
    menu.setVisible(request.visible() != null ? request.visible() : menu.getVisible());

    return MenuResponse.from(menu);
  }

    /* ══════════════════════════════════════ */
    /*  삭제                                  */
    /* ══════════════════════════════════════ */

    /** 메뉴 삭제 (하위 + role_menu 연쇄 삭제) */
  @Transactional
    public void deleteMenu(Long id, Long siteId) {
    Menu menu = findMenuOrThrow(id, siteId);
    if (menu.isMenuManagement()) {
      throw ErrorCode.MENU_SYSTEM_DELETE.toException();
    }
    menuRepository.delete(menu);
  }

    /* ══════════════════════════════════════ */
    /*  정렬                                  */
    /* ══════════════════════════════════════ */

    /** 정렬 순서 변경 */
  @Transactional
    public void updateSortOrder(Long id, Integer sortOrder, Long siteId) {
    findMenuOrThrow(id, siteId).setSortOrder(sortOrder);
  }

    /** 드래그 정렬 일괄 변경 — sortOrder + parentId 동시 업데이트 */
  @Transactional
    public void updateSortBatch(List<MenuSortBatchItem> items, Long siteId) {
    for (MenuSortBatchItem item : items) {
      Menu menu = findMenuOrThrow(item.id(), siteId);
      if (item.sortOrder() != null) {
        menu.setSortOrder(item.sortOrder());
      }
      Long currentParentId = menu.getParent() != null ? menu.getParent().getId() : null;
      if (!Objects.equals(currentParentId, item.parentId())) {
        if (item.parentId() == null) {
          menu.setParent(null);
        } else {
          Menu parent = menuRepository.findById(item.parentId())
                        .orElseThrow(ErrorCode.MENU_PARENT_NOT_FOUND::toException);
          menu.setParent(parent);
        }
      }
    }
  }

    /* ══════════════════════════════════════ */
    /*  역할 매핑                              */
    /* ══════════════════════════════════════ */

    /** 메뉴별 역할 매핑 조회 */
  @Transactional(readOnly = true)
    public List<RoleMenuResponse> getRoleMenuMappings(Long menuId, Long siteId) {
    findMenuOrThrow(menuId, siteId);
    Long effectiveSiteId = resolveEffectiveSiteId(siteId);
    List<Role> roles = roleRepository.findByCodeNot(SYSTEM_ADMIN_CODE,
        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("id")));
    Set<Long> mappedRoleIds = Set.copyOf(
        roleMenuRepository.findRoleIdsByMenuIdAndSite(menuId, effectiveSiteId));

    return roles.stream()
            .map(role -> new RoleMenuResponse(
                menuId, role.getId(), role.getCode(), role.getDisplayName(),
                mappedRoleIds.contains(role.getId())
            )).toList();
  }

    /** 역할 매핑 변경 (멱등성 보장) */
  @Transactional
    public void updateRoleMenuMapping(Long menuId, Long roleId, boolean hasAccess, Long siteId) {
    findMenuOrThrow(menuId, siteId);
    Role role = roleRepository.findById(roleId)
                .orElseThrow(ErrorCode.ROLE_NOT_FOUND::toException);

    if (SUPER_ADMIN_ONLY_MENU_IDS.contains(menuId) && !"SUPER_ADMIN".equals(role.getCode())) {
      throw ErrorCode.MENU_ROLE_PROTECTED.toException();
    }

    Long effectiveSiteId = resolveEffectiveSiteId(siteId);
    List<RoleMenu> rows = effectiveSiteId == null
        ? roleMenuRepository.findByRoleIdAndMenuIdAndSiteIdIsNull(roleId, menuId)
        : roleMenuRepository.findByRoleIdAndMenuIdAndSiteId(roleId, menuId, effectiveSiteId);

    if (hasAccess) {
      if (rows.isEmpty()) {
        roleMenuRepository.save(RoleMenu.builder()
            .roleId(roleId).menuId(menuId).siteId(effectiveSiteId).build());
      }
    } else if (!rows.isEmpty()) {
      roleMenuRepository.deleteAll(rows);
    }
  }

    /* ══════════════════════════════════════ */
    /*  API 매핑                               */
    /* ══════════════════════════════════════ */

    /** 메뉴가 사용하는 API 매핑 조회 (apiInfoId 목록) */
  @Transactional(readOnly = true)
    public List<Long> getMenuApiMappings(Long menuId, Long siteId) {
    findMenuOrThrow(menuId, siteId);
    return menuApiRepository.findByMenuId(menuId).stream()
                .map(MenuApi::getApiInfoId).toList();
  }

    /** API 매핑 전체 치환 (멱등성 보장) */
  @Transactional
    public void updateMenuApiMapping(Long menuId, List<Long> apiInfoIds, Long siteId) {
    findMenuOrThrow(menuId, siteId);
    Set<Long> requested = apiInfoIds == null ? Set.of() : new java.util.HashSet<>(apiInfoIds);

    if (!requested.isEmpty() && apiInfoRepository.findAllById(requested).size() != requested.size()) {
      throw ErrorCode.API_INFO_NOT_FOUND.toException();
    }

    Set<Long> current = menuApiRepository.findByMenuId(menuId).stream()
                .map(MenuApi::getApiInfoId).collect(Collectors.toSet());

    Set<Long> toAdd = new java.util.HashSet<>(requested);
    toAdd.removeAll(current);
    Set<Long> toRemove = new java.util.HashSet<>(current);
    toRemove.removeAll(requested);

    String employeeId = SecurityContextHolder.getContext().getAuthentication().getName();
    toAdd.forEach(apiInfoId -> menuApiRepository.save(
        MenuApi.builder().menuId(menuId).apiInfoId(apiInfoId).createdBy(employeeId).build()));
    toRemove.forEach(apiInfoId -> menuApiRepository.deleteByMenuIdAndApiInfoId(menuId, apiInfoId));

    menuApiAuthorizationCache.reload();
  }

    /* ══════════════════════════════════════ */
    /*  내부 헬퍼 — 다국어 연동               */
    /* ══════════════════════════════════════ */


    /* ══════════════════════════════════════ */
    /*  내부 헬퍼 — 조회                       */
    /* ══════════════════════════════════════ */

  private Menu findMenuOrThrow(Long id) {
    return menuRepository.findById(id)
            .orElseThrow(ErrorCode.MENU_NOT_FOUND::toException);
  }

  private Menu findMenuOrThrow(Long id, Long siteId) {
    Menu menu = findMenuOrThrow(id);
    if (siteId != null && menu.getSiteId() != null
        && !siteId.equals(menu.getSiteId()) && !isGlobalAdmin()) {
      throw ErrorCode.MENU_NOT_FOUND.toException();
    }
    return menu;
  }

    /** 현재 로그인한 사용자가 시스템관리자인지 확인 — role.is_system 기반 */
  private boolean isCurrentUserSystemAdmin() {
    return roleRepository.findByCode(currentRoleCode())
            .map(role -> role.isSystem())
            .orElse(false);
  }

  private boolean isGlobalAdmin() {
    String roleCode = currentRoleCode();
    if (SUPER_ADMIN_CODE.equals(roleCode)) {
      return true;
    }
    return roleRepository.findByCode(roleCode).map(Role::isSystem).orElse(false);
  }

    /**
     * 현재 로그인한 사용자의 역할이 해당 사이트에서 접근 가능한 menuId Set 반환
     * SecurityContextHolder → role 코드 → Role ID → role_menu 조회
     * 공통(site_id IS NULL) 매핑 + 해당 사이트 전용 매핑을 함께 포함한다
     * 역할이 없거나 매핑이 없으면 빈 Set 반환 (메뉴 전체 숨김)
     */
  private Set<Long> resolveAllowedMenuIds(Long siteId) {
    return roleRepository.findByCode(currentRoleCode())
            .map(role -> Set.copyOf(
                roleMenuRepository.findMenuIdsByRoleIdAndSite(role.getId(), siteId)))
            .orElse(Set.of());
  }

  private String currentRoleCode() {
    org.springframework.security.core.Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return "";
    }
    return authentication.getAuthorities().stream()
            .findFirst()
            .map(a -> {
              String auth = a.getAuthority();
              return auth.startsWith("ROLE_") ? auth.substring(5) : auth;
            })
            .orElse("");
  }

  private Long resolveEffectiveSiteId(Long siteId) {
    return effectiveSiteResolver.resolve(siteId);
  }

    /* ══════════════════════════════════════ */
    /*  내부 헬퍼 — 검증                       */
    /* ══════════════════════════════════════ */

  private void validateMenuType(String menuType) {
    if (!"BO".equals(menuType) && !"FO".equals(menuType)) {
      throw ErrorCode.MENU_TYPE_INVALID.toException();
    }
  }

    /** 부모 메뉴 검증 + 반환 (3depth까지 허용, 4depth 차단) */
  private Menu resolveParent(Long parentId, String menuType) {
    if (parentId == null) {
      return null;
    }
    Menu parent = menuRepository.findById(parentId)
            .orElseThrow(ErrorCode.MENU_PARENT_NOT_FOUND::toException);
    if (!parent.getMenuType().equals(menuType)) {
      throw ErrorCode.MENU_TYPE_MISMATCH.toException();
    }
        /* depth 계산: parent가 이미 3depth(조부모의 부모 존재)이면 차단 */
    if (parent.getParent() != null && parent.getParent().getParent() != null) {
      throw ErrorCode.MENU_DEPTH_EXCEEDED.toException();
    }
    return parent;
  }

    /** 하위메뉴 URL 검증 — 폴더(URL 없음)도 하위 추가 가능, 검증 스킵 */
  private void validateChildUrl(Menu parent, String url) {
        // 폴더(그룹 메뉴)는 URL 없이도 하위에 추가 가능
        // URL이 있는 경우에만 형식 검증은 validateUrlFormat에서 수행
  }

    /** URL 형식 검증 — 외부 링크(http/https)는 연속 슬래시 제한 대상에서 제외 */
  private void validateUrlFormat(String url) {
    if (url == null || url.isEmpty() || url.startsWith("http://") || url.startsWith("https://")) {
      return;
    }
    if (url.contains("//")) {
      throw ErrorCode.MENU_URL_INVALID.toException();
    }
  }

    /* ══════════════════════════════════════ */
    /*  내부 헬퍼 — 정제                       */
    /* ══════════════════════════════════════ */

    /** URL 정제: XSS 체크 + trim + trailing slash 제거 */
  private String sanitizeUrl(String url) {
    if (url == null) {
      return null;
    }
    if (XSS_PATTERN.matcher(url).find()) {
      throw ErrorCode.MENU_XSS_DETECTED.toException();
    }
    String cleaned = url.trim();
    if (cleaned.length() > 1 && cleaned.endsWith("/")) {
      cleaned = cleaned.replaceAll("/+$", "");
    }
    return cleaned.isEmpty() ? null : cleaned;
  }

    /** Meta Title/Description 정제: 빈 문자열은 NULL로 저장 */
  private String normalizeMeta(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}

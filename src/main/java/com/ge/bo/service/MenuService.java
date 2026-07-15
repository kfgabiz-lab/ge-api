package com.ge.bo.service;

import com.ge.bo.dto.FoGnbMenuResponse;
import com.ge.bo.dto.MenuRequest;
import com.ge.bo.dto.MenuResponse;
import com.ge.bo.dto.MenuSortBatchItem;
import com.ge.bo.dto.RoleMenuResponse;
import com.ge.bo.entity.Menu;
import com.ge.bo.entity.Role;
import com.ge.bo.entity.RoleMenu;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.MenuRepository;
import com.ge.bo.repository.RoleMenuRepository;
import com.ge.bo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.ArrayList;
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

  private static final Pattern XSS_PATTERN = Pattern.compile("[<>\"']");

    /* ══════════════════════════════════════ */
    /*  조회                                  */
    /* ══════════════════════════════════════ */

    /**
     * 메뉴 트리 조회
     * - BO: 공통 관리 — 사이트 무관하게 전체 조회
     * - FO: 사이트별 분리 — siteId 필터링 적용
     * - 시스템관리자(role.is_system=true)가 아닌 경우 isSystem=true 메뉴 제외
     * @param forNav true = 사이드바 네비게이션용 (FO 전용 분기), false = 관리 페이지용
     */
  @Transactional(readOnly = true)
    public List<MenuResponse> getMenuTree(String menuType, Long siteId, boolean forNav) {
    validateMenuType(menuType);

    boolean isSystemAdmin = isCurrentUserSystemAdmin();

        /* BO는 사이트 무관 — 공통 메뉴 전체 반환 */
    if ("BO".equals(menuType)) {
      List<Menu> allMenus = menuRepository.findByMenuTypeAndParentIsNullOrderBySortOrderAsc(menuType);

            /* 시스템관리자(role.is_system=true): 전체 반환 */
      if (isSystemAdmin) {
        return allMenus.stream().map(MenuResponse::from).toList();
      }

            /* 사이드바 네비게이션용 — role_menu 기반 필터링 적용 */
      if (forNav) {
        Set<Long> allowedMenuIds = resolveAllowedMenuIds();
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

    /**
     * FO GNB 메뉴 조회 — 비로그인 공개 API용
     * visible=true 루트 메뉴 + visible=true 자식 메뉴만 반환
     */
  @Transactional(readOnly = true)
    public List<FoGnbMenuResponse> getFoGnbMenus() {
    List<Menu> rootMenus = menuRepository.findFoGnbRootMenus();

        /* 트리 전체를 재귀 순회하여 name/description msgKey를 수집 → en 배치 조회(단일 호출) */
    List<String> msgKeys = new ArrayList<>();
    rootMenus.forEach(m -> collectMsgKeys(m, msgKeys));
    Map<String, String> enMap = messageResourceService.resolveEnMap(msgKeys);

        /* 수집한 en 맵으로 트리 전체 치환 (children 재귀 치환은 DTO에서 처리) */
    return rootMenus.stream()
                .map(m -> FoGnbMenuResponse.from(m, enMap))
                .toList();
  }

    /** 메뉴 트리를 재귀 순회하며 name/description msgKey를 수집 (visible=true 자식만) */
  private void collectMsgKeys(Menu menu, List<String> keys) {
    if (menu.getNameMsgKey() != null && !menu.getNameMsgKey().isBlank()) keys.add(menu.getNameMsgKey());
    if (menu.getDescriptionMsgKey() != null && !menu.getDescriptionMsgKey().isBlank()) keys.add(menu.getDescriptionMsgKey());
    menu.getChildren().stream()
                .filter(c -> Boolean.TRUE.equals(c.getVisible()))
                .forEach(c -> collectMsgKeys(c, keys));
  }

    /** 메뉴 단건 조회 */
  @Transactional(readOnly = true)
    public MenuResponse getMenu(Long id) {
    Menu menu = findMenuOrThrow(id);
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
            .icon(request.icon())
            .parent(parent)
            .menuType(request.menuType())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 1)
            .visible(request.visible() != null ? request.visible() : true)
            .siteId(siteId)
            .build();

    return MenuResponse.from(menuRepository.save(menu));
  }

    /* ══════════════════════════════════════ */
    /*  수정                                  */
    /* ══════════════════════════════════════ */

    /** 메뉴 수정 */
  @Transactional
    public MenuResponse updateMenu(Long id, MenuRequest request) {
    Menu menu = findMenuOrThrow(id);
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
    public void deleteMenu(Long id) {
    Menu menu = findMenuOrThrow(id);
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
    public void updateSortOrder(Long id, Integer sortOrder) {
    findMenuOrThrow(id).setSortOrder(sortOrder);
  }

    /** 드래그 정렬 일괄 변경 — sortOrder + parentId 동시 업데이트 */
  @Transactional
    public void updateSortBatch(List<MenuSortBatchItem> items) {
    for (MenuSortBatchItem item : items) {
      Menu menu = findMenuOrThrow(item.id());
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
    public List<RoleMenuResponse> getRoleMenuMappings(Long menuId) {
    findMenuOrThrow(menuId);
        /* is_system=true 역할은 제외 — 일반 사용자에게 시스템관리자 역할 존재 자체를 숨김 */
    List<Role> roles = roleRepository.findAllByOrderByIdAsc().stream()
                .filter(r -> !r.isSystem())
                .collect(java.util.stream.Collectors.toList());
    Set<Long> mappedRoleIds = roleMenuRepository.findByMenuId(menuId)
                .stream().map(RoleMenu::getRoleId).collect(Collectors.toSet());

    return roles.stream()
            .map(role -> new RoleMenuResponse(
                menuId, role.getId(), role.getCode(), role.getDisplayName(),
                mappedRoleIds.contains(role.getId())
            )).toList();
  }

    /** 역할 매핑 변경 (멱등성 보장) */
  @Transactional
    public void updateRoleMenuMapping(Long menuId, Long roleId, boolean hasAccess) {
    findMenuOrThrow(menuId);
    if (!roleRepository.existsById(roleId)) {
      throw ErrorCode.ROLE_NOT_FOUND.toException();
    }

    boolean exists = roleMenuRepository.existsByRoleIdAndMenuId(roleId, menuId);
    if (hasAccess && !exists) {
      roleMenuRepository.save(RoleMenu.builder().roleId(roleId).menuId(menuId).build());
    } else if (!hasAccess && exists) {
      roleMenuRepository.deleteByRoleIdAndMenuId(roleId, menuId);
    }
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

    /** 현재 로그인한 사용자가 시스템관리자인지 확인 — role.is_system 기반 */
  private boolean isCurrentUserSystemAdmin() {
    String roleCode = SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().stream()
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
     * 현재 로그인한 사용자의 역할에 허용된 menuId Set 반환
     * SecurityContextHolder → role 코드 → Role ID → role_menu 조회
     * 역할이 없거나 매핑이 없으면 빈 Set 반환 (메뉴 전체 숨김)
     */
  private Set<Long> resolveAllowedMenuIds() {
    String authority = SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority())
            .orElse("");

        /* "ROLE_SUPER_ADMIN" → "SUPER_ADMIN" 변환 */
    String roleCode = authority.startsWith("ROLE_") ? authority.substring(5) : authority;

    return roleRepository.findByCode(roleCode)
            .map(role -> roleMenuRepository.findByRoleId(role.getId())
                .stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toSet()))
            .orElse(Set.of());
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

    /** URL 형식 검증 */
  private void validateUrlFormat(String url) {
    if (url != null && !url.isEmpty() && url.contains("//")) {
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
}

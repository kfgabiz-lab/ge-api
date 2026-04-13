package com.ge.bo.service;

import com.ge.bo.dto.MenuRequest;
import com.ge.bo.dto.MenuResponse;
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

import java.util.List;
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

    private static final Pattern XSS_PATTERN = Pattern.compile("[<>\"']");

    /* ══════════════════════════════════════ */
    /*  조회                                  */
    /* ══════════════════════════════════════ */

    /** 타입별 메뉴 트리 조회 */
    @Transactional(readOnly = true)
    public List<MenuResponse> getMenuTree(String menuType) {
        validateMenuType(menuType);
        return menuRepository.findByMenuTypeAndParentIsNullOrderBySortOrderAsc(menuType)
                .stream().map(MenuResponse::from).toList();
    }

    /** 메뉴 단건 조회 */
    @Transactional(readOnly = true)
    public MenuResponse getMenu(Long id) {
        return MenuResponse.from(findMenuOrThrow(id));
    }

    /* ══════════════════════════════════════ */
    /*  생성                                  */
    /* ══════════════════════════════════════ */

    /** 메뉴 생성 */
    @Transactional
    public MenuResponse createMenu(MenuRequest request) {
        String trimmedName = sanitizeName(request.name());
        String cleanUrl = sanitizeUrl(request.url());
        Menu parent = resolveParent(request.parentId(), request.menuType());

        validateChildUrl(parent, cleanUrl);
        validateUrlFormat(cleanUrl);
        validateNameDuplicate(trimmedName, parent, request.menuType(), null);
        validateUrlDuplicate(cleanUrl, null);
        validateSlugDuplicate(sanitizeSlug(request.slug()), null);

        Menu menu = Menu.builder()
            .name(trimmedName)
            .url(cleanUrl)
            .icon(request.icon())
            .parent(parent)
            .menuType(request.menuType())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 1)
            .visible(request.visible() != null ? request.visible() : true)
            .isCategory(request.isCategory() != null ? request.isCategory() : false)
            .slug(sanitizeSlug(request.slug()))
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
        String trimmedName = sanitizeName(request.name());
        String cleanUrl = sanitizeUrl(request.url());

        /* menuType 변경 차단 */
        if (!menu.getMenuType().equals(request.menuType())) {
            throw ErrorCode.MENU_TYPE_CHANGE.toException();
        }

        /* parentId 변경 차단 */
        Long currentParentId = menu.getParent() != null ? menu.getParent().getId() : null;
        Long requestParentId = request.parentId();
        if (!java.util.Objects.equals(currentParentId, requestParentId)) {
            throw ErrorCode.MENU_PARENT_CHANGE.toException();
        }

        validateChildUrl(menu.getParent(), cleanUrl);
        validateUrlFormat(cleanUrl);
        validateNameDuplicate(trimmedName, menu.getParent(), menu.getMenuType(), id);
        validateUrlDuplicate(cleanUrl, id);
        validateSlugDuplicate(sanitizeSlug(request.slug()), id);

        menu.setName(trimmedName);
        menu.setUrl(cleanUrl);
        menu.setIcon(request.icon());
        menu.setSortOrder(request.sortOrder() != null ? request.sortOrder() : menu.getSortOrder());
        menu.setVisible(request.visible() != null ? request.visible() : menu.getVisible());
        menu.setIsCategory(request.isCategory() != null ? request.isCategory() : menu.getIsCategory());
        menu.setSlug(sanitizeSlug(request.slug()));

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

    /* ══════════════════════════════════════ */
    /*  역할 매핑                              */
    /* ══════════════════════════════════════ */

    /** 메뉴별 역할 매핑 조회 */
    @Transactional(readOnly = true)
    public List<RoleMenuResponse> getRoleMenuMappings(Long menuId) {
        findMenuOrThrow(menuId);
        List<Role> allRoles = roleRepository.findAll();
        Set<Long> mappedRoleIds = roleMenuRepository.findByMenuId(menuId)
                .stream().map(RoleMenu::getRoleId).collect(Collectors.toSet());

        return allRoles.stream()
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
    /*  내부 헬퍼 — 조회                       */
    /* ══════════════════════════════════════ */

    private Menu findMenuOrThrow(Long id) {
        return menuRepository.findById(id)
            .orElseThrow(ErrorCode.MENU_NOT_FOUND::toException);
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
        if (parentId == null) return null;
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

    /** 이름 중복 검증 (excludeId: 수정 시 자신 제외) */
    private void validateNameDuplicate(String name, Menu parent, String menuType, Long excludeId) {
        boolean duplicate = excludeId == null
            ? menuRepository.existsByNameAndParentAndMenuType(name, parent, menuType)
            : menuRepository.existsByNameAndParentAndMenuTypeAndIdNot(name, parent, menuType, excludeId);
        if (duplicate) throw ErrorCode.MENU_NAME_DUPLICATE.toException();
    }

    /** URL 중복 검증 (excludeId: 수정 시 자신 제외) */
    private void validateUrlDuplicate(String url, Long excludeId) {
        if (url == null || url.isEmpty()) return;
        boolean duplicate = excludeId == null
            ? menuRepository.existsByUrl(url)
            : menuRepository.existsByUrlAndIdNot(url, excludeId);
        if (duplicate) throw ErrorCode.MENU_URL_DUPLICATE.toException();
    }

    /* ══════════════════════════════════════ */
    /*  내부 헬퍼 — 정제                       */
    /* ══════════════════════════════════════ */

    /** 메뉴명 정제: XSS 체크 + trim */
    private String sanitizeName(String name) {
        if (name != null && XSS_PATTERN.matcher(name).find()) {
            throw ErrorCode.MENU_XSS_DETECTED.toException();
        }
        return name != null ? name.trim() : "";
    }

    /** Slug 중복 검증 (excludeId: 수정 시 자신 제외) */
    private void validateSlugDuplicate(String slug, Long excludeId) {
        if (slug == null || slug.isEmpty()) return;
        boolean duplicate = excludeId == null
            ? menuRepository.existsBySlug(slug)
            : menuRepository.existsBySlugAndIdNot(slug, excludeId);
        if (duplicate) throw ErrorCode.MENU_SLUG_DUPLICATE.toException();
    }

    /** 슬러그 정제: trim, 빈 문자열은 null 반환 */
    private String sanitizeSlug(String slug) {
        if (slug == null) return null;
        String trimmed = slug.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** URL 정제: XSS 체크 + trim + trailing slash 제거 */
    private String sanitizeUrl(String url) {
        if (url == null) return null;
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

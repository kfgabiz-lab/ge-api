package com.ge.bo.dto;

import com.ge.bo.entity.Menu;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 메뉴 응답 DTO (재귀 트리 구조)
 */
public record MenuResponse(
    Long id,
    String name,
    String nameMsgKey,
    String description,
    String descriptionMsgKey,
    String url,
    String icon,
    Long parentId,
    String menuType,
    Integer sortOrder,
    Boolean visible,
    Boolean isSystem,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MenuResponse> children
) {
    /** 엔티티 → DTO 변환 (재귀) */
  public static MenuResponse from(Menu menu) {
    return new MenuResponse(
            menu.getId(),
            menu.getName(),
            menu.getNameMsgKey(),
            menu.getDescription(),
            menu.getDescriptionMsgKey(),
            menu.getUrl(),
            menu.getIcon(),
            menu.getParent() != null ? menu.getParent().getId() : null,
            menu.getMenuType(),
            menu.getSortOrder(),
            menu.getVisible(),
            menu.isSystem(),
            menu.getCreatedAt(),
            menu.getUpdatedAt(),
            menu.getChildren() != null
                ? menu.getChildren().stream().map(MenuResponse::from).toList()
                : List.of()
        );
  }

    /**
     * 역할 허용 menuId 기반 재귀 변환 (사이드바 네비게이션용)
     */
  public static MenuResponse fromFiltered(Menu menu, Set<Long> allowedMenuIds) {
    List<MenuResponse> filteredChildren = menu.getChildren() != null
            ? menu.getChildren().stream()
                .filter(child -> isAllowed(child, allowedMenuIds))
                .map(child -> fromFiltered(child, allowedMenuIds))
                .toList()
            : List.of();

    return new MenuResponse(
            menu.getId(),
            menu.getName(),
            menu.getNameMsgKey(),
            menu.getDescription(),
            menu.getDescriptionMsgKey(),
            menu.getUrl(),
            menu.getIcon(),
            menu.getParent() != null ? menu.getParent().getId() : null,
            menu.getMenuType(),
            menu.getSortOrder(),
            menu.getVisible(),
            menu.isSystem(),
            menu.getCreatedAt(),
            menu.getUpdatedAt(),
            filteredChildren
        );
  }

    /** 해당 메뉴가 role_menu에 직접 등록된 경우에만 허용 */
  public static boolean isAllowed(Menu menu, Set<Long> allowedMenuIds) {
    return allowedMenuIds.contains(menu.getId());
  }
}

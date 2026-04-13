package com.ge.bo.dto;

import com.ge.bo.entity.Menu;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메뉴 응답 DTO (재귀 트리 구조)
 */
public record MenuResponse(
    Long id,
    String name,
    String url,
    String icon,
    Long parentId,
    String menuType,
    Integer sortOrder,
    Boolean visible,
    Boolean isCategory,
    String slug,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MenuResponse> children
) {
    /** 엔티티 → DTO 변환 (재귀) */
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
            menu.getId(),
            menu.getName(),
            menu.getUrl(),
            menu.getIcon(),
            menu.getParent() != null ? menu.getParent().getId() : null,
            menu.getMenuType(),
            menu.getSortOrder(),
            menu.getVisible(),
            menu.getIsCategory(),
            menu.getSlug(),
            menu.getCreatedAt(),
            menu.getUpdatedAt(),
            menu.getChildren() != null
                ? menu.getChildren().stream().map(MenuResponse::from).toList()
                : List.of()
        );
    }
}

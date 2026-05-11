package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.MenuRequest;
import com.ge.bo.dto.MenuResponse;
import com.ge.bo.dto.MenuSortBatchItem;
import com.ge.bo.dto.RoleMenuResponse;
import com.ge.bo.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 메뉴 관리 REST API Controller
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@ApiLinkedEntity("Menu")
public class MenuController {

    private final MenuService menuService;

    /** 타입별 메뉴 트리 조회 */
    @GetMapping
    public ResponseEntity<List<MenuResponse>> getMenuTree(@RequestParam String type) {
        return ResponseEntity.ok(menuService.getMenuTree(type));
    }

    /** 메뉴 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenu(id));
    }

    /** 메뉴 생성 */
    @PostMapping
    public ResponseEntity<MenuResponse> createMenu(@Valid @RequestBody MenuRequest request) {
        MenuResponse response = menuService.createMenu(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 메뉴 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<MenuResponse> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return ResponseEntity.ok(menuService.updateMenu(id, request));
    }

    /** 메뉴 삭제 (하위 포함) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    /** 정렬 순서 변경 */
    @PatchMapping("/{id}/sort")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer sortOrder = body.get("sortOrder");
        if (sortOrder == null || sortOrder < 1 || sortOrder > 999) {
            return ResponseEntity.badRequest().build();
        }
        menuService.updateSortOrder(id, sortOrder);
        return ResponseEntity.ok().build();
    }

    /** 드래그 정렬 일괄 변경 */
    @PatchMapping("/sort-batch")
    public ResponseEntity<Void> updateSortBatch(@RequestBody List<MenuSortBatchItem> items) {
        menuService.updateSortBatch(items);
        return ResponseEntity.ok().build();
    }

    /** 메뉴별 역할 매핑 조회 */
    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleMenuResponse>> getRoleMenuMappings(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getRoleMenuMappings(id));
    }

    /** 역할 매핑 변경 */
    @PutMapping("/{menuId}/roles/{roleId}")
    public ResponseEntity<Void> updateRoleMenuMapping(
            @PathVariable Long menuId,
            @PathVariable Long roleId,
            @RequestBody Map<String, Boolean> body) {
        Boolean hasAccess = body.get("hasAccess");
        if (hasAccess == null) {
            return ResponseEntity.badRequest().build();
        }
        menuService.updateRoleMenuMapping(menuId, roleId, hasAccess);
        return ResponseEntity.ok().build();
    }
}

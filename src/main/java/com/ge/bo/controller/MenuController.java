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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@ApiLinkedEntity("Menu")
public class MenuController {

  private final MenuService menuService;

  @GetMapping
    public ResponseEntity<List<MenuResponse>> getMenuTree(
            @RequestParam String type,
            @RequestParam(defaultValue = "false") boolean forNav,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.getMenuTree(type, siteId, forNav));
  }

  @GetMapping("/{id}")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable Long id) {
    return ResponseEntity.ok(menuService.getMenu(id));
  }

  @PostMapping
    public ResponseEntity<MenuResponse> createMenu(
            @Valid @RequestBody MenuRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    MenuResponse response = menuService.createMenu(request, siteId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{id}")
    public ResponseEntity<MenuResponse> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
    return ResponseEntity.ok(menuService.updateMenu(id, request));
  }

  @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
    menuService.deleteMenu(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/sort")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
    Integer sortOrder = body.get("sortOrder");
    if (sortOrder == null || sortOrder < 1 || sortOrder > 999) {
      return ResponseEntity.badRequest().build();
    }
    menuService.updateSortOrder(id, sortOrder);
    return ResponseEntity.ok().build();
  }

  @PatchMapping("/sort-batch")
    public ResponseEntity<Void> updateSortBatch(@RequestBody List<MenuSortBatchItem> items) {
    menuService.updateSortBatch(items);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleMenuResponse>> getRoleMenuMappings(@PathVariable Long id) {
    return ResponseEntity.ok(menuService.getRoleMenuMappings(id));
  }

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

package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.MenuImportRequest;
import com.ge.bo.dto.MenuImportResponse;
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

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@ApiLinkedEntity("Menu")
public class MenuController {

  private final MenuService menuService;

  @GetMapping
    @PreAuthorize("#forNav or @securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<List<MenuResponse>> getMenuTree(
            @RequestParam String type,
            @RequestParam(defaultValue = "false") boolean forNav,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.getMenuTree(type, siteId, forNav));
  }

  @GetMapping("/site/{siteId}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<List<MenuResponse>> getMenuTreeBySite(
            @PathVariable Long siteId,
            @RequestParam String type) {
    return ResponseEntity.ok(menuService.getMenuTreeForSite(type, siteId));
  }

  @PostMapping("/import")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MenuImportResponse> importMenus(
            @Valid @RequestBody MenuImportRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.importMenus(request, siteId));
  }

  @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MenuResponse> getMenu(
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.getMenu(id, siteId));
  }

  @PostMapping
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MenuResponse> createMenu(
            @Valid @RequestBody MenuRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    MenuResponse response = menuService.createMenu(request, siteId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MenuResponse> updateMenu(
            @PathVariable Long id,
            @Valid @RequestBody MenuRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.updateMenu(id, request, siteId));
  }

  @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> deleteMenu(
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    menuService.deleteMenu(id, siteId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/sort")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateSortOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    Integer sortOrder = body.get("sortOrder");
    if (sortOrder == null || sortOrder < 1 || sortOrder > 999) {
      return ResponseEntity.badRequest().build();
    }
    menuService.updateSortOrder(id, sortOrder, siteId);
    return ResponseEntity.ok().build();
  }

  @PatchMapping("/sort-batch")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateSortBatch(
            @RequestBody List<MenuSortBatchItem> items,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    menuService.updateSortBatch(items, siteId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/roles")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<List<RoleMenuResponse>> getRoleMenuMappings(
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.getRoleMenuMappings(id, siteId));
  }

  @PutMapping("/{menuId}/roles/{roleId}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateRoleMenuMapping(
            @PathVariable Long menuId,
            @PathVariable Long roleId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    Boolean hasAccess = body.get("hasAccess");
    if (hasAccess == null) {
      return ResponseEntity.badRequest().build();
    }
    menuService.updateRoleMenuMapping(menuId, roleId, hasAccess, siteId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/apis")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<List<Long>> getMenuApiMappings(
            @PathVariable Long id,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(menuService.getMenuApiMappings(id, siteId));
  }

  @PutMapping("/{id}/apis")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateMenuApiMapping(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> body,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    List<Long> apiInfoIds = body.get("apiInfoIds");
    menuService.updateMenuApiMapping(id, apiInfoIds, siteId);
    return ResponseEntity.noContent().build();
  }
}

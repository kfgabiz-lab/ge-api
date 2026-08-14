package com.ge.bo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ge.bo.dto.AdminDto;
import com.ge.bo.dto.SiteDto;
import com.ge.bo.service.AdminService;
import com.ge.bo.service.SiteService;

import java.util.List;

import com.ge.bo.annotation.ApiLinkedEntity;

@RestController
@RequestMapping("/api/v1/admins")
@RequiredArgsConstructor
@ApiLinkedEntity("AdminUser")
public class AdminController {

  private final AdminService adminService;
  private final SiteService siteService;

  /**
   * 관리자 전체 목록 조회
   *
   * @return 관리자 응답 DTO 목록
   */
  @GetMapping
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<List<AdminDto.Response>> getAllAdmins() {
    return ResponseEntity.ok(adminService.getAllAdmins());
  }

  /**
   * 관리자 단건 조회
   *
   * @param id 관리자 PK
   * @return 관리자 응답 DTO
   */
  @GetMapping("/{id}")
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminDto.Response> getAdmin(@PathVariable Long id) {
    return ResponseEntity.ok(adminService.getAdminById(id));
  }

  /**
   * 관리자 정보 수정
   *
   * @param id 관리자 PK
   * @param request 수정 요청 DTO
   * @return 수정된 관리자 응답 DTO
   */
  @PatchMapping("/{id}")
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminDto.Response> updateAdmin(
      @PathVariable Long id,
      @Valid @RequestBody AdminDto.UpdateRequest request) {
    return ResponseEntity.ok(adminService.updateAdmin(id, request));
  }

  /**
   * 관리자 활성화/비활성화 상태 변경
   *
   * @param id 관리자 PK
   * @param request 상태 변경 요청 DTO
   * @return 상태가 변경된 관리자 응답 DTO
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminDto.Response> toggleStatus(
      @PathVariable Long id,
      @RequestBody AdminDto.UpdateRequest request) {
    return ResponseEntity.ok(adminService.toggleStatus(id, request.isActive()));
  }

  /**
   * 관리자 삭제
   *
   * @param id 관리자 PK
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<Void> deleteAdmin(@PathVariable Long id) {
    adminService.deleteAdmin(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * 관리자별 매핑된 홈페이지 목록 조회
   *
   * @param id 관리자 PK
   * @return 매핑된 홈페이지 응답 DTO 목록
   */
  @GetMapping("/{id}/sites")
  @PreAuthorize("@securityService.isSelf(authentication, #id) or @securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<List<SiteDto.Response>> getAdminSites(@PathVariable Long id) {
    return ResponseEntity.ok(siteService.getSitesByAdminUser(id));
  }

  /**
   * 관리자 홈페이지 매핑 일괄 변경
   *
   * @param id 관리자 PK
   * @param request 매핑할 홈페이지 ID 목록
   * @return 변경 후 매핑된 홈페이지 응답 DTO 목록
   */
  @PutMapping("/{id}/sites")
  @PreAuthorize("@securityService.isSystemAdmin(authentication) or hasRole('SUPER_ADMIN')")
  public ResponseEntity<List<SiteDto.Response>> updateAdminSites(
      @PathVariable Long id,
      @Valid @RequestBody SiteDto.SiteMappingRequest request) {
    return ResponseEntity.ok(siteService.updateAdminUserSites(id, request));
  }
}

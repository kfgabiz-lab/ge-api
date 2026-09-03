package com.ge.bo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ge.bo.dto.RoleDto;
import com.ge.bo.service.RoleService;

import java.util.List;

import com.ge.bo.annotation.ApiLinkedEntity;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@ApiLinkedEntity("Role")
@PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
public class RoleController {

  private final RoleService roleService;

  /**
   * 전체 역할 목록 조회 (시스템 관리자 전용)
   *
   * @return 역할 응답 DTO 목록
   */
  @GetMapping
  public ResponseEntity<List<RoleDto.Response>> getAllRoles() {
    return ResponseEntity.ok(roleService.getAllRoles());
  }

  /**
   * 배정 가능한 역할 목록 조회 (is_system=false 역할만, 관리자 폼 권한 드롭다운용)
   *
   * @return is_system=false 역할 응답 DTO 목록
   */
  @GetMapping("/assignable")
  public ResponseEntity<List<RoleDto.Response>> getAssignableRoles() {
    return ResponseEntity.ok(roleService.getAssignableRoles());
  }

  /**
   * 역할 단건 조회 (시스템 관리자 전용)
   *
   * @param id 역할 PK
   * @return 역할 응답 DTO
   */
  @GetMapping("/{id}")
  public ResponseEntity<RoleDto.Response> getRoleById(@PathVariable Long id) {
    return ResponseEntity.ok(roleService.getRoleById(id));
  }

  /**
   * 역할 신규 등록 (시스템 관리자 전용)
   *
   * @param request 역할 생성 요청 DTO
   * @return 등록된 역할 응답 DTO
   */
  @PostMapping
  public ResponseEntity<RoleDto.Response> createRole(
      @Valid @RequestBody RoleDto.CreateRequest request) {
    return ResponseEntity.ok(roleService.createRole(request));
  }

  /**
   * 역할 정보 수정 (시스템 관리자 전용)
   *
   * @param id 역할 PK
   * @param request 수정 요청 DTO
   * @return 수정된 역할 응답 DTO
   */
  @PatchMapping("/{id}")
  public ResponseEntity<RoleDto.Response> updateRole(
      @PathVariable Long id,
      @Valid @RequestBody RoleDto.UpdateRequest request) {
    return ResponseEntity.ok(roleService.updateRole(id, request));
  }

  /**
   * 역할 삭제 (시스템 관리자 전용, 시스템 역할 및 사용 중인 역할 삭제 불가)
   *
   * @param id 역할 PK
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
    roleService.deleteRole(id);
    return ResponseEntity.noContent().build();
  }
}

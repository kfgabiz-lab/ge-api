package com.ge.bo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ge.bo.common.util.RoleCodeUtils;
import com.ge.bo.dto.RoleDto;
import com.ge.bo.entity.Role;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.RoleRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository roleRepository;
  private final AdminRepository adminRepository;
  private final MessageResourceService messageResourceService;

  private static final String SYSTEM_ADMIN_CODE = "SYSTEM_ADMIN";

  /**
   * 역할 목록 조회 (전체) — RoleController 클래스 레벨 @PreAuthorize로 시스템관리자만 도달
   *
   * @return 역할 응답 DTO 목록
   */
  @Transactional(readOnly = true)
  public List<RoleDto.Response> getAllRoles() {
    return roleRepository.findByCodeNot(SYSTEM_ADMIN_CODE, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")))
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  /**
   * 배정 가능한 역할 목록 조회 (is_system=false 역할만 반환, 관리자 폼 권한 드롭다운용)
   *
   * @return is_system=false 역할 응답 DTO 목록
   */
  @Transactional(readOnly = true)
  public List<RoleDto.Response> getAssignableRoles() {
    return roleRepository.findByCodeNot(SYSTEM_ADMIN_CODE).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  /**
   * 역할 단건 조회
   *
   * @param id 역할 PK
   * @return 역할 응답 DTO
   */
  @Transactional(readOnly = true)
  public RoleDto.Response getRoleById(Long id) {
    Role role = roleRepository.findByIdAndCodeNot(id, SYSTEM_ADMIN_CODE)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다."));
    return toResponse(role);
  }

  /**
   * 역할 신규 등록
   *
   * @param request 역할 생성 요청 DTO
   * @return 등록된 역할 응답 DTO
   */
  @Transactional
  public RoleDto.Response createRole(RoleDto.CreateRequest request) {
    if (RoleCodeUtils.containsReservedCode(request.getCode())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "RESERVED_ROLE_CODE", "예약된 역할 코드가 포함된 코드는 사용할 수 없습니다.");
    }

    if (roleRepository.existsByCode(request.getCode())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "DUPLICATE_ROLE_CODE", "이미 사용 중인 역할 코드입니다.");
    }

    /* 표시명/표시명 msgKey — 둘 중 하나는 필수 */
    boolean hasMsgKey = request.getDisplayNameMsgKey() != null && !request.getDisplayNameMsgKey().isBlank();
    boolean hasDirectName = request.getDisplayName() != null && !request.getDisplayName().isBlank();
    if (!hasMsgKey && !hasDirectName) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "ROLE_DISPLAY_NAME_REQUIRED", "표시명을 입력해주세요.");
    }

    /* 다국어 모드 ON: msgKey로 ko 값 조회 / OFF: 직접 입력값 사용 */
    String displayNameKo = hasMsgKey
        ? messageResourceService.resolveKo(request.getDisplayNameMsgKey())
        : request.getDisplayName().trim();
    boolean hasDescMsgKey = request.getDescriptionMsgKey() != null && !request.getDescriptionMsgKey().isBlank();
    String descriptionKo = hasDescMsgKey
        ? messageResourceService.resolveKo(request.getDescriptionMsgKey())
        : (request.getDescription() != null ? request.getDescription().trim() : "");

    Role role = Role.builder()
        .code(request.getCode().toUpperCase())
        .displayName(displayNameKo)
        .displayNameMsgKey(hasMsgKey ? request.getDisplayNameMsgKey() : null)
        .description(descriptionKo.isEmpty() ? null : descriptionKo)
        .descriptionMsgKey(hasDescMsgKey ? request.getDescriptionMsgKey() : null)
        .color(request.getColor() != null ? request.getColor() : "#6b7280")
        .isSystem(false)
        .build();

    return toResponse(roleRepository.save(role));
  }

  /**
   * 역할 정보 수정 (표시명, 설명, 색상만 변경 가능)
   *
   * @param id 역할 PK
   * @param request 수정 요청 DTO
   * @return 수정된 역할 응답 DTO
   */
  @Transactional
  public RoleDto.Response updateRole(Long id, RoleDto.UpdateRequest request) {
    Role role = roleRepository.findByIdAndCodeNot(id, SYSTEM_ADMIN_CODE)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다."));

    if (role.isSystem()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SYSTEM_ROLE", "시스템 기본 역할은 수정할 수 없습니다.");
    }
    if ("SUPER_ADMIN".equals(role.getCode())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_ROLE", "최고 관리자 역할은 수정할 수 없습니다.");
    }

    /* 표시명/표시명 msgKey — 둘 중 하나는 필수 */
    boolean hasMsgKey = request.getDisplayNameMsgKey() != null && !request.getDisplayNameMsgKey().isBlank();
    boolean hasDirectName = request.getDisplayName() != null && !request.getDisplayName().isBlank();
    if (!hasMsgKey && !hasDirectName) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "ROLE_DISPLAY_NAME_REQUIRED", "표시명을 입력해주세요.");
    }

    /* 다국어 모드 ON: msgKey로 ko 값 조회 / OFF: 직접 입력값 사용 */
    String displayNameKo = hasMsgKey
        ? messageResourceService.resolveKo(request.getDisplayNameMsgKey())
        : request.getDisplayName().trim();
    boolean hasDescMsgKey = request.getDescriptionMsgKey() != null && !request.getDescriptionMsgKey().isBlank();
    String descriptionKo = hasDescMsgKey
        ? messageResourceService.resolveKo(request.getDescriptionMsgKey())
        : (request.getDescription() != null ? request.getDescription().trim() : "");

    role.setDisplayName(displayNameKo);
    role.setDisplayNameMsgKey(hasMsgKey ? request.getDisplayNameMsgKey() : null);
    role.setDescription(descriptionKo.isEmpty() ? null : descriptionKo);
    role.setDescriptionMsgKey(hasDescMsgKey ? request.getDescriptionMsgKey() : null);
    role.setColor(request.getColor());

    return toResponse(roleRepository.save(role));
  }

  /**
   * 역할 삭제 (시스템 역할 및 사용 중인 역할 삭제 불가)
   *
   * @param id 역할 PK
   */
  @Transactional
  public void deleteRole(Long id) {
    Role role = roleRepository.findByIdAndCodeNot(id, SYSTEM_ADMIN_CODE)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "역할을 찾을 수 없습니다."));

    if (role.isSystem()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SYSTEM_ROLE", "시스템 기본 역할은 삭제할 수 없습니다.");
    }
    if ("SUPER_ADMIN".equals(role.getCode())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_ROLE", "최고 관리자 역할은 삭제할 수 없습니다.");
    }

    long memberCount = adminRepository.countByRole(role.getCode());
    if (memberCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, "ROLE_IN_USE",
          "해당 역할을 사용 중인 관리자가 " + memberCount + "명 있습니다.");
    }

    roleRepository.deleteById(id);
  }

  private RoleDto.Response toResponse(Role role) {
    long memberCount = adminRepository.countByRole(role.getCode());
    return RoleDto.Response.builder()
        .id(role.getId())
        .code(role.getCode())
        .displayName(role.getDisplayName())
        .displayNameMsgKey(role.getDisplayNameMsgKey())
        .description(role.getDescription())
        .descriptionMsgKey(role.getDescriptionMsgKey())
        .color(role.getColor())
        .isSystem(role.isSystem())
        .memberCount(memberCount)
        .build();
  }
}

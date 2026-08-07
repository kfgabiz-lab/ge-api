package com.ge.bo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ge.bo.dto.AdminDto;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.Role;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.RoleRepository;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

  private final AdminRepository adminRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * 관리자 단건 조회 (is_system=true 역할 계정은 존재하지 않는 것처럼 처리)
   *
   * @param id 관리자 PK
   * @return 관리자 응답 DTO
   */
  @Transactional(readOnly = true)
  public AdminDto.Response getAdminById(Long id) {
    AdminUser adminUser = adminRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "관리자를 찾을 수 없습니다."));
    if (isSystemRole(adminUser.getRole())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "관리자를 찾을 수 없습니다.");
    }
    return convertToResponse(adminUser);
  }

  /**
   * 관리자 전체 목록 조회 (is_system=true 역할 계정 제외, 생성일 내림차순)
   *
   * @return 관리자 응답 DTO 목록
   */
  @Transactional(readOnly = true)
  public List<AdminDto.Response> getAllAdmins() {
    /* is_system=true 역할 코드 Set을 한 번만 조회 후 필터 — N+1 방지 */
    Set<String> systemRoleCodes = roleRepository.findAllByOrderByIdAsc().stream()
        .filter(Role::isSystem)
        .map(Role::getCode)
        .collect(Collectors.toSet());

    List<AdminUser> admins = adminRepository.findAll(
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")));
    /* is_system=true 역할 계정은 목록에서 완전 제외 */
    return admins.stream()
        .filter(a -> !systemRoleCodes.contains(a.getRole()))
        .map(this::convertToResponse)
        .collect(Collectors.toList());
  }

  /**
   * 관리자 신규 등록 (임시 비밀번호 자동 생성 후 응답에 포함)
   *
   * @param request 관리자 생성 요청 DTO
   * @return 등록된 관리자 응답 DTO (tempPassword 포함)
   */
  @Transactional
  public AdminDto.Response createAdmin(AdminDto.CreateRequest request) {
    if (!roleRepository.existsByCode(request.getRole()) || isSystemRole(request.getRole())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "유효하지 않은 역할 코드입니다.");
    }
    if (adminRepository.existsByEmail(request.getEmail())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 등록된 아이디입니다.");
    }

    /* 임시 비밀번호 자동 생성 */
    String rawPassword = UUID.randomUUID().toString().substring(0, 12);

    AdminUser adminUser = AdminUser.builder()
        .email(request.getEmail())
        .name(request.getName())
        .deptCode(request.getDeptCode())
        .deptName(request.getDeptName())
        .remark(request.getRemark())
        .passwordHash(passwordEncoder.encode(rawPassword))
        .role(request.getRole())
        .isActive(request.isActive())
        .build();

    AdminUser saved = adminRepository.save(adminUser);
    AdminDto.Response response = convertToResponse(saved);
    response.setTempPassword(rawPassword);

    return response;
  }

  /**
   * 관리자 정보 수정
   *
   * @param id 관리자 PK
   * @param request 수정 요청 DTO
   * @return 수정된 관리자 응답 DTO
   */
  @Transactional
  public AdminDto.Response updateAdmin(Long id, AdminDto.UpdateRequest request) {
    AdminUser adminUser = adminRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "해당 관리자를 찾을 수 없습니다."));

    if (request.getRole() != null
        && (!roleRepository.existsByCode(request.getRole()) || isSystemRole(request.getRole()))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "유효하지 않은 역할 코드입니다.");
    }

    adminUser.setName(request.getName());
    adminUser.setDeptCode(request.getDeptCode());
    adminUser.setDeptName(request.getDeptName());
    adminUser.setRemark(request.getRemark());
    adminUser.setRole(request.getRole());
    adminUser.setActive(request.isActive());
    if (request.isActive()) {
      adminUser.setFailedLoginAttempts(0);
    }

    return convertToResponse(adminRepository.save(adminUser));
  }

  /**
   * 관리자 활성화/비활성화 상태 변경
   *
   * @param id 관리자 PK
   * @param isActive 변경할 활성화 여부
   * @return 상태가 변경된 관리자 응답 DTO
   */
  @Transactional
  public AdminDto.Response toggleStatus(Long id, boolean isActive) {
    AdminUser adminUser = adminRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "해당 관리자를 찾을 수 없습니다."));

    adminUser.setActive(isActive);
    if (isActive) {
      adminUser.setFailedLoginAttempts(0);
    }
    return convertToResponse(adminRepository.save(adminUser));
  }

  /**
   * 관리자 삭제
   *
   * @param id 관리자 PK
   */
  @Transactional
  public void deleteAdmin(Long id) {
    adminRepository.deleteById(id);
  }

  /**
   * 해당 역할 코드가 is_system=true인지 확인
   * role.is_system DB 값 기반으로 판별 (코드 하드코딩 방식 사용 금지)
   */
  private boolean isSystemRole(String roleCode) {
    if (roleCode == null) {
      return false;
    }
    return roleRepository.findByCode(roleCode)
        .map(Role::isSystem)
        .orElse(false);
  }

  private AdminDto.Response convertToResponse(AdminUser user) {
    return AdminDto.Response.builder()
        .id(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .deptCode(user.getDeptCode())
        .deptName(user.getDeptName())
        .remark(user.getRemark())
        .role(user.getRole())
        .isActive(user.isActive())
        .lastLoginAt(user.getLastLoginAt())
        .createdAt(user.getCreatedAt())
        .regDt(user.getRegDt())
        .regTm(user.getRegTm())
        .build();
  }
}

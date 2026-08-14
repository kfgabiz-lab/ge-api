package com.ge.bo.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ge.bo.entity.AdminUser;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<AdminUser, Long> {
  Optional<AdminUser> findByEmail(String email);

  boolean existsByEmail(String email);

  long countByRole(String role);

  List<AdminUser> findByRoleNot(String role, Sort sort);

  Optional<AdminUser> findByIdAndRoleNot(Long id, String role);
}

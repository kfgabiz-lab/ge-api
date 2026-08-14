package com.ge.bo.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ge.bo.entity.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
  boolean existsByCode(String code);
  Optional<Role> findByCode(String code);
  List<Role> findByCodeNot(String code, Sort sort);
  List<Role> findByCodeNot(String code);
  Optional<Role> findByIdAndCodeNot(Long id, String code);
}

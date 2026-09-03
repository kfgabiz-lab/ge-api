package com.ge.bo.repository;

import com.ge.bo.entity.AdminUserSite;
import com.ge.bo.entity.AdminUserSiteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 관리자-홈페이지 매핑 Repository
 */
public interface AdminUserSiteRepository extends JpaRepository<AdminUserSite, AdminUserSiteId> {

    /** 관리자별 매핑된 홈페이지 ID 목록 조회 */
  List<AdminUserSite> findByAdminUserId(Long adminUserId);

    /** 관리자의 전체 매핑 삭제 (일괄 변경 시 사용) */
  void deleteByAdminUserId(Long adminUserId);

  @Query("select min(aus.siteId) from AdminUserSite aus, AdminUser au"
      + " where au.id = aus.adminUserId and au.employeeId = :employeeId")
  Optional<Long> findDefaultSiteIdByEmployeeId(@Param("employeeId") String employeeId);
}

package com.ge.bo.repository;

import com.ge.bo.entity.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 역할-메뉴 매핑 Repository
 */
public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {

    /** 특정 메뉴의 역할 매핑 전체 조회 (사이트 무관) */
  List<RoleMenu> findByMenuId(Long menuId);

    /** 특정 역할의 매핑 전체 조회 (사이트 무관) */
  List<RoleMenu> findByRoleId(Long roleId);

    /** 매핑 삭제 (사이트 무관) */
  void deleteByRoleIdAndMenuId(Long roleId, Long menuId);

  @Query("SELECT COUNT(rm) > 0 FROM RoleMenu rm WHERE rm.roleId = :roleId AND rm.menuId = :menuId"
      + " AND (rm.siteId IS NULL OR rm.siteId = :siteId)")
    boolean existsByRoleIdAndMenuIdAndSite(@Param("roleId") Long roleId,
      @Param("menuId") Long menuId, @Param("siteId") Long siteId);

  @Query("SELECT DISTINCT rm.menuId FROM RoleMenu rm WHERE rm.roleId = :roleId"
      + " AND (rm.siteId IS NULL OR rm.siteId = :siteId)")
    List<Long> findMenuIdsByRoleIdAndSite(@Param("roleId") Long roleId, @Param("siteId") Long siteId);

  @Query("SELECT DISTINCT rm.roleId FROM RoleMenu rm WHERE rm.menuId = :menuId"
      + " AND (rm.siteId IS NULL OR rm.siteId = :siteId)")
    List<Long> findRoleIdsByMenuIdAndSite(@Param("menuId") Long menuId, @Param("siteId") Long siteId);

  List<RoleMenu> findByRoleIdAndMenuIdAndSiteId(Long roleId, Long menuId, Long siteId);

  List<RoleMenu> findByRoleIdAndMenuIdAndSiteIdIsNull(Long roleId, Long menuId);
}

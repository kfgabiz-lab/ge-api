package com.ge.bo.repository;

import com.ge.bo.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 메뉴 Repository
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 타입별 루트 메뉴 전체 조회 */
  @EntityGraph(attributePaths = {"children"})
    List<Menu> findByMenuTypeAndParentIsNullOrderBySortOrderAsc(String menuType);

    /** 네비게이션용: 공통(NULL) + 특정 사이트 메뉴 함께 조회 */
  @EntityGraph(attributePaths = {"children"})
    @Query("SELECT m FROM Menu m WHERE m.menuType = :menuType"
        + " AND m.parent IS NULL AND (m.siteId IS NULL OR m.siteId = :siteId)"
        + " ORDER BY m.sortOrder ASC")
    List<Menu> findNavMenusByTypeAndSite(@Param("menuType") String menuType, @Param("siteId") Long siteId);

    /** 관리용: 특정 사이트 메뉴만 조회 */
  @EntityGraph(attributePaths = {"children"})
    List<Menu> findByMenuTypeAndSiteIdAndParentIsNullOrderBySortOrderAsc(String menuType, Long siteId);

    /** FO GNB용: 공통(NULL) + 특정 사이트, visible=true 루트 메뉴만 조회 (자식도 EntityGraph로 한번에 로드) */
    @EntityGraph(attributePaths = {"children"})
    @Query("SELECT m FROM Menu m WHERE m.menuType = 'FO'"
        + " AND m.parent IS NULL AND m.visible = true"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)"
        + " ORDER BY m.sortOrder ASC")
    List<Menu> findFoGnbRootMenus(@Param("siteId") Long siteId);

    /** 이름 중복 확인 — 생성 시 (같은 부모 + 타입) */
  boolean existsByNameAndParentAndMenuType(String name, Menu parent, String menuType);

    /** 이름 중복 확인 — 수정 시 (자신 제외) */
  boolean existsByNameAndParentAndMenuTypeAndIdNot(String name, Menu parent, String menuType, Long id);

}

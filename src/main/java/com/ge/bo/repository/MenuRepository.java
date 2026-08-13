package com.ge.bo.repository;

import com.ge.bo.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

  @EntityGraph(attributePaths = {"children"})
    List<Menu> findByMenuTypeAndParentIsNullOrderBySortOrderAsc(String menuType);

  @EntityGraph(attributePaths = {"children"})
    @Query("SELECT m FROM Menu m WHERE m.menuType = :menuType"
        + " AND m.parent IS NULL AND (m.siteId IS NULL OR m.siteId = :siteId)"
        + " ORDER BY m.sortOrder ASC")
    List<Menu> findNavMenusByTypeAndSite(@Param("menuType") String menuType, @Param("siteId") Long siteId);

  @EntityGraph(attributePaths = {"children"})
    List<Menu> findByMenuTypeAndSiteIdAndParentIsNullOrderBySortOrderAsc(String menuType, Long siteId);

    @EntityGraph(attributePaths = {"children"})
    @Query("SELECT m FROM Menu m WHERE m.menuType = 'FO'"
        + " AND m.parent IS NULL AND m.visible = true"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)"
        + " ORDER BY m.sortOrder ASC")
    List<Menu> findFoGnbRootMenus(@Param("siteId") Long siteId);

  @Query("SELECT m FROM Menu m WHERE m.menuType = 'FO' AND m.url = :url"
        + " AND m.visible = true"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)")
    Optional<Menu> findFoMenuByUrl(@Param("url") String url, @Param("siteId") Long siteId);

  @Query("SELECT m FROM Menu m WHERE m.menuType = 'BO' AND m.url IS NOT NULL"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)")
    List<Menu> findBoMenusWithUrlBySite(@Param("siteId") Long siteId);

  boolean existsByNameAndParentAndMenuType(String name, Menu parent, String menuType);

  boolean existsByNameAndParentAndMenuTypeAndIdNot(String name, Menu parent, String menuType, Long id);

  List<Menu> findByUrl(String url);

}

package com.ge.bo.repository;

import com.ge.bo.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    @EntityGraph(attributePaths = {"children"})
    @Query("SELECT m FROM Menu m WHERE m.menuType = 'FO'"
        + " AND m.parent IS NULL AND m.visible = true"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)"
        + " ORDER BY m.sortOrder ASC")
    List<Menu> findFoGnbRootMenus(@Param("siteId") Long siteId);

    /** FO 공개 SEO 메타 조회용: URL로 단건 조회 */
  @Query("SELECT m FROM Menu m WHERE m.menuType = 'FO' AND m.url = :url"
        + " AND m.visible = true"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)")
    Optional<Menu> findFoMenuByUrl(@Param("url") String url, @Param("siteId") Long siteId);

    /**
     * 인가 검사용 BO 메뉴 후보 조회 — menu_type='BO'이고 url이 설정된 메뉴 중 공통(site_id NULL) + 특정 사이트 전용 메뉴를 함께 반환한다.
     * 요청 경로와의 최장 접두어 일치 판정은 호출자(AccessAuthorizationService)가 수행한다.
     * (기존 findFoMenuByUrl은 FO 전용 + Optional 단건 반환이라 다건 후보가 나올 수 있는 이 용도에는 부적합해 재사용하지 않음)
     */
  @Query("SELECT m FROM Menu m WHERE m.menuType = 'BO' AND m.url IS NOT NULL"
        + " AND (m.siteId IS NULL OR m.siteId = :siteId)")
    List<Menu> findBoMenusWithUrlBySite(@Param("siteId") Long siteId);

    /** 이름 중복 확인 — 생성 시 (같은 부모 + 타입) */
  boolean existsByNameAndParentAndMenuType(String name, Menu parent, String menuType);

    /** 이름 중복 확인 — 수정 시 (자신 제외) */
  boolean existsByNameAndParentAndMenuTypeAndIdNot(String name, Menu parent, String menuType, Long id);

}

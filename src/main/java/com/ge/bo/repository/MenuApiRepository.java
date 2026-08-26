package com.ge.bo.repository;

import com.ge.bo.entity.MenuApi;
import com.ge.bo.entity.MenuApiId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 메뉴-API 매핑 Repository
 */
public interface MenuApiRepository extends JpaRepository<MenuApi, MenuApiId> {

    /** 특정 메뉴의 API 매핑 전체 조회 */
  List<MenuApi> findByMenuId(Long menuId);

    /** 매핑 삭제 */
  void deleteByMenuIdAndApiInfoId(Long menuId, Long apiInfoId);

    /** 인가 캐시 적재용 — (menuId, method, urlPattern) 전체 조회. 비활성/삭제된 api_info는 제외 */
  @Query(value = "SELECT ma.menu_id AS menuId, ai.method AS method, ai.url_pattern AS urlPattern "
      + "FROM menu_api ma JOIN api_info ai ON ai.id = ma.api_info_id "
      + "WHERE ma.is_deleted = false AND ai.is_deleted = false AND ai.is_active = true",
      nativeQuery = true)
  List<MenuApiAuthProjection> findAllForAuthorizationCache();

  interface MenuApiAuthProjection {
    Long getMenuId();
    String getMethod();
    String getUrlPattern();
  }
}

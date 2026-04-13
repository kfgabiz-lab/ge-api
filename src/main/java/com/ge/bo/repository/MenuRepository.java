package com.ge.bo.repository;

import com.ge.bo.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

/**
 * 메뉴 Repository
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 타입별 루트 메뉴 조회 (fetch join으로 N+1 방지) */
    @EntityGraph(attributePaths = {"children"})
    List<Menu> findByMenuTypeAndParentIsNullOrderBySortOrderAsc(String menuType);

    /** 이름 중복 확인 — 생성 시 (같은 부모 + 타입) */
    boolean existsByNameAndParentAndMenuType(String name, Menu parent, String menuType);

    /** 이름 중복 확인 — 수정 시 (자신 제외) */
    boolean existsByNameAndParentAndMenuTypeAndIdNot(String name, Menu parent, String menuType, Long id);

    /** URL 중복 확인 — 생성 시 */
    boolean existsByUrl(String url);

    /** URL 중복 확인 — 수정 시 (자신 제외) */
    boolean existsByUrlAndIdNot(String url, Long id);

    /** Slug 중복 확인 — 생성 시 */
    boolean existsBySlug(String slug);

    /** Slug 중복 확인 — 수정 시 (자신 제외) */
    boolean existsBySlugAndIdNot(String slug, Long id);
}

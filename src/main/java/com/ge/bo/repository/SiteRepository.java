package com.ge.bo.repository;

import com.ge.bo.entity.Site;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 홈페이지 Repository
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    /** 사용여부 기준 목록 조회 */
  List<Site> findByIsActive(boolean isActive, Sort sort);
}

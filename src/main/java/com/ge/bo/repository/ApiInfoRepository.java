package com.ge.bo.repository;

import com.ge.bo.entity.ApiInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * API 정보 Repository
 * - JpaSpecificationExecutor: category/method/keyword 동적 필터링 지원
 */
public interface ApiInfoRepository extends JpaRepository<ApiInfo, Long>, JpaSpecificationExecutor<ApiInfo> {

    /** method + urlPattern 조합 중복 여부 확인 (동기화 시 사용) */
  boolean existsByMethodAndUrlPattern(String method, String urlPattern);

    /** 활성 API 목록 조회 (빌더 드롭다운용) — name 오름차순 */
  java.util.List<ApiInfo> findAllByActiveTrueOrderByNameAsc();
}

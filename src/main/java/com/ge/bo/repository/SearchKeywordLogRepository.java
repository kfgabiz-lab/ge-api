package com.ge.bo.repository;

import com.ge.bo.entity.SearchKeywordLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FO 검색어 로그 Repository (append-only 저장 전용)
 * - 인기검색어 집계는 윈도우 함수가 필요해 SearchKeywordLogService의 EntityManager 네이티브 쿼리로 처리한다
 */
public interface SearchKeywordLogRepository extends JpaRepository<SearchKeywordLog, Long> {
}

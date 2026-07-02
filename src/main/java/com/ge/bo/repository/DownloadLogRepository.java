package com.ge.bo.repository;

import com.ge.bo.entity.DownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 다운로드 이력 Repository
 * - JpaSpecificationExecutor: 동적 필터링 지원
 */
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long>, JpaSpecificationExecutor<DownloadLog> {
}

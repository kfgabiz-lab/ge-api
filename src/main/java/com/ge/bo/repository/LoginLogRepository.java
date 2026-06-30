package com.ge.bo.repository;

import com.ge.bo.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 접속이력 Repository
 * - JpaSpecificationExecutor: 동적 필터링 지원
 */
public interface LoginLogRepository extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {
}

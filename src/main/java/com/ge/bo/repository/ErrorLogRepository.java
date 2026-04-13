package com.ge.bo.repository;

import com.ge.bo.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 오류로그 Repository
 */
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
}

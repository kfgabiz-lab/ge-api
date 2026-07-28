package com.ge.bo.repository;

import com.ge.bo.entity.ContentsBatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * contents_batch_log(배치 실행 이력) Repository
 */
public interface ContentsBatchLogRepository extends JpaRepository<ContentsBatchLog, Long> {

    boolean existsBySourceSystemAndStatus(String sourceSystem, String status);
}

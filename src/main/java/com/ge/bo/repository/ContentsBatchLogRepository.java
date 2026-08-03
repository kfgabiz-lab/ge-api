package com.ge.bo.repository;

import com.ge.bo.entity.ContentsBatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * contents_batch_log(배치 실행 이력) Repository
 */
public interface ContentsBatchLogRepository extends JpaRepository<ContentsBatchLog, Long> {

    boolean existsBySourceSystemAndStatus(String sourceSystem, String status);

    /** 소스별 가장 최근 배치 이력 — 수동 실행 화면이 새로고침 후에도 마지막 결과를 보여주기 위해 사용 */
    Optional<ContentsBatchLog> findFirstBySourceSystemOrderByBatchIdDesc(String sourceSystem);
}

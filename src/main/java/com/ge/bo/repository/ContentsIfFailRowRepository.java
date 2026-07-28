package com.ge.bo.repository;

import com.ge.bo.entity.ContentsIfFailRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * contents_if_fail_row(적재 실패 행 보관) Repository
 */
public interface ContentsIfFailRowRepository extends JpaRepository<ContentsIfFailRow, Long> {

    List<ContentsIfFailRow> findByBatchId(Long batchId);

    /** R-05 문서 가드 — 이 배치에서 해당 문서가 1건이라도 격리됐는지 확인 */
    boolean existsByBatchIdAndSourceDocKey(Long batchId, String sourceDocKey);
}

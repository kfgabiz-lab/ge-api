package com.ge.bo.repository;

import com.ge.bo.entity.ContentsVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * contents_version(콘텐츠 버전) Repository
 */
public interface ContentsVersionRepository extends JpaRepository<ContentsVersion, Long> {

    List<ContentsVersion> findByContentsId(Long contentsId);

    /** 삭제 후보 조회(문서 내 하위 행 기준) — 이번 배치 도장을 못 받은 행 */
    @Query("select v from ContentsVersion v where v.contentsId = :contentsId "
        + "and v.deleteCheckBatchId < :batchId and v.isDeleted = false")
    List<ContentsVersion> findStale(@Param("contentsId") Long contentsId, @Param("batchId") Long batchId);
}

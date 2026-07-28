package com.ge.bo.repository;

import com.ge.bo.entity.ContentsFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * contents_file(콘텐츠 파일) Repository
 */
public interface ContentsFileRepository extends JpaRepository<ContentsFile, Long> {

    List<ContentsFile> findByContentsVersionId(Long contentsVersionId);

    /** 삭제 후보 조회(버전 내 하위 행 기준) — 이번 배치 도장을 못 받은 행 */
    @Query("select f from ContentsFile f where f.contentsVersionId = :contentsVersionId "
        + "and f.deleteCheckBatchId < :batchId and f.isDeleted = false")
    List<ContentsFile> findStale(@Param("contentsVersionId") Long contentsVersionId, @Param("batchId") Long batchId);
}

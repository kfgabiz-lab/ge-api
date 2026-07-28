package com.ge.bo.repository;

import com.ge.bo.entity.ContentsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * contents_category(콘텐츠 × NAHP 카테고리 등록) Repository
 */
public interface ContentsCategoryRepository extends JpaRepository<ContentsCategory, Long> {

    List<ContentsCategory> findByContentsId(Long contentsId);

    /** 삭제 후보 조회(문서 내 하위 행 기준) — 이번 배치 도장을 못 받은 행 */
    @Query("select c from ContentsCategory c where c.contentsId = :contentsId "
        + "and c.deleteCheckBatchId < :batchId and c.isDeleted = false")
    List<ContentsCategory> findStale(@Param("contentsId") Long contentsId, @Param("batchId") Long batchId);

    /** 미매핑 카테고리 재처리(remap) 대상 조회 — 변환표 보완 후 재시도할 행 */
    @Query("select c from ContentsCategory c where c.sourceSystem = :sourceSystem "
        + "and c.nahpCategoryId is null and c.isDeleted = false")
    List<ContentsCategory> findUnmapped(@Param("sourceSystem") String sourceSystem);

    /**
     * 이미 매핑은 됐지만(nahp_category_id 있음) L1~L3 코드 중 하나라도 아직 없는 행 — 코드가 새로 확정될 때마다
     * (레벨 단위로 순차 확정될 수 있음, 예: L1/L2 먼저 → L3 나중) 소급 반영 대상으로 다시 잡힌다.
     */
    @Query("select c from ContentsCategory c where c.sourceSystem = :sourceSystem and c.nahpCategoryId is not null "
        + "and (c.categoryL1Id is null or c.categoryL2Id is null or c.categoryL3Id is null) and c.isDeleted = false")
    List<ContentsCategory> findMappedWithoutCode(@Param("sourceSystem") String sourceSystem);
}

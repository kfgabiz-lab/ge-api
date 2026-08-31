package com.ge.bo.repository;

import com.ge.bo.entity.SlugRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 슬러그 관계 매핑 Repository
 */
public interface SlugRelationRepository extends JpaRepository<SlugRelation, Long>, JpaSpecificationExecutor<SlugRelation> {

    /** FETCH 방향의 slug 관계 목록 조회 (PageDataService FETCH 처리용) */
    List<SlugRelation> findByMasterSlugAndRelationDir(String masterSlug, String relationDir);

    @Query("SELECT DISTINCT r.masterSlug FROM SlugRelation r WHERE r.relationDir = 'FETCH'")
    List<String> findDistinctMasterSlugByRelationDirFetch();
}

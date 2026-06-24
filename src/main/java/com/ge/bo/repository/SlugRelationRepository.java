package com.ge.bo.repository;

import com.ge.bo.entity.SlugRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 슬러그 관계 매핑 Repository
 */
public interface SlugRelationRepository extends JpaRepository<SlugRelation, Long>, JpaSpecificationExecutor<SlugRelation> {
}

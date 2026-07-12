package com.ge.bo.repository;

import com.ge.bo.entity.SlugEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Slug Entity Repository
 */
public interface SlugEntityRepository
    extends JpaRepository<SlugEntity, Long>, JpaSpecificationExecutor<SlugEntity> {

    /** slug 중복 확인 (등록 시) */
    boolean existsBySlug(String slug);

    /** 빌더용 — active=true 전체 목록, slug ASC 정렬 */
    List<SlugEntity> findAllByActiveTrueOrderBySlugAsc();

    /** 하위(자식) entity 존재 여부 — 삭제 전 확인용 */
    boolean existsByParentEntity_Id(Long parentEntityId);
}

package com.ge.bo.repository;

import com.ge.bo.entity.SlugEntityField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Slug Entity Field Repository
 */
public interface SlugEntityFieldRepository extends JpaRepository<SlugEntityField, Long> {

    /** entity별 필드 목록 조회 (sortOrder ASC) */
    List<SlugEntityField> findAllByEntityIdOrderBySortOrderAsc(Long entityId);

    /** 연동 대상(connected_entity_id) 참조 필드 존재 여부 — 연동 대상 entity 삭제 전 확인용 */
    boolean existsByConnectedEntity_Id(Long connectedEntityId);
}

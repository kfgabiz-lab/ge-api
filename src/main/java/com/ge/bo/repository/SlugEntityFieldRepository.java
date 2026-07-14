package com.ge.bo.repository;

import com.ge.bo.entity.SlugEntityField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Slug Entity Field Repository
 */
public interface SlugEntityFieldRepository extends JpaRepository<SlugEntityField, Long> {

    /** entity별 필드 목록 조회 (sortOrder ASC) */
    List<SlugEntityField> findAllByEntityIdOrderBySortOrderAsc(Long entityId);

    /**
     * slug로 export 대상 entity의 필드 목록을 connectedEntity까지 fetch join으로 함께 로딩한다.
     * CSV export(트랜잭션 밖)에서 FILE/ENTITY_REF 분류 및 connectedEntity.tableName 접근에 사용한다.
     */
    @Query("select f from SlugEntityField f "
        + "left join fetch f.connectedEntity "
        + "where f.entity.slug = :slug "
        + "order by f.sortOrder asc")
    List<SlugEntityField> findAllByEntitySlugFetchConnected(@Param("slug") String slug);

    /** 연동 대상(connected_entity_id) 참조 필드 존재 여부 — 연동 대상 entity 삭제 전 확인용 */
    boolean existsByConnectedEntity_Id(Long connectedEntityId);
}

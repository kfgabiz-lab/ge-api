package com.ge.bo.repository;

import com.ge.bo.entity.TsxGeneration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TSX 생성 이력 JPA Repository
 */
public interface TsxGenerationRepository extends JpaRepository<TsxGeneration, Long> {

    /**
     * templateType 필터 목록 조회 (created_at DESC 정렬은 Pageable로 처리)
     *
     * @param templateType 템플릿 유형 (LIST 또는 LAYER)
     * @param pageable     페이지네이션 + 정렬
     */
    Page<TsxGeneration> findAllByTemplateType(String templateType, Pageable pageable);
}

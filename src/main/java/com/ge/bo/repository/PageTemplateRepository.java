package com.ge.bo.repository;

import com.ge.bo.entity.PageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 페이지 템플릿 레포지토리
 */
public interface PageTemplateRepository extends JpaRepository<PageTemplate, Long> {

    /** 이름 오름차순 전체 조회 */
    List<PageTemplate> findAllByOrderByNameAsc();

    /** 이름 중복 검사 (같은 templateType 안에서만) */
    boolean existsByNameAndTemplateType(String name, String templateType);

    /** slug 중복 검사 (같은 templateType 안에서만) */
    boolean existsBySlugAndTemplateType(String slug, String templateType);

    /** 이름 중복 검사 — 자신 제외, 수정 시 사용 */
    boolean existsByNameAndTemplateTypeAndIdNot(String name, String templateType, Long id);

    /** slug 중복 검사 — 자신 제외, 수정 시 사용 */
    boolean existsBySlugAndTemplateTypeAndIdNot(String slug, String templateType, Long id);

    /** slug로 단건 조회 */
    Optional<PageTemplate> findBySlug(String slug);

    /** slug + type으로 단건 조회 — upsert 판단용 */
    Optional<PageTemplate> findBySlugAndTemplateType(String slug, String templateType);
}

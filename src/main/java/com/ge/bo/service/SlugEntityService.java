package com.ge.bo.service;

import com.ge.bo.dto.SlugEntityFieldRequest;
import com.ge.bo.dto.SlugEntityRequest;
import com.ge.bo.dto.SlugEntityResponse;
import com.ge.bo.entity.SlugEntity;
import com.ge.bo.entity.SlugEntityField;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.SlugEntityRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Slug Entity 서비스
 */
@Service
@RequiredArgsConstructor
public class SlugEntityService {

    private final SlugEntityRepository slugEntityRepository;

    /* ══════════ 목록 조회 (관리 페이지용 — 페이징) ══════════ */

    @Transactional(readOnly = true)
    public Page<SlugEntityResponse> getList(String keyword, Pageable pageable) {
        return slugEntityRepository.findAll(buildSpec(keyword), pageable)
            .map(SlugEntityResponse::fromList);
    }

    /* ══════════ 활성 목록 (빌더용 — 페이징 없음, 필드 포함) ══════════ */

    @Transactional(readOnly = true)
    public List<SlugEntityResponse> getActiveList() {
        return slugEntityRepository.findAllByActiveTrueOrderBySlugAsc()
            .stream().map(SlugEntityResponse::from).toList();
    }

    /* ══════════ 단건 조회 (필드 포함) ══════════ */

    @Transactional(readOnly = true)
    public SlugEntityResponse getOne(Long id) {
        return SlugEntityResponse.from(findOrThrow(id));
    }

    /* ══════════ 등록 ══════════ */

    @Transactional
    public SlugEntityResponse create(SlugEntityRequest request) {
        if (slugEntityRepository.existsBySlug(request.slug())) {
            throw ErrorCode.SLUG_ENTITY_SLUG_DUPLICATE.toException();
        }

        SlugEntity entity = SlugEntity.builder()
            .slug(request.slug().trim())
            .name(request.name().trim())
            .tableName(trimOrNull(request.tableName()))
            .description(trimOrNull(request.description()))
            .active(request.active() != null ? request.active() : true)
            .build();

        return SlugEntityResponse.from(slugEntityRepository.save(entity));
    }

    /* ══════════ 수정 (slug는 변경 불가) ══════════ */

    @Transactional
    public SlugEntityResponse update(Long id, SlugEntityRequest request) {
        SlugEntity entity = findOrThrow(id);

        entity.setName(request.name().trim());
        entity.setTableName(trimOrNull(request.tableName()));
        entity.setDescription(trimOrNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        return SlugEntityResponse.from(entity);
    }

    /* ══════════ 삭제 (하위 필드 CASCADE) ══════════ */

    @Transactional
    public void delete(Long id) {
        slugEntityRepository.delete(findOrThrow(id));
    }

    /* ══════════ 필드 목록 일괄 저장 ══════════ */

    @Transactional
    public SlugEntityResponse saveFields(Long id, List<SlugEntityFieldRequest> fieldRequests) {
        SlugEntity entity = findOrThrow(id);

        /* 기존 필드 전체 제거 (orphanRemoval=true 활용) */
        entity.getFields().clear();

        /* 요청 순서대로 재삽입 — sortOrder는 배열 index 기준 */
        for (int i = 0; i < fieldRequests.size(); i++) {
            SlugEntityFieldRequest req = fieldRequests.get(i);
            SlugEntityField field = SlugEntityField.builder()
                .entity(entity)
                .key(trimOrNull(req.key()))
                .label(req.label().trim())
                .columnType(req.columnType())
                .columnLength(req.columnLength())
                .fieldType(trimOrNull(req.fieldType()))
                .codeGroupCode(trimOrNull(req.codeGroupCode()))
                .isNullable(req.isNullable() != null ? req.isNullable() : true)
                .description(trimOrNull(req.description()))
                .sortOrder(i)
                .build();
            entity.getFields().add(field);
        }

        return SlugEntityResponse.from(slugEntityRepository.save(entity));
    }

    /* ══════════ 헬퍼 ══════════ */

    private SlugEntity findOrThrow(Long id) {
        return slugEntityRepository.findById(id)
            .orElseThrow(ErrorCode.SLUG_ENTITY_NOT_FOUND::toException);
    }

    private String trimOrNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    /**
     * 동적 필터 Specification
     * - keyword: slug 또는 name LIKE %keyword%
     */
    private Specification<SlugEntity> buildSpec(String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                    cb.like(root.get("slug"), like),
                    cb.like(root.get("name"), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

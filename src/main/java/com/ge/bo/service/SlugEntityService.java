package com.ge.bo.service;

import com.ge.bo.dto.SlugEntityFieldRequest;
import com.ge.bo.dto.SlugEntityRequest;
import com.ge.bo.dto.SlugEntityResponse;
import com.ge.bo.entity.SlugEntity;
import com.ge.bo.entity.SlugEntityField;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.SlugEntityFieldRepository;
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
    private final SlugEntityFieldRepository slugEntityFieldRepository;

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

    /* ══════════ 코드 생성용 원본 조회 (SlugEntityCodeGenerator 전달용) ══════════ */

    /**
     * Slug Entity 코드 생성기(SlugEntityCodeGenerator)에 전달할 원본 엔티티를 조회한다.
     * - fields(OneToMany, LAZY)까지 트랜잭션 내에서 로딩되도록 findOrThrow를 재사용한다.
     * - Response DTO로 변환하지 않고 엔티티 그대로 반환한다. (코드 생성은 raw 값이 필요하기 때문)
     */
    @Transactional(readOnly = true)
    public SlugEntity getEntityForCodegen(Long id) {
        SlugEntity entity = findOrThrow(id);
        entity.getFields().size(); // LAZY 컬렉션을 트랜잭션 안에서 강제로 로딩
        return entity;
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
            .parentEntity(resolveParent(request.parentEntityId(), null))
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
        entity.setParentEntity(resolveParent(request.parentEntityId(), id));

        return SlugEntityResponse.from(entity);
    }

    /* ══════════ 삭제 (하위 필드 CASCADE) ══════════ */

    @Transactional
    public void delete(Long id) {
        /* 부모(마스터) 참조 가드 — 하위 entity가 이 entity를 마스터로 지정하고 있으면 삭제 차단 */
        if (slugEntityRepository.existsByParentEntity_Id(id)) {
            throw ErrorCode.SLUG_ENTITY_HAS_CHILDREN.toException();
        }
        /* 연동(ENTITY_REF) 참조 가드 — 다른 entity의 필드가 이 entity를 연동 대상으로 참조하고 있으면 삭제 차단.
           DataIntegrityViolationException(FK 위반) 발생 전에 먼저 막고 친화적 에러 메시지를 반환한다. */
        if (slugEntityFieldRepository.existsByConnectedEntity_Id(id)) {
            throw ErrorCode.SLUG_ENTITY_REFERENCED.toException();
        }
        slugEntityRepository.delete(findOrThrow(id));
    }

    /**
     * 마스터(부모) Entity 참조 해석
     * - parentEntityId가 없으면 null(독립 Entity)
     * - selfId와 동일하면 자기참조 차단
     * - 존재하지 않으면 SLUG_ENTITY_PARENT_NOT_FOUND
     */
    private SlugEntity resolveParent(Long parentEntityId, Long selfId) {
        if (parentEntityId == null) {
            return null;
        }
        if (parentEntityId.equals(selfId)) {
            throw ErrorCode.SLUG_ENTITY_PARENT_SELF.toException();
        }
        return slugEntityRepository.findById(parentEntityId)
            .orElseThrow(ErrorCode.SLUG_ENTITY_PARENT_NOT_FOUND::toException);
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
                .columnName(deriveColumnName(req.key()))
                .columnType(req.columnType())
                .columnLength(req.columnLength())
                .connectedEntity(resolveConnectedEntity(req.connectedEntityId()))
                .fieldType(trimOrNull(req.fieldType()))
                .codeGroupCode(trimOrNull(req.codeGroupCode()))
                .defaultValue(trimOrNull(req.defaultValue()))
                .isNullable(req.isNullable() != null ? req.isNullable() : true)
                .description(trimOrNull(req.description()))
                .sortOrder(i)
                .build();
            entity.getFields().add(field);
        }

        return SlugEntityResponse.from(slugEntityRepository.save(entity));
    }

    /**
     * ENTITY_REF 필드의 연동 대상 Entity 참조 해석
     * - connectedEntityId가 없으면 null(연동 없음)
     * - 존재하지 않으면 SLUG_ENTITY_NOT_FOUND
     * - entity_id(소속)와 달리 소유가 아닌 메타 참조이므로 자기참조 차단(selfId 비교)은 하지 않는다.
     */
    private SlugEntity resolveConnectedEntity(Long connectedEntityId) {
        if (connectedEntityId == null) {
            return null;
        }
        return slugEntityRepository.findById(connectedEntityId)
            .orElseThrow(ErrorCode.SLUG_ENTITY_NOT_FOUND::toException);
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
     * 필드 key(camelCase 가능) → DB 컬럼명(snake_case) 파생. SlugEntityCodeGenerator의 정규화 규칙과 동일하게 맞춘다.
     * (EntityExportFieldResolver가 slug_entity_field.column_name으로 FILE/ENTITY_REF 필드를 식별하는 데 사용)
     */
    private String deriveColumnName(String key) {
        String trimmed = trimOrNull(key);
        if (trimmed == null) {
            return null;
        }
        return SlugEntityCodeGenerator.toSnakeCase(SlugEntityCodeGenerator.toCamelCase(trimmed));
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

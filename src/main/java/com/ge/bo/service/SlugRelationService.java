package com.ge.bo.service;

import com.ge.bo.dto.SlugRelationRequest;
import com.ge.bo.dto.SlugRelationResponse;
import com.ge.bo.entity.SlugRelation;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.SlugRelationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 슬러그 관계 매핑 서비스
 */
@Service
@RequiredArgsConstructor
public class SlugRelationService {

    private final SlugRelationRepository slugRelationRepository;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     *
     * @param masterSlug  master slug 필터 (null이면 전체)
     * @param slaveSlug   slave slug 필터 (null이면 전체)
     * @param relationDir 방향 필터 (null이면 전체)
     * @param pageable    페이지 정보
     */
    @Transactional(readOnly = true)
    public Page<SlugRelationResponse> getList(String masterSlug, String slaveSlug, String relationDir, Pageable pageable) {
        Specification<SlugRelation> spec = buildSpec(masterSlug, slaveSlug, relationDir);
        return slugRelationRepository.findAll(spec, pageable).map(SlugRelationResponse::from);
    }

    /* ══════════ 단건 조회 ══════════ */

    @Transactional(readOnly = true)
    public SlugRelationResponse getOne(Long id) {
        return SlugRelationResponse.from(findOrThrow(id));
    }

    /* ══════════ 등록 ══════════ */

    @Transactional
    public SlugRelationResponse create(SlugRelationRequest request) {
        SlugRelation entity = SlugRelation.builder()
                .masterSlug(request.masterSlug().trim())
                .slaveSlug(request.slaveSlug().trim())
                .masterKey(StringUtils.hasText(request.masterKey()) ? request.masterKey().trim() : "id")
                .slaveKey(request.slaveKey().trim())
                .joinType(StringUtils.hasText(request.joinType()) ? request.joinType().trim() : "EQ")
                .slaveFilter(trimOrNull(request.slaveFilter()))
                .relationDir(StringUtils.hasText(request.relationDir()) ? request.relationDir().trim() : "FILTER")
                .fetchFields(trimOrNull(request.fetchFields()))
                .fetchSeparator(request.fetchSeparator() != null ? request.fetchSeparator() : "")
                .slaveType(StringUtils.hasText(request.slaveType()) ? request.slaveType().trim() : "TABLE")
                .categoryDepth(request.categoryDepth() != null ? request.categoryDepth() : 1)
                .categoryDepthFrom(request.categoryDepthFrom())
                .description(trimOrNull(request.description()))
                .build();
        return SlugRelationResponse.from(slugRelationRepository.save(entity));
    }

    /* ══════════ 수정 ══════════ */

    @Transactional
    public SlugRelationResponse update(Long id, SlugRelationRequest request) {
        SlugRelation entity = findOrThrow(id);
        entity.setMasterSlug(request.masterSlug().trim());
        entity.setSlaveSlug(request.slaveSlug().trim());
        entity.setMasterKey(StringUtils.hasText(request.masterKey()) ? request.masterKey().trim() : "id");
        entity.setSlaveKey(request.slaveKey().trim());
        entity.setJoinType(StringUtils.hasText(request.joinType()) ? request.joinType().trim() : "EQ");
        entity.setSlaveFilter(trimOrNull(request.slaveFilter()));
        entity.setRelationDir(StringUtils.hasText(request.relationDir()) ? request.relationDir().trim() : "FILTER");
        entity.setFetchFields(trimOrNull(request.fetchFields()));
        entity.setFetchSeparator(request.fetchSeparator() != null ? request.fetchSeparator() : "");
        entity.setSlaveType(StringUtils.hasText(request.slaveType()) ? request.slaveType().trim() : "TABLE");
        entity.setCategoryDepth(request.categoryDepth() != null ? request.categoryDepth() : 1);
        entity.setCategoryDepthFrom(request.categoryDepthFrom());
        entity.setDescription(trimOrNull(request.description()));
        return SlugRelationResponse.from(slugRelationRepository.save(entity));
    }

    /* ══════════ 삭제 ══════════ */

    @Transactional
    public void delete(Long id) {
        if (!slugRelationRepository.existsById(id)) {
            throw ErrorCode.SLUG_RELATION_NOT_FOUND.toException();
        }
        slugRelationRepository.deleteById(id);
    }

    /* ══════════ 내부 유틸 ══════════ */

    private SlugRelation findOrThrow(Long id) {
        return slugRelationRepository.findById(id)
                .orElseThrow(ErrorCode.SLUG_RELATION_NOT_FOUND::toException);
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 동적 WHERE 조건 생성 */
    private Specification<SlugRelation> buildSpec(String masterSlug, String slaveSlug, String relationDir) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(masterSlug)) {
                predicates.add(cb.like(cb.lower(root.get("masterSlug")), "%" + masterSlug.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(slaveSlug)) {
                predicates.add(cb.like(cb.lower(root.get("slaveSlug")), "%" + slaveSlug.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(relationDir)) {
                predicates.add(cb.equal(root.get("relationDir"), relationDir.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

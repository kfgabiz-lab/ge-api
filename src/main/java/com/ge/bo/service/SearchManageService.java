package com.ge.bo.service;

import com.ge.bo.dto.SearchManageRequest;
import com.ge.bo.dto.SearchManageResponse;
import com.ge.bo.dto.SearchManageTextRequest;
import com.ge.bo.entity.SearchManage;
import com.ge.bo.entity.SearchManageText;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.SearchManageRepository;
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
 * 검색관리 서비스
 */
@Service
@RequiredArgsConstructor
public class SearchManageService {

    private final SearchManageRepository searchManageRepository;

    /* ══════════ 목록 조회 (url 필터 + 페이징) ══════════ */

    @Transactional(readOnly = true)
    public Page<SearchManageResponse> getList(String url, Pageable pageable) {
        return searchManageRepository.findAll(buildSpec(url), pageable)
            .map(SearchManageResponse::fromList);
    }

    /* ══════════ 단건 조회 (검색텍스트 포함, 최신순) ══════════ */

    @Transactional(readOnly = true)
    public SearchManageResponse getOne(Long id) {
        return SearchManageResponse.from(findOrThrow(id));
    }

    /* ══════════ 등록 ══════════ */

    @Transactional
    public SearchManageResponse create(SearchManageRequest request) {
        SearchManage entity = SearchManage.builder()
            .url(request.url().trim())
            .active(request.active() != null ? request.active() : true)
            .build();

        return SearchManageResponse.from(searchManageRepository.save(entity));
    }

    /* ══════════ 수정 ══════════ */

    @Transactional
    public SearchManageResponse update(Long id, SearchManageRequest request) {
        SearchManage entity = findOrThrow(id);

        entity.setUrl(request.url().trim());
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        return SearchManageResponse.from(entity);
    }

    /* ══════════ 삭제 (하위 검색텍스트 CASCADE) ══════════ */

    @Transactional
    public void delete(Long id) {
        searchManageRepository.delete(findOrThrow(id));
    }

    /* ══════════ 검색텍스트 등록 — 최신순 정렬이라 조회 시 자동으로 맨 위에 노출 ══════════ */

    @Transactional
    public SearchManageResponse addText(Long id, SearchManageTextRequest request) {
        SearchManage entity = findOrThrow(id);

        SearchManageText text = SearchManageText.builder()
            .searchManage(entity)
            .text(request.text().trim())
            .build();
        entity.getTexts().add(text);

        return SearchManageResponse.from(searchManageRepository.save(entity));
    }

    /* ══════════ 검색텍스트 삭제 ══════════ */

    @Transactional
    public SearchManageResponse deleteText(Long id, Long textId) {
        SearchManage entity = findOrThrow(id);

        SearchManageText target = entity.getTexts().stream()
            .filter(t -> t.getId().equals(textId))
            .findFirst()
            .orElseThrow(ErrorCode.SEARCH_MANAGE_TEXT_NOT_FOUND::toException);

        entity.getTexts().remove(target);

        return SearchManageResponse.from(searchManageRepository.save(entity));
    }

    /* ══════════ 헬퍼 ══════════ */

    private SearchManage findOrThrow(Long id) {
        return searchManageRepository.findById(id)
            .orElseThrow(ErrorCode.SEARCH_MANAGE_NOT_FOUND::toException);
    }

    /**
     * 동적 필터 Specification
     * - url: LIKE %url%
     */
    private Specification<SearchManage> buildSpec(String url) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (url != null && !url.isBlank()) {
                predicates.add(cb.like(root.get("url"), "%" + url.trim() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

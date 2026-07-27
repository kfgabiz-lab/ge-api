package com.ge.bo.dto;

import java.util.List;

/**
 * FO Tech Hub 목록 페이징 응답(Spring Data Page 형태와 동일 필드 — FE 기존 PageResult 컨벤션 재사용)
 *
 * @param content       현재 페이지 카드 목록
 * @param totalElements 필터/검색 적용 후 전체 콘텐츠 수(카테고리 아코디언/Total 표시용)
 * @param totalPages    전체 페이지 수
 * @param page          현재 페이지(0-based)
 * @param size          페이지 크기(기본 12)
 */
public record TechHubContentPageResponse(
        List<TechHubContentResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}

package com.ge.bo.dto;

import java.util.List;

/**
 * FO 통합 미디어 검색 페이징 응답(Spring Data Page 형태와 동일 필드 — FE 기존 PageResult 컨벤션 재사용)
 *
 * @param content       현재 페이지 결과 목록(4개 소스 UNION 후 sort_date DESC 정렬)
 * @param totalElements 검색/소스필터 적용 후 전체 매칭 건수
 * @param totalPages    전체 페이지 수(ceil(totalElements/size))
 * @param page          현재 페이지(0-based)
 * @param size          페이지 크기(기본 20)
 */
public record MediaSearchResponse(
        List<MediaSearchItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}

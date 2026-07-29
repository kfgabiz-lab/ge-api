package com.ge.bo.dto;

import java.util.List;

/**
 * FO 통합검색 Pages 탭 페이징 응답(Spring Data Page 형태와 동일 필드 — FE 기존 PageResult 컨벤션 재사용).
 *
 * <p>Media 응답과 달리 sourceCounts 가 없다. Pages 는 단일 타입이라 소스별 집계가 필요 없기 때문이다.</p>
 *
 * @param content       현재 페이지 결과 목록(updated_at DESC, id DESC 정렬)
 * @param totalElements 전체 건수(COUNT(*) OVER() 로 목록 쿼리와 함께 조회)
 * @param totalPages    전체 페이지 수(ceil(totalElements/size))
 * @param page          현재 페이지(0-based)
 * @param size          페이지 크기(기본 10)
 */
public record PageSearchResponse(
        List<PageSearchItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}

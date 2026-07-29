package com.ge.bo.dto;

import java.util.List;
import java.util.Map;

/**
 * FO 통합 미디어 검색 페이징 응답(Spring Data Page 형태와 동일 필드 + sourceCounts — FE 기존 PageResult 컨벤션 재사용)
 *
 * @param content       현재 페이지 결과 목록(4개 소스 UNION 후 sort_ts DESC 정렬)
 * @param totalElements 선택된 소스들의 건수 합(= sourceCounts 중 선택 소스 합계)
 * @param totalPages    전체 페이지 수(ceil(totalElements/size))
 * @param page          현재 페이지(0-based)
 * @param size          페이지 크기(기본 20)
 * @param sourceCounts  소스별 검색 건수 — TECH_HUB/BLOG/PRESS/ARTICLE 4키를 항상 포함(0건도 0으로 채움, LinkedHashMap 순서 고정).
 *                      키워드(q)만 적용하고 소스 필터는 적용하지 않은 값이라, 소스 선택을 바꿔도 값이 유지되어 FE 필터 UI("Article (0)")가 정상 동작한다.
 */
public record MediaSearchResponse(
        List<MediaSearchItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        Map<String, Long> sourceCounts
) {}

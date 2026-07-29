package com.ge.bo.dto;

import java.util.List;
import java.util.Map;

/**
 * FO 통합검색 Pages 탭 페이징 응답(Spring Data Page 형태와 동일 필드 + sectionCounts — Media 의 sourceCounts 컨벤션 재사용).
 *
 * @param content       현재 페이지 결과 목록(URL 중복 제거 후, sections 필터 적용, search_manage.updated_at DESC · id DESC 정렬)
 * @param totalElements 전체 건수 — <b>URL 중복 제거 후</b> 기준(매칭된 검색텍스트 건수가 아님), sections 필터가 지정되면 그 필터까지 반영된 값.
 * @param totalPages    전체 페이지 수(ceil(totalElements/size))
 * @param page          현재 페이지(0-based)
 * @param size          페이지 크기(기본 10)
 * @param sectionCounts 분류(page_section)별 검색 건수 — code_group.group_code='PAGE_SECTION' 의 활성 코드를 sort_order 순으로 모두 포함(0건도 0으로, LinkedHashMap 순서 고정).
 *                      키워드(q)만 적용하고 sections 필터는 적용하지 않은 값이라, 분류 선택을 바꿔도 값이 유지되어 FE 필터 UI(분류별 건수 표시)가 정상 동작한다.
 *                      집계는 목록과 동일하게 <b>URL 중복 제거 후</b> 기준이다. 단, page_section 이 NULL 인 행은 어느 분류 count 에도 잡히지 않으므로
 *                      sections 미지정 시 totalElements(=전체 목록 건수)와 sectionCounts 의 합계가 다를 수 있다(NULL 분류 행이 존재하는 경우).
 */
public record PageSearchResponse(
        List<PageSearchItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        Map<String, Long> sectionCounts
) {}

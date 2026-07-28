package com.ge.bo.dto;

import java.util.List;

/**
 * FO 문서(Download Center) 키워드 검색 전용 응답 DTO
 * - contents_master 중 노출 게이트(expose + 노출 버전/파일, 영상 doc_type='V' 제외)를 통과한 문서 중
 *   제목(nahp_title 우선, 없으면 doc_title)이 키워드와 부분일치하는 문서 목록을 표현한다.
 * - total: 키워드에 매칭되는 전체 건수(반환 limit과 별개 — "99+" 등 전체 건수 표기용).
 * - items: 실제로 반환하는 상위 limit 건. 문서 카드 1건은 기존 DownloadCenterContentResponse
 *   (버전/파일 중첩 포함)를 그대로 재사용한다.
 *
 * @param total 키워드 매칭 전체 건수(limit 무관)
 * @param items 상위 limit 건의 문서 목록(버전/파일 중첩 포함)
 */
public record FoDocumentSearchResponse(
        long total,
        List<DownloadCenterContentResponse> items
) {}

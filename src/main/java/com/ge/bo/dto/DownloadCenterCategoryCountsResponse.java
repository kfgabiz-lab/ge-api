package com.ge.bo.dto;

import java.util.List;

/**
 * FO Download Center 카테고리별 콘텐츠 건수 응답 — LV1 합계 + LV2(그룹 내) 세부 건수를 함께 내려준다.
 * - 문서 1건은 항상 대표 카테고리(LV1/LV2) 1개로만 집계되므로(다중 카테고리 소속이어도 대표 1개로 통일),
 *   l1Counts 합계 = l2Counts 합계 = 해당 필터 기준 total 과 항상 일치한다.
 *
 * @param l1Counts LV1별 전체 건수(하위 LV2 합산이 아니라 대표 카테고리가 해당 LV1인 문서 수를 직접 집계)
 * @param l2Counts 기존 DownloadCenterCategoryCountResponse 구조(LV1+LV2 조합별 건수, LV2 미지정 대표는 categoryL2Id=null)
 */
public record DownloadCenterCategoryCountsResponse(
        List<DownloadCenterL1CategoryCountResponse> l1Counts,
        List<DownloadCenterCategoryCountResponse> l2Counts
) {}

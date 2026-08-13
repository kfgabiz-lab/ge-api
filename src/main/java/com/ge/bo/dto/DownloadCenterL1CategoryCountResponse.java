package com.ge.bo.dto;

/**
 * FO Download Center LV1 카테고리별 콘텐츠 건수 — 대표 카테고리 기준 집계(하위 LV2 합산 아님).
 * - 문서 1건이 여러 카테고리에 걸쳐 있어도 대표 카테고리(LV1/LV2) 1개로만 집계되므로,
 *   이 응답의 count 합계는 항상 해당 필터 기준 total 과 일치한다.
 *
 * @param categoryL1Id LV1 카테고리 코드
 * @param count        해당 LV1이 대표 카테고리인 노출 콘텐츠(master) 수
 */
public record DownloadCenterL1CategoryCountResponse(
        String categoryL1Id,
        int count
) {}

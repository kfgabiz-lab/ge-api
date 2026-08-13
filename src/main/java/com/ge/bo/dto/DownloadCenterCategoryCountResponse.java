package com.ge.bo.dto;

/**
 * FO Download Center 카테고리(LV2)별 콘텐츠 건수 — 필터 아코디언 옆 숫자 표시용.
 * - LV1>LV2 계층 자체는 FE가 category-data로 구성하고, 각 LV2의 건수를 이 응답과 병합한다.
 * - 여기 없는 LV2는 콘텐츠 0건(FE에서 0으로 표시).
 * - 문서 1건이 여러 카테고리에 걸쳐 있어도 대표 카테고리(LV1/LV2) 1개로만 집계된다(중복 가산 없음).
 * - categoryL2Id 가 null 인 행 = 대표 카테고리가 해당 LV1 이고 LV2 는 지정되지 않은 콘텐츠(LV1-only) 버킷.
 *
 * @param categoryL1Id LV1 카테고리 코드
 * @param categoryL2Id LV2 카테고리 코드
 * @param count        대표 카테고리가 이 LV1/LV2 조합인 노출 콘텐츠(master) 수
 */
public record DownloadCenterCategoryCountResponse(
        String categoryL1Id,
        String categoryL2Id,
        int count
) {}

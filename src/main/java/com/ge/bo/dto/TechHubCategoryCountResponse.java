package com.ge.bo.dto;

/**
 * FO Tech Hub 카테고리(LV2)별 콘텐츠 건수 — 필터 아코디언 옆 숫자(tech2.png) 표시용.
 * - LV1>LV2 계층 자체는 FE가 category-data(devices-tree 패턴)로 구성하고, 각 LV2의 건수를 이 응답과 병합한다.
 * - 여기 없는 LV2는 콘텐츠 0건(FE에서 0으로 표시).
 *
 * @param categoryL2Id LV2 카테고리 코드(category-data category.code depth2)
 * @param count        해당 LV2의 노출 영상 콘텐츠(master) 수
 */
public record TechHubCategoryCountResponse(
        String categoryL2Id,
        int count
) {}

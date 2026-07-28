package com.ge.bo.batch.contents;

import lombok.Builder;
import lombok.Getter;

/**
 * 원천 공통 카테고리 모델 — contents_category 1행에 대응
 */
@Getter
@Builder
public class CategoryItem {
    /** 소스가 보낸 카테고리 위치 원본 표현(trim 후 구분자로 조합). 등록의 정체성 기준 */
    private final String sourcePath;
    private final String nahpCategoryId;
    /**
     * 레벨별 NAHP 카테고리 코드(L01, L01-01 체계) — NAHP는 3단계 고정이라 L4는 없음.
     * page_data(category-data)에 아직 3단계(소분류) 코드가 등록되지 않아(2026-07-24 기준) 당분간
     * 소스 불문 L3는 null — 3단계가 등록되면 그때 채우면 됨.
     */
    private final String categoryL1Id;
    private final String categoryL2Id;
    private final String categoryL3Id;
    private final Integer nahpLevelSeq;
    private final boolean nahpDisplayFlag;
}

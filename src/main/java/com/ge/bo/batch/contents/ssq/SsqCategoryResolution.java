package com.ge.bo.batch.contents.ssq;

/**
 * SsqCategoryMapping 조회 결과 — nahp_category_id(이름 조합)와 레벨별 코드(L1/L2, 2026-07-24부터 반영됨)를 함께 담는다.
 * NAHP 카테고리는 3단계 고정이라 필드는 L1~L3까지 있지만, page_data(category-data)에 아직 3단계(소분류) 코드가
 * 등록되지 않아 categoryL3Id는 당분간 null이다 — 3단계가 등록되면 채우면 된다.
 */
public record SsqCategoryResolution(String nahpCategoryId, String categoryL1Id, String categoryL2Id, String categoryL3Id) {
}

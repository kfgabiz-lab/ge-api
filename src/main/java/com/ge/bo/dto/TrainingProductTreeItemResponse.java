package com.ge.bo.dto;

/**
 * 트레이닝 제품 트리 "평면 행" 1건 — Lv1 > Lv2 > (depth3 연결행) > 제품 을 한 줄로 펼친 형태.
 *
 * <p>power/automation 계층 응답만으로는 표현할 수 없는 두 가지 용도를 위해 함께 내려준다.
 * <ul>
 *   <li>categoryId(= category-data depth3 연결행 PK)는 커리큘럼 상세(currDtlMgmt-data)의
 *       power_list / automation_list 에 저장되는 값이다. FO 는 이 값으로
 *       "연결제품 id → 제품명" 을 해석하고, curriculum-by-category 의 categoryIds 파라미터를 만든다.</li>
 *   <li>Lv1/Lv2 타이틀이 함께 있어 FO 커리큘럼 목록의 Lv/Sub Category 필터 옵션을
 *       관계설정(_fetchedRel*) 의존 없이 이 응답만으로 파생할 수 있다.</li>
 * </ul>
 * 기존 power/automation 필드는 그대로 유지되므로 Training Request(Step4) 화면에는 영향이 없다.
 */
public record TrainingProductTreeItemResponse(
        /** category-data depth3 연결행 PK — currDtlMgmt-data.power_list/automation_list 에 저장되는 값 */
        Long categoryId,
        /** Lv1 카테고리 PK */
        Long lv1Id,
        /** Lv1 카테고리 명 (Power 기준 Lv Category 옵션) */
        String lv1Title,
        /** Lv2 카테고리 PK */
        Long lv2Id,
        /** Lv2 카테고리 명 (Power 기준 Sub Category / Automation 기준 Lv Category 옵션) */
        String lv2Title,
        /** product-data PK */
        Long productId,
        /** 제품명 (Automation 기준 Sub Category 옵션 / PRODUCTS COVERED 표기값) */
        String productName,
        /** 제품 분류 코드 "P"=Power / "A"=Automation */
        String productType) {
}

package com.ge.bo.dto;

import java.util.List;

/**
 * 트레이닝 요청(Step4) 제품 선택 트리 노드 — 하위 카테고리가 있으면 children, 없고 제품만 있으면 products.
 * FE는 children이 있으면 드롭다운으로, products만 있으면 체크박스로 렌더링한다.
 */
public record TrainingProductNodeResponse(
        Long id,
        String title,
        List<TrainingProductNodeResponse> children,
        List<TrainingProductOptionResponse> products) {
}

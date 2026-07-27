package com.ge.bo.dto;

import java.util.List;

/**
 * 트레이닝 요청(Step4) 제품 선택 노드 — 드롭다운2의 옵션 1개(id/title)와 그 하위 체크박스 목록(products).
 * Power는 products가 Lv2 카테고리 목록, Automation은 products가 실제 제품 목록이다.
 */
public record TrainingProductNodeResponse(
        Long id,
        String title,
        List<TrainingProductOptionResponse> products) {
}

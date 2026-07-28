package com.ge.bo.dto;

import java.util.List;

/**
 * 트레이닝 제품 트리 — Power/Automation 각각의 제품 선택 트리(트레이닝 사용여부 활성 제품만, 빈 가지 제외).
 *
 * <p>power/automation : Training Request(Step4) 제품 선택 드롭다운/체크박스용 계층 구조(기존 그대로).
 * <p>items            : 같은 조회 결과의 평면 행 목록. FO 커리큘럼 목록의 Lv/Sub Category 필터 옵션 파생과
 *                       연결제품 id(category-data depth3 PK) → 제품명 해석에 사용한다.
 */
public record TrainingProductTreeResponse(
        List<TrainingProductNodeResponse> power,
        List<TrainingProductNodeResponse> automation,
        List<TrainingProductTreeItemResponse> items) {
}

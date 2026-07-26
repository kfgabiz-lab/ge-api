package com.ge.bo.dto;

import java.util.List;

/** 트레이닝 요청(Step4) — Power/Automation 각각의 제품 선택 트리(트레이닝 사용여부 활성 제품만, 빈 가지 제외) */
public record TrainingProductTreeResponse(
        List<TrainingProductNodeResponse> power,
        List<TrainingProductNodeResponse> automation) {
}

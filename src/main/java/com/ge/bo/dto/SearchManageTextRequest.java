package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 검색관리 하위 검색텍스트 등록 요청 DTO
 *
 * <p>title 은 나중에 추가된 선택 항목이다. 기존 BO 화면은 아직 title 을 보내지 않으므로
 * {@code @NotBlank} 를 붙이면 기존 등록이 전부 깨진다 → 길이 제한만 검증한다.
 * 미전송(JSON 키 없음) 시 Jackson 이 null 을 넣어 그대로 동작한다(하위호환).</p>
 */
public record SearchManageTextRequest(
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요.")
    String title,

    @NotBlank(message = "검색텍스트를 입력해주세요.")
    String text
) {}

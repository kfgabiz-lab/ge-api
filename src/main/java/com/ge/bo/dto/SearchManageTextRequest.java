package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 검색관리 하위 검색텍스트 등록 요청 DTO
 */
public record SearchManageTextRequest(
    @NotBlank(message = "검색텍스트를 입력해주세요.")
    String text
) {}

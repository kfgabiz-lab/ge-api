package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 검색관리 등록/수정 요청 DTO
 */
public record SearchManageRequest(
    @NotBlank(message = "URL을 입력해주세요.")
    @Size(max = 500, message = "URL은 500자 이하로 입력해주세요.")
    String url,

    Boolean active
) {}

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

    Boolean active,

    /** 분류(페이지 섹션) — code_detail(group_code='PAGE_SECTION') 코드값. 선택 입력. */
    @Size(max = 30, message = "분류 코드는 30자 이하로 입력해주세요.")
    String pageSection
) {}

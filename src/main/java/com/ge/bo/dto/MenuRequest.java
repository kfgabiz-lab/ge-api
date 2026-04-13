package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * 메뉴 생성/수정 요청 DTO
 */
public record MenuRequest(
    @NotBlank(message = "메뉴명을 입력해주세요.")
    @Size(max = 50, message = "메뉴명은 50자 이하로 입력해주세요.")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s\\-_()]{1,50}$",
             message = "메뉴명은 한글, 영문, 숫자, 공백, -, _, ()만 사용 가능합니다.")
    String name,

    @Size(max = 200, message = "URL은 200자 이하로 입력해주세요.")
    @Pattern(regexp = "^$|^/[a-zA-Z0-9\\-_/]*$",
             message = "URL은 /로 시작하는 경로를 입력해주세요.")
    String url,

    @NotBlank(message = "아이콘을 선택해주세요.")
    String icon,

    Long parentId,

    @NotBlank(message = "메뉴 구분을 선택해주세요.")
    @Pattern(regexp = "^(BO|FO)$", message = "메뉴 구분은 BO 또는 FO만 가능합니다.")
    String menuType,

    @NotNull(message = "정렬 순서를 입력해주세요.")
    @Min(value = 1, message = "정렬 순서는 1 이상이어야 합니다.")
    @Max(value = 999, message = "정렬 순서는 999 이하여야 합니다.")
    Integer sortOrder,

    Boolean visible,

    Boolean isCategory,

    @Size(max = 100, message = "슬러그는 100자 이하로 입력해주세요.")
    @Pattern(regexp = "^$|^[a-zA-Z0-9\\-_]+$",
             message = "슬러그는 영문, 숫자, -, _만 사용 가능합니다.")
    String slug
) {}

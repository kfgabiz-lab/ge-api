package com.ge.bo.dto;

import jakarta.validation.constraints.*;

public record CodeDetailRequest(
    @NotBlank(message = "코드값을 입력해주세요.")
    @Size(max = 30) @Pattern(regexp = "^[A-Z0-9_]+$", message = "영문 대문자, 숫자, _만 사용 가능합니다.")
    String code,

    /* msgKey가 있으면 BE에서 ko 텍스트를 채우므로 nullable */
    @Size(max = 100, message = "코드명은 100자 이하로 입력해주세요.")
    String name,

    /* 다국어 키 (선택) — message_resource.key */
    @Size(max = 255)
    String nameMsgKey,

    @Size(max = 200) String description,

    @NotNull(message = "정렬순서를 입력해주세요.")
    @Min(value = 1, message = "정렬순서는 1 이상이어야 합니다.")
    @Max(value = 999, message = "정렬순서는 999 이하여야 합니다.")
    Integer sortOrder,

    Boolean active,

    /* 기타 항목 1~5 (선택값) */
    @Size(max = 100) String extra1,
    @Size(max = 100) String extra2,
    @Size(max = 100) String extra3,
    @Size(max = 100) String extra4,
    @Size(max = 100) String extra5
) {}

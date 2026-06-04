package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * 메뉴 생성/수정 요청 DTO
 * 다국어 모드 ON  → nameMsgKey 전달 (message_resource 키)
 * 다국어 모드 OFF → name 직접 입력 전달
 * 두 값 중 하나는 반드시 있어야 하며, 서비스 레이어에서 검증한다.
 */
public record MenuRequest(

    /** 다국어 모드 OFF 시 직접 입력한 메뉴명 */
    @Size(max = 50, message = "메뉴명은 50자 이하여야 합니다.")
    String name,

    /** 메뉴명 다국어 키 — message_resource.key (WORD 타입) */
    String nameMsgKey,

    /** 다국어 모드 OFF 시 직접 입력한 메뉴 설명 (선택) */
    @Size(max = 500, message = "메뉴 설명은 500자 이하여야 합니다.")
    String description,

    /** 메뉴 설명 다국어 키 — message_resource.key (WORD/SENTENCE 타입, 선택) */
    String descriptionMsgKey,

    @Size(max = 200, message = "URL은 200자 이하로 입력해주세요.")
    @Pattern(regexp = "^$|^/[a-zA-Z0-9\\-_/]*$",
             message = "URL은 /로 시작하는 경로를 입력해주세요.")
    String url,

    @Size(max = 30, message = "아이콘명은 30자 이하여야 합니다.")
    String icon,

    Long parentId,

    @NotBlank(message = "메뉴 구분을 선택해주세요.")
    @Pattern(regexp = "^(BO|FO)$", message = "메뉴 구분은 BO 또는 FO만 가능합니다.")
    String menuType,

    @NotNull(message = "정렬 순서를 입력해주세요.")
    @Min(value = 1, message = "정렬 순서는 1 이상이어야 합니다.")
    @Max(value = 999, message = "정렬 순서는 999 이하여야 합니다.")
    Integer sortOrder,

    Boolean visible
) {}

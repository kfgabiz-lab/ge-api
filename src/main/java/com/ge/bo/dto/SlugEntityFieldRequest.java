package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * Slug Entity 필드 단건 요청 DTO
 * - columnName / javaType / isPk / defaultValue: entity 생성 기능 추가 시 사용
 */
public record SlugEntityFieldRequest(

    /** 빌더 참조 키 (선택, 예: memberId) */
    @Size(max = 100, message = "key는 100자 이하로 입력해주세요.")
    String key,

    /** 화면 표시명 (필수, 예: 회원 ID) */
    @NotBlank(message = "label을 입력해주세요.")
    @Size(max = 100, message = "label은 100자 이하로 입력해주세요.")
    String label,

    @NotBlank(message = "DB 타입을 선택해주세요.")
    @Pattern(
        regexp = "^(VARCHAR|TEXT|BIGINT|INT|BOOLEAN|TIMESTAMPTZ|DATE|JSONB)$",
        message = "올바른 DB 타입을 선택해주세요."
    )
    String columnType,

    Integer columnLength,

    /** 빌더 필드 타입 (선택, 예: input, textarea, date, checkbox 등) */
    String fieldType,

    /** 공통코드 그룹 코드 (선택) */
    String codeGroupCode,

    Boolean isNullable,

    @Size(max = 500)
    String description,

    Integer sortOrder
) {}

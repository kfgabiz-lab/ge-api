package com.ge.bo.dto;

import jakarta.validation.constraints.*;

/**
 * Slug Entity 등록/수정 요청 DTO
 * - slug: 등록 시에만 사용 (수정 시 서비스에서 무시)
 * - tableName: entity 생성 기능 추가 시 사용
 */
public record SlugEntityRequest(

    @NotBlank(message = "slug를 입력해주세요.")
    @Size(max = 100, message = "slug는 100자 이하로 입력해주세요.")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "slug는 영문/숫자/하이픈/언더스코어만 가능합니다.")
    String slug,

    @NotBlank(message = "표시명을 입력해주세요.")
    @Size(max = 100, message = "표시명은 100자 이하로 입력해주세요.")
    String name,

    @Size(max = 100, message = "DB 테이블명은 100자 이하로 입력해주세요.")
    String tableName,

    String description,

    Boolean active,

    /** 마스터(부모) Entity ID — 없으면 독립 Entity */
    Long parentEntityId
) {}

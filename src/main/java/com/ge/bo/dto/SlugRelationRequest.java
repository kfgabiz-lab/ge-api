package com.ge.bo.dto;

import jakarta.validation.constraints.*;

public record SlugRelationRequest(


        @NotBlank(message = "Master Slug를 입력해주세요.")
        @Size(max = 200)
        String masterSlug,

        @NotBlank(message = "Slave Slug를 입력해주세요.")
        @Size(max = 200)
        String slaveSlug,

        @NotBlank(message = "Master Key를 입력해주세요.")
        @Size(max = 200)
        String masterKey,

        @NotBlank(message = "Slave Key를 입력해주세요.")
        @Size(max = 200)
        String slaveKey,

        @NotBlank(message = "Join Type을 입력해주세요.")
        @Pattern(regexp = "^(EQ|ARRAY_CONTAINS)$", message = "joinType은 EQ 또는 ARRAY_CONTAINS 이어야 합니다.")
        String joinType,

        @Size(max = 200)
        String slaveFilter,

        @NotBlank(message = "방향을 선택해주세요.")
        @Pattern(regexp = "^(FILTER|FETCH)$", message = "relationDir은 FILTER 또는 FETCH 이어야 합니다.")
        String relationDir,

        @Size(max = 500)
        String fetchFields,

        @Size(max = 10)
        String fetchSeparator,

        /** TABLE: slave 직접 추출 / CATEGORY: 상위 계층 거슬러 추출 (기본 TABLE) */
        @Pattern(regexp = "^(TABLE|CATEGORY)$", message = "slaveType은 TABLE 또는 CATEGORY 이어야 합니다.")
        String slaveType,

        /** CATEGORY 유형 FETCH 시 표시할 계층 depth 수 (기본 1, 최대 5) */
        @Min(1) @Max(5)
        Integer categoryDepth,

        /** CATEGORY 유형 FETCH 시 표시할 계층 시작 depth — null이면 categoryDepth와 동일(단일 depth) */
        @Min(1) @Max(5)
        Integer categoryDepthFrom,

        /** CATEGORY 유형 FETCH 시 리프(연결 레코드 자기 자신)를 breadcrumb에 포함할지 여부 — 기본 false(기존 동작 유지) */
        Boolean includeLeaf,

        String description
) {}

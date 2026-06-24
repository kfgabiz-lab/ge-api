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

        String description
) {}

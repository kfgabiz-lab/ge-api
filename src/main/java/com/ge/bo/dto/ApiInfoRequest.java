package com.ge.bo.dto;

import jakarta.validation.constraints.*;

public record ApiInfoRequest(

        @Size(max = 30) String category,

        @NotBlank(message = "API 명칭을 입력해주세요.") @Size(max = 100, message = "API 명칭은 100자 이하로 입력해주세요.") String name,

        @NotBlank(message = "HTTP 메서드를 입력해주세요.")
        @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE)$", message = "올바른 HTTP 메서드를 입력해주세요.")
        String method,

        @NotBlank(message = "URL 패턴을 입력해주세요.") @Size(max = 300, message = "URL 패턴은 300자 이하로 입력해주세요.") String urlPattern,

        String description,

        String connectedEntity,

        Boolean active,

        @Pattern(regexp = "^(ALL|RESTRICTED)$", message = "접근 유형은 ALL 또는 RESTRICTED만 가능합니다.")
        String accessType) {
}

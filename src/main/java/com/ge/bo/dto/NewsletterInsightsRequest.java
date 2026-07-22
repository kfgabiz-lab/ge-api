package com.ge.bo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewsletterInsightsRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "관심 분야를 입력해주세요.")
        @Size(max = 1000)
        String areasOfInterest,
        
        @NotBlank
        String userTimeZone
) {
}
package com.ge.bo.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link MaxByteLength} 검증 구현 — 문자열을 UTF-8 로 인코딩한 byte 길이를 비교한다.
 */
public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

    private int max;

    @Override
    public void initialize(MaxByteLength constraintAnnotation) {
        this.max = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 은 이 검증의 관심사가 아니다 (필수 여부는 @NotBlank 등이 담당)
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}

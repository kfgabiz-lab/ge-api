package com.ge.bo.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * 문자열의 UTF-8 byte 길이 상한 검증.
 * <p>
 * {@code @Size} 는 "문자 수" 기준이라 한글 등 멀티바이트 입력에서는 최대 3배까지 통과한다.
 * 기획에서 "최대 N byte" 로 규정된 항목(예: Training Request 의 comments 2000byte)에 사용한다.
 * null 은 통과시키므로 필수 여부는 {@code @NotBlank} 등으로 따로 지정한다.
 */
@Documented
@Constraint(validatedBy = MaxByteLengthValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxByteLength {

    /** 허용 최대 byte 수 (UTF-8 기준) */
    int value();

    String message() default "입력값이 허용된 byte 길이를 초과했습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TSX 파일 생성 전용 요청 DTO (DB 저장 없음 — 방식 B)
 */
@Getter
@Setter
@NoArgsConstructor
public class PageTemplateGenerateRequest {

    /** URL + 파일경로 식별자 */
    @NotBlank(message = "slug는 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "slug는 영문, 숫자, 하이픈, 언더스코어만 허용됩니다.")
    @Size(max = 100, message = "slug는 100자 이하여야 합니다.")
    private String slug;

    /** FE 생성 TSX 코드 */
    @NotBlank(message = "tsxCode는 필수입니다.")
    private String tsxCode;

    /** 템플릿 유형 (LIST / LAYER) */
    @NotBlank(message = "templateType은 필수입니다.")
    private String templateType;

    /** 생성할 파일명 (확장자 제외, 선택) */
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "fileName은 영문, 숫자, 하이픈, 언더스코어만 허용됩니다.")
    @Size(max = 100)
    private String fileName;
}

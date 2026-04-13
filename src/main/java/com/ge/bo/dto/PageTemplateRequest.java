package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 페이지 템플릿 생성/수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class PageTemplateRequest {

    /** 템플릿 표시 이름 */
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    private String name;

    /**
     * URL + 파일경로 식별자
     * 소문자, 숫자, 하이픈, 언더스코어 허용 — Path Traversal 방지
     */
    @NotBlank(message = "slug는 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "slug는 영문(대·소문자), 숫자, 하이픈, 언더스코어만 허용됩니다. (DEBUG)")
    @Size(max = 100, message = "slug는 100자 이하여야 합니다.")
    private String slug;

    /** 설명 */
    @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
    private String description;

    /** 재편집용 JSON 설정 */
    @NotBlank(message = "configJson은 필수입니다.")
    private String configJson;

    /**
     * FE 생성 TSX 코드 (선택)
     * - 있으면: 파일 시스템에 TSX 파일 생성 (생성 버튼)
     * - 없으면: DB configJson만 저장 (저장 버튼)
     */
    private String tsxCode;

    /** 검색폼 접기 여부 */
    private boolean collapsible;

    /** 템플릿 유형 (LIST / LAYER) — 미전송 시 기존값 유지 또는 기본값 "LIST" */
    private String templateType;

    /**
     * 생성할 파일명 (확장자 제외, 선택)
     * - 입력 시: {slug}/{fileName}.tsx 로 생성
     * - 미입력 시: 기존 기본값 사용 (LIST → page, LAYER → LayerPopup)
     */
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "fileName은 영문, 숫자, 하이픈, 언더스코어만 허용됩니다.")
    @Size(max = 100)
    private String fileName;
}

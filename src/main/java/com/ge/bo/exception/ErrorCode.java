package com.ge.bo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 메뉴 관리 도메인 에러코드
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /* 메뉴 */
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "해당 메뉴를 찾을 수 없습니다."),
    MENU_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "상위 메뉴를 찾을 수 없습니다."),
    MENU_NAME_DUPLICATE(HttpStatus.CONFLICT, "MENU_NAME_DUPLICATE", "이미 동일한 이름의 메뉴가 존재합니다."),
    MENU_URL_DUPLICATE(HttpStatus.CONFLICT, "MENU_URL_DUPLICATE", "이미 사용 중인 URL입니다."),
    MENU_SLUG_DUPLICATE(HttpStatus.CONFLICT, "MENU_SLUG_DUPLICATE", "이미 사용 중인 SLUG입니다."),
    MENU_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "MENU_DEPTH_EXCEEDED", "메뉴는 3단계까지만 생성할 수 있습니다."),
    MENU_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "MENU_TYPE_MISMATCH", "상위 메뉴와 메뉴 구분이 일치하지 않습니다."),
    MENU_SYSTEM_DELETE(HttpStatus.BAD_REQUEST, "MENU_SYSTEM_DELETE", "메뉴 관리 메뉴는 삭제할 수 없습니다."),
    MENU_URL_REQUIRED(HttpStatus.BAD_REQUEST, "MENU_URL_REQUIRED", "하위 메뉴는 URL을 입력해야 합니다."),
    MENU_URL_INVALID(HttpStatus.BAD_REQUEST, "MENU_URL_INVALID", "URL에 연속 슬래시(//)는 사용할 수 없습니다."),
    MENU_XSS_DETECTED(HttpStatus.BAD_REQUEST, "MENU_XSS_DETECTED", "허용되지 않는 문자가 포함되어 있습니다."),
    MENU_TYPE_CHANGE(HttpStatus.BAD_REQUEST, "MENU_TYPE_CHANGE", "메뉴 구분은 변경할 수 없습니다. 삭제 후 재생성해주세요."),
    MENU_PARENT_CHANGE(HttpStatus.BAD_REQUEST, "MENU_PARENT_CHANGE", "상위 메뉴는 변경할 수 없습니다. 삭제 후 재생성해주세요."),
    MENU_TYPE_INVALID(HttpStatus.BAD_REQUEST, "MENU_TYPE_INVALID", "메뉴 구분은 BO 또는 FO만 가능합니다."),

    /* 역할 */
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "해당 역할을 찾을 수 없습니다."),

    /* 공통코드 */
    CODE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "CODE_GROUP_NOT_FOUND", "해당 코드 그룹을 찾을 수 없습니다."),
    CODE_DETAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "CODE_DETAIL_NOT_FOUND", "해당 코드를 찾을 수 없습니다."),
    CODE_GROUP_DUPLICATE(HttpStatus.CONFLICT, "CODE_GROUP_DUPLICATE", "이미 동일한 그룹코드가 존재합니다."),
    CODE_DETAIL_DUPLICATE(HttpStatus.CONFLICT, "CODE_DETAIL_DUPLICATE", "동일 그룹 내에 같은 코드값이 존재합니다."),
    CODE_GROUP_MISMATCH(HttpStatus.BAD_REQUEST, "CODE_GROUP_MISMATCH", "해당 코드는 이 그룹에 속하지 않습니다."),
    CODE_XSS_DETECTED(HttpStatus.BAD_REQUEST, "XSS_DETECTED", "허용되지 않는 문자가 포함되어 있습니다."),

    /* 페이지 템플릿 */
    PAGE_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAGE_TEMPLATE_NOT_FOUND", "해당 페이지 템플릿을 찾을 수 없습니다."),
    PAGE_TEMPLATE_NAME_DUPLICATE(HttpStatus.CONFLICT, "PAGE_TEMPLATE_NAME_DUPLICATE", "이미 동일한 이름의 템플릿이 존재합니다."),
    PAGE_TEMPLATE_SLUG_DUPLICATE(HttpStatus.CONFLICT, "PAGE_TEMPLATE_SLUG_DUPLICATE", "이미 사용 중인 slug입니다."),
    PAGE_TEMPLATE_FILE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAGE_TEMPLATE_FILE_ERROR", "TSX 파일 생성 중 오류가 발생했습니다."),

    /* 페이지 데이터 */
    PAGE_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "PAGE_DATA_NOT_FOUND", "해당 데이터를 찾을 수 없습니다."),

    /* TSX 생성 이력 */
    TSX_GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "TSX_GENERATION_NOT_FOUND", "해당 생성 이력을 찾을 수 없습니다."),

    /* 파일 업로드 */
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "파일은 필수입니다."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "비어있는 파일은 업로드할 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "해당 파일을 찾을 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 저장 중 오류가 발생했습니다."),

    /* 공통 */
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "DATA_INTEGRITY", "데이터 무결성 제약조건 위반이 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    /** BusinessException 생성 헬퍼 */
    public BusinessException toException() {
        return new BusinessException(this.status, this.code, this.message);
    }
}

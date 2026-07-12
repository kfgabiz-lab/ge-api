package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Request DTO — 테스트데이터
 * 생성일시: 2026-07-12T20:46:31.980769900+09:00
 * 원본 Slug Entity: id=18, tableName=test_data
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

/**
 * 테스트데이터 등록/수정 요청 DTO
 */
public record TestDataRequest(

    String titme,

    @NotBlank(message = "필수 입력 항목입니다: 내용")
    String content,

    @NotBlank(message = "필수 입력 항목입니다: 공개여부")
    String display,

    @NotBlank(message = "필수 입력 항목입니다: 주문방식")
    String orderType,

    @NotNull(message = "필수 입력 항목입니다: 게시시작시간")
    OffsetDateTime dispFrom,

    @NotNull(message = "필수 입력 항목입니다: 게시종료시간")
    OffsetDateTime dispTo,

    @NotNull(message = "필수 입력 항목입니다: 제품")
    Long products,

    @NotNull(message = "필수 입력 항목입니다: 담당자")
    Long persons,

    @NotBlank(message = "필수 입력 항목입니다: 정보1")
    String info1,

    @NotBlank(message = "필수 입력 항목입니다: 정보2")
    String info2
) {}

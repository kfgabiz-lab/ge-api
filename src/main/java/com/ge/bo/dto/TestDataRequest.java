package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Request DTO — 테스트데이터
 * 생성일시: 2026-07-13T13:30:49.157417100+09:00
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

    String content,

    String display,

    String orderType,

    OffsetDateTime dispFrom,

    OffsetDateTime dispTo,

    Long products,

    Long persons,

    String info1,

    String info2
) {}

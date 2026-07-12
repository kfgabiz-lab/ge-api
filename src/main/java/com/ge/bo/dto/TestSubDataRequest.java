package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Request DTO — 테스트서브데이터
 * 생성일시: 2026-07-12T20:46:55.027515800+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import jakarta.validation.constraints.*;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * 테스트서브데이터 등록/수정 요청 DTO
 */
public record TestSubDataRequest(

    @NotNull(message = "필수 입력 항목입니다: 테스트데이터 ID")
    Long testDataId,

    String name,

    OffsetDateTime dispFrom,

    OffsetDateTime dispTo,

    @NotNull(message = "필수 입력 항목입니다: 첨부파일1")
    List<Long> file1Id,

    @NotNull(message = "필수 입력 항목입니다: 첨부파일2")
    List<Long> file2Id
) {}

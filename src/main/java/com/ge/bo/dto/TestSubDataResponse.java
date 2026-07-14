package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Response DTO — 테스트서브데이터
 * 생성일시: 2026-07-13T13:31:22.729860200+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.entity.TestSubData;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * 테스트서브데이터 응답 DTO
 */
public record TestSubDataResponse(
    Long id,
    Long testDataId,
    String name,
    OffsetDateTime dispFrom,
    OffsetDateTime dispTo,
    List<Long> file1Id,
    List<Long> file2Id,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt) {

  public static TestSubDataResponse from(TestSubData e) {
    return new TestSubDataResponse(
        e.getId(),
        e.getTestDataId(),
        e.getName(),
        e.getDispFrom(),
        e.getDispTo(),
        e.getFile1Id(),
        e.getFile2Id(),
        e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
  }
}

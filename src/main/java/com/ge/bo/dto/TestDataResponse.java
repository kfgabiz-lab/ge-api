package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Response DTO — 테스트데이터
 * 생성일시: 2026-07-12T20:46:31.981773500+09:00
 * 원본 Slug Entity: id=18, tableName=test_data
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.entity.TestData;
import java.time.OffsetDateTime;

/**
 * 테스트데이터 응답 DTO
 */
public record TestDataResponse(
    Long id,
    String titme,
    String content,
    String display,
    String orderType,
    OffsetDateTime dispFrom,
    OffsetDateTime dispTo,
    Long products,
    Long persons,
    String info1,
    String info2,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt) {

  public static TestDataResponse from(TestData e) {
    return new TestDataResponse(
        e.getId(),
        e.getTitme(),
        e.getContent(),
        e.getDisplay(),
        e.getOrderType(),
        e.getDispFrom(),
        e.getDispTo(),
        e.getProducts(),
        e.getPersons(),
        e.getInfo1(),
        e.getInfo2(),
        e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
  }
}

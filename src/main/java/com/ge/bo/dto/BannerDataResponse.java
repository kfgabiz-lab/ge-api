package com.ge.bo.dto;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Response DTO — 배너
 * 생성일시: 2026-07-12T13:37:46.284663+09:00
 * 원본 Slug Entity: id=1, tableName=banner
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.entity.BannerData;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * 배너 응답 DTO
 */
public record BannerDataResponse(
    Long id,
    String bannerPosition,
    String title,
    OffsetDateTime postDateFrom,
    String prefix,
    String mainTitle,
    String bottomText,
    String subTitle,
    String url,
    List<Long> imageFileId,
    Integer sortOrder,
    String isVisible,
    String infoSort,
    OffsetDateTime postDateTo,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt) {

  public static BannerDataResponse from(BannerData e) {
    return new BannerDataResponse(
        e.getId(),
        e.getBannerPosition(),
        e.getTitle(),
        e.getPostDateFrom(),
        e.getPrefix(),
        e.getMainTitle(),
        e.getBottomText(),
        e.getSubTitle(),
        e.getUrl(),
        e.getImageFileId(),
        e.getSortOrder(),
        e.getIsVisible(),
        e.getInfoSort(),
        e.getPostDateTo(),
        e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
  }
}

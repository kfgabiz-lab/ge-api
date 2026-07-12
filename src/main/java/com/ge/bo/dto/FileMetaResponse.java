package com.ge.bo.dto;

import com.ge.bo.entity.FileMeta;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 파일 메타정보 응답 DTO
 * Slug Entity FILE 타입 필드가 참조하는 파일 메타데이터 응답
 */
@Getter
@Builder
public class FileMetaResponse {

  private Long id;
  private String originalName;
  private String blobUrl;
  private Long fileSize;
  private String mimeType;
  private OffsetDateTime createdAt;

  /** FileMeta 엔티티 → 응답 DTO 변환 */
  public static FileMetaResponse from(FileMeta fileMeta) {
    return FileMetaResponse.builder()
        .id(fileMeta.getId())
        .originalName(fileMeta.getOriginalName())
        .blobUrl(fileMeta.getBlobUrl())
        .fileSize(fileMeta.getFileSize())
        .mimeType(fileMeta.getMimeType())
        .createdAt(fileMeta.getCreatedAt())
        .build();
  }
}

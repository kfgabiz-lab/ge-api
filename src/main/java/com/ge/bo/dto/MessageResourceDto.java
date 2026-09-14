package com.ge.bo.dto;

import com.ge.bo.entity.MessageResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;

public class MessageResourceDto {

    /** 등록 요청 DTO */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CreateRequest {

    @NotBlank(message = "번역 키를 입력해주세요.")
        @Size(max = 255, message = "번역 키는 255자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9.]+$", message = "번역 키는 영문, 숫자, 점(.)만 입력 가능합니다.")
        private String key;

    @NotBlank(message = "한국어를 입력해주세요.")
        @Size(max = 500, message = "한국어는 500자 이하여야 합니다.")
        private String ko;

    @Size(max = 500, message = "영어는 500자 이하여야 합니다.")
        private String en;

    /* 유형 — null이면 서버에서 WORD로 처리 */
    private MessageResourceType resourceType;
  }

    /** 수정 요청 DTO — key 변경 불가이므로 미포함 */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class UpdateRequest {

    @NotBlank(message = "한국어를 입력해주세요.")
        @Size(max = 500, message = "한국어는 500자 이하여야 합니다.")
        private String ko;

    @Size(max = 500, message = "영어는 500자 이하여야 합니다.")
        private String en;

    @NotNull(message = "사용여부를 입력해주세요.")
        private Boolean active;

    /* 유형 — null이면 기존 값 유지 */
    private MessageResourceType resourceType;
  }

    /** 응답 DTO */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class Response {
    private Long id;
    private String key;
    private String ko;
    private String en;
    private boolean active;
    private MessageResourceType resourceType;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
  }

    /** 목록 조회 응답 DTO (페이징 포함) */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PageResponse {
    private java.util.List<Response> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;
  }
}

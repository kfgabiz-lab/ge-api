package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

public class RoleDto {

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CreateRequest {
    @NotBlank(message = "역할 코드를 입력해주세요.")
        @Size(min = 2, max = 30, message = "역할 코드는 2자 이상 30자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "영문 대문자, 숫자, _만 입력 가능합니다.")
        private String code;

    @Size(max = 20, message = "표시명은 20자 이하여야 합니다.")
        private String displayName;

    private String displayNameMsgKey;

    @Size(max = 100, message = "설명은 100자 이하여야 합니다.")
        private String description;

    private String descriptionMsgKey;

    @NotBlank(message = "색상을 선택해주세요.")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "올바른 색상 형식이 아닙니다.")
        private String color;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class UpdateRequest {
    @Size(max = 20, message = "표시명은 20자 이하여야 합니다.")
        private String displayName;

    private String displayNameMsgKey;

    @Size(max = 100, message = "설명은 100자 이하여야 합니다.")
        private String description;

    private String descriptionMsgKey;

    @NotBlank(message = "색상을 선택해주세요.")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "올바른 색상 형식이 아닙니다.")
        private String color;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class Response {
    private Long id;
    private String code;
    private String displayName;
    private String displayNameMsgKey;
    private String description;
    private String descriptionMsgKey;
    private String color;
    private boolean isSystem;
    private long memberCount; // 해당 역할을 가진 관리자 수
  }
}

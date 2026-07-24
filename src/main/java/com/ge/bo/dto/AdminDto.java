package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AdminDto {

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CreateRequest {
    @NotBlank(message = "유효한 아이디(이메일)를 입력해주세요.")
        @Email(message = "유효한 아이디(이메일)를 입력해주세요.")
        private String email;

    @NotBlank(message = "사용자명은 2자 이상 50자 이하로 입력해주세요.")
        @Size(min = 2, max = 50, message = "사용자명은 2자 이상 50자 이하로 입력해주세요.")
        private String name;

        /* 부서코드 */
    @Size(max = 50, message = "부서코드는 50자 이내로 입력해주세요.")
        private String deptCode;

        /* 부서명 */
    @Size(max = 100, message = "부서명은 100자 이내로 입력해주세요.")
        private String deptName;

        /* 비고 */
    @Size(max = 500, message = "비고는 500자 이내로 입력해주세요.")
        private String remark;

    @NotBlank(message = "유효하지 않은 역할 코드입니다.")
        private String role;

    @JsonProperty("isActive")
        private boolean isActive;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UpdateRequest {
    private String email;

    @Size(min = 2, max = 50, message = "사용자명은 2자 이상 50자 이하로 입력해주세요.")
        private String name;

        /* 부서코드 */
    @Size(max = 50, message = "부서코드는 50자 이내로 입력해주세요.")
        private String deptCode;

        /* 부서명 */
    @Size(max = 100, message = "부서명은 100자 이내로 입력해주세요.")
        private String deptName;

        /* 비고 */
    @Size(max = 500, message = "비고는 500자 이내로 입력해주세요.")
        private String remark;

    private String role;

    @JsonProperty("isActive")
        private boolean isActive;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class Response {
    private Long id;
    private String email;
    private String name;
        /* 부서코드 */
    private String deptCode;
        /* 부서명 */
    private String deptName;
        /* 비고 */
    private String remark;
    private String role;
    @JsonProperty("isActive")
        private boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
        /* 등록일 */
    private LocalDate regDt;
        /* 등록시간 */
    private LocalTime regTm;
    private String tempPassword; // Only populated during creation
  }
}

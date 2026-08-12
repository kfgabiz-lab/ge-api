package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UpdateRequest {
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
  }
}

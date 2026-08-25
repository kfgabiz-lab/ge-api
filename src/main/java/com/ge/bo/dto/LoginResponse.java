package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
  /** 2FA 미완료 상태 임시 토큰 (10분, 2FA 완료 후 accessToken 발급) */
  private String tempToken;
  /** true = QR 등록 화면으로 이동 (2FA 미등록 계정) */
  private Boolean requireTotpSetup;
  /** true = OTP 입력 화면으로 이동 (2FA 등록 완료 계정) */
  private Boolean requireTotpVerify;
  private String accessToken;
  private Long expiresIn;
  private AdminInfo adminInfo;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AdminInfo {
    private Long id;
    private String name;
    private String email;
    private String employeeId;
    private String role;
    private String roleDisplayName;
    private String roleDisplayNameMsgKey;
    @JsonProperty("isSystem")
        private boolean isSystem; // 시스템관리자 여부 (role.is_system 기반)
  }
}

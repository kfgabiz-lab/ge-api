package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
  @NotBlank(message = "아이디를 입력해주세요.")
  @Size(max = 30, message = "아이디는 30자 이내로 입력해주세요.")
  @Pattern(regexp = "^[a-zA-Z0-9.]+$", message = "아이디는 영문/숫자/마침표(.)만 입력 가능합니다.")
  private String email;

  @NotBlank(message = "비밀번호를 입력해주세요.")
  @Size(min = 4, max = 100, message = "비밀번호는 4자 이상 100자 이하로 입력해주세요.")
  private String password;

  /** 자체 캡차 인증번호 (FE에서 입력값 전달) */
  private String captchaCode;

  /** 자체 캡차 발급 시 받은 암호화 토큰 (FE가 그대로 되돌려줌, stateless 검증용) */
  private String captchaToken;
}

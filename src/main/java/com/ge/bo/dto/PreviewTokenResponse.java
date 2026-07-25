package com.ge.bo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewTokenResponse {
  private String token;
  private long expiresIn;
}

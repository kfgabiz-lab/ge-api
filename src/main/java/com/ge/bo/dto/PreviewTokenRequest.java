package com.ge.bo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewTokenRequest {
  private String slug;
  private String recordId;
}

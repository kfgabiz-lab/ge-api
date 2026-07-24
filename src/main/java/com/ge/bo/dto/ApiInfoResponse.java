package com.ge.bo.dto;

import com.ge.bo.entity.ApiInfo;
import java.time.LocalDateTime;

public record ApiInfoResponse(
        Long id,
        String category,
        String name,
        String method,
        String urlPattern,
        String description,
        String connectedEntity,
        Boolean active,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt) {
  public static ApiInfoResponse from(ApiInfo e) {
    return new ApiInfoResponse(
                e.getId(), e.getCategory(), e.getName(), e.getMethod(),
                e.getUrlPattern(), e.getDescription(), e.getConnectedEntity(), e.getActive(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
  }
}

package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CtpFileDownResponse(
        String downloadUrl,
        String expiresInMinutes,
        String requestedExpiresInMinutes,
        String filePath,
        String maxExpiresInMinutes,
        String expiresAt
) {
}

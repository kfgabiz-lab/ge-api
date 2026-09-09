package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CtpFilePreviewResponse(
        String code,
        String message,
        PreviewData data
) {

    public record PreviewData(
            String viewerType,
            String filePath,
            String fileName,
            String extension,
            Long fileSize,
            String lastModified,
            String previewUrl,
            Integer expiresInMinutes,
            Integer requestedExpiresInMinutes,
            Integer maxExpiresInMinutes
    ) {
    }
}
package com.ge.bo.dto;

public record FoPageFileMetaResponse(Long id, String mimeType) {

    public static FoPageFileMetaResponse from(PageFileResponse pageFile) {
        return new FoPageFileMetaResponse(pageFile.getId(), pageFile.getMimeType());
    }
}

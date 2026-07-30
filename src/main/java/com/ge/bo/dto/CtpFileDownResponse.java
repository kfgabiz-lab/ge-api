package com.ge.bo.dto;

public record CtpFileDownResponse(String downloadUrl, String expiresInMinutes, String requestedExpiresInMinutes, String filePath, String maxExpiresInMinutes, String expiresAt) {
}

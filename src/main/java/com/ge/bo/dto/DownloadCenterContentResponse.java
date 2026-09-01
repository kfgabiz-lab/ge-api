package com.ge.bo.dto;

import java.util.List;

public record DownloadCenterContentResponse(
        Long id,
        String docType,
        String docTypeLabel,
        String title,
        String date,
        List<CategoryRef> categories,
        List<DownloadCenterVersionResponse> versions
) {}

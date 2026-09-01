package com.ge.bo.dto;

import java.util.List;

public record TechHubContentResponse(
        Long id,
        String title,
        String sourceUpdatedAt,
        List<CategoryRef> categories,
        String videoUrl,
        int versionCount
) {}

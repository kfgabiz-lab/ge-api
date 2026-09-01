package com.ge.bo.dto;

import java.util.List;

public record TechHubDetailResponse(
        Long id,
        String title,
        String sourceUpdatedAt,
        List<CategoryRef> categories,
        int versionCount,
        List<TechHubChapterResponse> chapters,
        List<TechHubContentResponse> relatedVideos
) {}

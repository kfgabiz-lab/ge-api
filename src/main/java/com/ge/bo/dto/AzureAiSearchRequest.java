package com.ge.bo.dto;

public record AzureAiSearchRequest(

        String search,
        String filter,
        String highlight,
        String highlightPreTag,
        String highlightPostTag,
        String orderby,
        Integer top,
        Integer skip,
        Boolean count
) {
}
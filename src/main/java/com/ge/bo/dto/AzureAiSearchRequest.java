package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
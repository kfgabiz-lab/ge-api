package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureAiSearchResponse(
        @JsonProperty("@odata.context")
        String odataContext,

        @JsonProperty("@odata.count")
        Long totalCount,

        List<AzureAiSearchDocument> value
) {
}

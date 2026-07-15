package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Salesforce OAuth2 client_credentials 토큰 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CtpTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("instance_url") String instanceUrl,
        @JsonProperty("token_type") String tokenType) {
}
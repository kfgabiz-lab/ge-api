package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatbotSearchRequest(
        String query,

        @JsonProperty("data_market")
        String dataMarket,

        @JsonProperty("web_enabled")
        String webEnabled
) {

        public ChatbotSearchRequest {
                dataMarket =
                        dataMarket == null || dataMarket.isBlank()
                                ? "GLOBAL"
                                : dataMarket;

                webEnabled =
                        webEnabled == null || webEnabled.isBlank()
                                ? "true"
                                : webEnabled;
        }
}
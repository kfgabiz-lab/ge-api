package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.AzureAiSearchRequest;
import com.ge.bo.dto.AzureAiSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAiSearchService {

    private final ExternalApiClient externalApiClient;

    @Value("${ls.lse.out-api.azure-search.api-url}")
    String apiUrl;

    @Value("${ls.lse.out-api.azure-search.api-key}")
    String apiKey;


    public AzureAiSearchResponse azureAiSearch(
            String keyword
    ) {
        /* 콤마로 이어진 "키워드,연관검색어1,연관검색어2..."에서 주 키워드(첫 토큰)만 Azure에 전달 */
        String primaryKeyword = keyword == null ? "" : keyword.split(",", 2)[0].trim();

        AzureAiSearchRequest body = new AzureAiSearchRequest(
                primaryKeyword,
                "",
                "content",
                "<em>",
                "</em>",
                "search.score() desc",
                null,
                null,
                true
        );

        ApiCallRequest request = ApiCallRequest.post(apiUrl)
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .body(body)
                .build();
        ApiCallResult<AzureAiSearchResponse> result = externalApiClient.call(request, AzureAiSearchResponse.class);

        if (!result.isSuccess() || result.getData() == null) {
            log.warn("Azure AI Search API 실패 또는 결과 없음. statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());
            return null;
        }
        log.info("AzureAiSearchResponse : {}", result.getData().toString());
        return result.getData();
    }
}

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
            String keyword,
            String pageNumber
    ) {

        // parameter setting

        int pageSize = 10;
        int skip = (pageNumber == null? 1 : Integer.parseInt(pageNumber)) * pageSize;

        AzureAiSearchRequest body = new AzureAiSearchRequest(
                keyword,
                "",
                "content",
                "<em>",
                "</em>",
                "last_modified desc",
                pageSize,
                skip,
                true
        );

        // Azure AI Search API 호출
        ApiCallRequest request = ApiCallRequest.post(apiUrl)
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .body(body)
                .build();
        ApiCallResult<AzureAiSearchResponse> result = externalApiClient.call(request, AzureAiSearchResponse.class);

        if (!result.isSuccess() || result.getData() == null) {
            log.warn("Azure AI Search API 실패 또는 결과 없음. statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());

        }
        log.info("AzureAiSearchResponse : {}", result.getData().toString());
        return result.getData();
    }
}

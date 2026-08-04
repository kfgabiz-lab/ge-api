package com.ge.bo.service;

import com.ge.bo.dto.AzureAiSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegratedSearchService {

    private final AzureAiSearchService azureAiSearchService;

    public AzureAiSearchResponse search(
            String keyword
    ) {

        // Azure Ai Search API 호출로 문서 정보 조회
        return azureAiSearchService.azureAiSearch(keyword);
    }

}
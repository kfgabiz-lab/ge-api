package com.ge.bo.controller;

import com.ge.bo.dto.AzureAiSearchResponse;
import com.ge.bo.dto.ChatbotSearchRequest;
import com.ge.bo.service.ChatbotSearchService;
import com.ge.bo.service.IntegratedSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/fo/search")
@RequiredArgsConstructor
public class IntegratedSearchController {

    private final IntegratedSearchService integratedSearchService;
    private final ChatbotSearchService chatbotSearchService;

    /**
     * AI 챗봇 SSE 스트리밍 API.
     *
     * 요청:
     * Content-Type: application/json
     *
     * 응답:
     * Content-Type: text/event-stream
     *
     * 응답 예시:
     *
     * event: response.chunk
     * data: {...}
     *
     * event: response.complete
     * data: {...}
     */
    @PostMapping(
            value="/chatbot",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chatbotSearch(@Valid
                                                           @RequestBody
                                                       ChatbotSearchRequest request) {
        log.info("CHATBOT START {}", LocalTime.now());

        Flux<ServerSentEvent<String>> result = chatbotSearchService.search(request);


        log.info("CHATBOT END {}", LocalTime.now());

        return result;

    }

    @GetMapping("/integrated")
    public ResponseEntity<AzureAiSearchResponse> integratedSearch(@RequestParam String keyword,
                                                                                              @RequestParam(defaultValue = "all")
                                                      String type,
                                                                                              @RequestParam String pageNumber) {

        log.info("INTEGRATED START {}", LocalTime.now());

        AzureAiSearchResponse result = integratedSearchService.search(
                keyword,
                type,
                pageNumber
        );

        log.info("INTEGRATED END {}", LocalTime.now());

        return ResponseEntity.ok(result);
    }
}

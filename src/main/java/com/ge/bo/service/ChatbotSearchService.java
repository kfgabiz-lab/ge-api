package com.ge.bo.service;

import com.ge.bo.dto.ChatbotSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.LocalTime;

/**
 * 외부 AI 챗봇 API 호출 Service.
 * 외부 API 응답 형식
 * event: response.chunk
 * data: {...}
 *
 * event: response.chunk
 * data: {...}
 *
 * event: response.complete
 * data: {...}
 */
@Slf4j
@Service
public class ChatbotSearchService {

    /**
     * 챗봇 답변 일부가 전달되는 이벤트명.
     */
    private static final String EVENT_CHUNK =
            "response.chunk";

    /**
     * 챗봇 답변 생성이 완료되었을 때 전달되는 이벤트명.
     */
    private static final String EVENT_COMPLETED =
            "response.completed";

    private static final String EVENT_KEYWORD = "response.keyword";

    /**
     * ServerSentEvent<String>의 제네릭 타입 정보를
     * 런타임까지 유지하기 위한 타입 정의.
     */
    private static final ParameterizedTypeReference<
            ServerSentEvent<String>
            > SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /**
     * 챗봇 SSE 호출 전용 WebClient.
     */
    private final WebClient chatbotWebClient;

    /**
     * 외부 챗봇 API 주소.
     */
    private final String chatbotApiUrl;

    /**
     * 외부 챗봇 API 인증키.
     */
    private final String chatbotApiKey;

    /**
     * 외부 챗봇 API 인증 헤더 이름.
     *
     * 예:
     * api-key
     * Authorization
     */
    private final String chatbotApiKeyHeader;

    public ChatbotSearchService(
            @Qualifier("chatbotWebClient")
            WebClient chatbotWebClient,

            @Value("${ls.lse.out-api.chatbot-search.api-url}")
            String chatbotApiUrl,

            @Value("${ls.lse.out-api.chatbot-search.api-key}")
            String chatbotApiKey
    ) {
        this.chatbotWebClient = chatbotWebClient;
        this.chatbotApiUrl = chatbotApiUrl;
        this.chatbotApiKey = "Bearer " + chatbotApiKey;
        this.chatbotApiKeyHeader = HttpHeaders.AUTHORIZATION;
    }

    /**
     * 외부 챗봇 API를 호출하고 SSE 이벤트를 실시간으로 반환한다.
     *
     * response.chunk 이벤트는 계속 전달하고,
     * response.complete 이벤트까지 전달한 뒤 스트림을 종료한다.
     *
     * @param request 챗봇 검색 요청
     * @return 외부 챗봇의 SSE 응답 스트림
     */
    public Flux<ServerSentEvent<String>> search(
            ChatbotSearchRequest request
    ) {
        return chatbotWebClient
                .post()
                .uri(chatbotApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header(
                        chatbotApiKeyHeader,
                        chatbotApiKey
                )
                .bodyValue(request)
                .exchangeToFlux(response -> {

                    log.info(
                            "[CHATBOT HTTP RESPONSE] status={}, contentType={}, timestamp={}",
                            response.statusCode(),
                            response.headers()
                                    .contentType()
                                    .orElse(null),
                            LocalTime.now()
                    );

                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(
                                SSE_STRING_TYPE
                        );
                    }

                    return response
                            .bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMapMany(body ->
                                    Flux.error(
                                            new IllegalStateException(
                                                    "챗봇 API 호출 실패. status="
                                                            + response.statusCode()
                                                            + ", body="
                                                            + body
                                            )
                                    )
                            );
                })
                .doOnNext(event ->
                        log.info(
                                "[CHATBOT SSE RECEIVED] event={}, id={}, data={}, timestamp={}",
                                event.event(),
                                event.id(),
                                event.data(),
                                LocalTime.now()
                        )
                )
                .filter(event -> {
                    String eventName = event.event();

                    return EVENT_KEYWORD.equals(eventName)
                            || EVENT_CHUNK.equals(eventName)
                            || EVENT_COMPLETED.equals(eventName);
                })
                .doOnNext(event ->
                        log.info(
                                "[CHATBOT SSE FORWARD] event={}, id={}, data={}, timestamp={}",
                                event.event(),
                                event.id(),
                                event.data(),
                                LocalTime.now()
                        )
                )
                .doOnComplete(() ->
                        log.info(
                                "[CHATBOT STREAM COMPLETE] url={}, timestamp={}",
                                chatbotApiUrl,
                                LocalTime.now()
                        )
                )
                .doOnCancel(() ->
                        log.info(
                                "[CHATBOT STREAM CANCELLED] url={}",
                                chatbotApiUrl
                        )
                )
                .doOnError(error ->
                        log.error(
                                "[CHATBOT STREAM ERROR] url={}, message={}",
                                chatbotApiUrl,
                                error.getMessage(),
                                error
                        )
                );
    }
}

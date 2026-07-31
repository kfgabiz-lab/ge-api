package com.ge.bo.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 외부 AI 챗봇의 SSE 스트리밍 호출에 사용할 WebClient 설정.
 */
@Configuration
public class StreamingWebClientConfig {

    /**
     * 챗봇 스트리밍 전용 WebClient.
     * 챗봇 응답은 연결이 오랫동안 유지될 수 있으므로
     * 일반 REST API와 별도의 WebClient를 사용한다.
     */
    @Bean("chatbotWebClient")
    public WebClient chatbotWebClient(
            WebClient.Builder builder
    ) {
        HttpClient httpClient = HttpClient.create()

                /*
                 * 외부 서버와 TCP 연결을 맺을 때까지 기다리는 시간.
                 *
                 * 10초 안에 연결되지 않으면 연결 오류가 발생한다.
                 */
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        10_000
                )

                /*
                 * 요청 후 최초 HTTP 응답을 받기까지 기다리는 시간.
                 *
                 * SSE 전체 스트리밍 제한 시간이 아니라,
                 * 응답이 시작될 때까지의 제한 시간이다.
                 */
                .responseTimeout(
                        Duration.ofSeconds(60)
                )

                /*
                 * 요청 데이터를 외부 서버로 전송하는 제한 시간.
                 */
                .doOnConnected(connection ->
                        connection.addHandlerLast(
                                new WriteTimeoutHandler(
                                        30,
                                        TimeUnit.SECONDS
                                )
                        )
                );

        return builder
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .build();
    }
}
package com.ge.bo.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * 외부 API 호출 공통 클라이언트
 * 사용법: externalApiClient.call(ApiCallRequest.post(url).body(data).build(), ResponseType.class)
 * - RestClient 빈 1개 재사용 (매번 new 생성 금지)
 * - 전역 타임아웃: 연결 5초 / 읽기 10초
 * - 사내 SSL 검사 우회 처리 포함 (운영 시 회사 CA를 Java truststore에 등록 권장)
 */
@Slf4j
@Component
public class ExternalApiClient {

    private final RestClient restClient;

    // SSL 전체 신뢰 컨텍스트 (한 번만 생성해서 재사용)
    private final SSLContext trustAllSslContext;

    public ExternalApiClient(RestClient.Builder builder) {
        // SSL 컨텍스트 초기화
        this.trustAllSslContext = createTrustAllSslContext();

        // SimpleClientHttpRequestFactory를 익명 클래스로 확장해 prepareConnection 오버라이드
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection conn, String httpMethod)
                    throws IOException {
                // 사내 SSL 검사 우회 (운영 시 회사 CA를 Java truststore에 등록 권장)
                if (conn instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
                    https.setHostnameVerifier((h, s) -> true);
                }
                super.prepareConnection(conn, httpMethod);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = builder
                .requestFactory(factory)
                .build();
    }

    /**
     * 외부 API 호출
     *
     * @param request      요청 정보 (url, method, headers, body)
     * @param responseType 응답 바디 매핑 타입
     * @return ApiCallResult — 성공/실패 여부, 상태코드, 데이터, 오류 메시지 포함
     */
    public <T> ApiCallResult<T> call(ApiCallRequest request, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .method(request.getMethod())
                .uri(request.getUrl());

            // 요청 헤더 추가
            request.getHeaders().forEach(spec::header);

            // 바디 유무에 따라 분기
            ResponseEntity<T> response = (request.getBody() != null)
                ? spec.body(request.getBody()).retrieve().toEntity(responseType)
                : spec.retrieve().toEntity(responseType);

            return ApiCallResult.success(response.getStatusCode().value(), response.getBody());

        } catch (HttpClientErrorException e) {
            // 4xx 오류 (잘못된 요청, 인증 실패 등)
            log.warn("외부 API 클라이언트 오류 [{}] url={}", e.getStatusCode().value(), request.getUrl());
            return ApiCallResult.failure(e.getStatusCode().value(), e.getMessage());
        } catch (HttpServerErrorException e) {
            // 5xx 오류 (외부 서버 문제)
            log.warn("외부 API 서버 오류 [{}] url={}", e.getStatusCode().value(), request.getUrl());
            return ApiCallResult.failure(e.getStatusCode().value(), "외부 서버 오류");
        } catch (ResourceAccessException e) {
            // 네트워크 오류 (타임아웃, 연결 거부 등)
            log.warn("외부 API 연결 실패 url={}", request.getUrl());
            return ApiCallResult.failure(0, "외부 서버 연결 실패");
        } catch (Exception e) {
            log.error("외부 API 호출 오류 url={}", request.getUrl(), e);
            return ApiCallResult.failure(0, "외부 API 호출 오류");
        }
    }

    /**
     * SSL 전체 신뢰 컨텍스트 생성
     * 사내 SSL 검사 우회용 — 운영 환경에서는 회사 CA를 Java truststore에 등록하여 사용 권장
     */
    private SSLContext createTrustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("SSL 우회 컨텍스트 생성 실패", e);
        }
    }
}
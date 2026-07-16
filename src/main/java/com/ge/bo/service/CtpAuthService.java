package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.CtpTokenResponse;
import com.ge.bo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.time.Duration;
import java.time.Instant;

/**
 * Connect Portal(CTP) OAuth2 client_credentials 토큰 발급/캐시
 * 사용법: getAccessToken() — 캐시된 토큰 반환(없거나 만료 시 자동 재발급)
 *        401 응답 수신 시 refreshAccessToken() 으로 1회 재시도
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CtpAuthService {

    // Salesforce client_credentials 토큰은 만료 정보를 별도로 내려주지 않아 30분 주기로 보수적으로 재발급
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final ExternalApiClient externalApiClient;
    private final CtpProperties ctpProperties;

    private volatile String cachedAccessToken;
    private volatile Instant cachedAt = Instant.EPOCH;

    public String getAccessToken() {
        String token = cachedAccessToken;
        if (token != null && Instant.now().isBefore(cachedAt.plus(TOKEN_TTL))) {
            return token;
        }
        synchronized (this) {
            if (cachedAccessToken == null || !Instant.now().isBefore(cachedAt.plus(TOKEN_TTL))) {
                refreshToken();
            }
            return cachedAccessToken;
        }
    }

    public synchronized String refreshAccessToken() {
        refreshToken();
        return cachedAccessToken;
    }

    private void refreshToken() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", ctpProperties.getGrantType());
        form.add("client_id", ctpProperties.getClientId());
        form.add("client_secret", ctpProperties.getClientSecret());

        ApiCallRequest request = ApiCallRequest.post(ctpProperties.getTokenUrl())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(form)
                .build();

        ApiCallResult<CtpTokenResponse> result = externalApiClient.call(request, CtpTokenResponse.class);

        if (!result.isSuccess() || result.getData() == null || result.getData().accessToken() == null) {
            log.warn("Connect Portal 토큰 발급 실패 statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "CTP_AUTH_FAILED",
                "Connect Portal 인증 토큰 발급에 실패했습니다.");
        }

        this.cachedAccessToken = result.getData().accessToken();
        this.cachedAt = Instant.now();
        log.info("Connect Portal 액세스 토큰 재발급 완료");
    }
}

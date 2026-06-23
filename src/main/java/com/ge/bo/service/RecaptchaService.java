package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Google reCAPTCHA v2 토큰 검증 서비스
 * 사용법: recaptchaService.verify(token) — 실패 시 BusinessException 발생
 */
@Service
@RequiredArgsConstructor
public class RecaptchaService {

    private static final String VERIFY_URL =
        "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s";

    @Value("${ls.lse.outApi.recapchaKey}")
    private String secretKey;

    private final ExternalApiClient externalApiClient;

    /**
     * reCAPTCHA 토큰 검증
     * Google siteverify API를 호출하여 success 여부 확인
     *
     * @param token FE에서 전달받은 reCAPTCHA 토큰
     */
    public void verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RECAPTCHA_REQUIRED",
                "reCAPTCHA 인증을 완료해주세요.");
        }

        String url = String.format(VERIFY_URL, secretKey, token);
        ApiCallRequest request = ApiCallRequest.post(url).build();

        @SuppressWarnings("unchecked")
        ApiCallResult<Map> result = externalApiClient.call(request, Map.class);

        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getData().get("success"))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RECAPTCHA_FAILED",
                "reCAPTCHA 인증에 실패했습니다. 다시 시도해주세요.");
        }
    }
}

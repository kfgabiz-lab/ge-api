package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.CtpContactUsPayload;
import com.ge.bo.dto.CtpContactUsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * CTP(Connect Portal) 문의 접수 전송 공통 클라이언트
 * - ContactUsService(구 CTP 전용 전송)와 ContactUsInquiryService(신규 폼 → CTP 연동)가
 *   동일한 저수준 전송/인증재시도/응답해석 로직을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtpContactUsClient {

    private final CtpAuthService ctpAuthService;
    private final ExternalApiClient externalApiClient;
    private final CtpProperties ctpProperties;

    /** CTP 문의 접수 전송 실행 — 401(토큰 만료) 시 토큰 재발급 후 1회 재시도, 실패 시 Status="E"로 반환 */
    public CtpContactUsResult send(CtpContactUsPayload payload) {
        ApiCallResult<CtpContactUsResult> result = callWithAuthRetry(
                token -> postCtp(payload, token));

        if (!result.isSuccess() || result.getData() == null) {
            log.warn("CTP 문의 접수 전송 실패 statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());
            return new CtpContactUsResult("E", "ERROR", "Connect Portal 전송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        return result.getData();
    }

    /** CTP 응답 Status 코드를 프론트에 보여줄 안내 문구로 변환 */
    public String resolveMessage(CtpContactUsResult result) {
        if (isBlank(result.status())) {
            return result.returnMessage() != null ? result.returnMessage() : "문의 접수 처리 중 오류가 발생했습니다.";
        }
        return switch (result.status()) {
            case "S" -> "문의가 정상적으로 접수되었습니다.";
            case "X" -> "예외가 발생했습니다.";
            case "D" -> "이미 접수된 문의입니다.";
            default -> result.returnMessage() != null ? result.returnMessage() : "문의 접수 처리 중 오류가 발생했습니다.";
        };
    }

    /** CTP 호출 후 401(토큰 만료) 응답 시 토큰 재발급 후 1회 재시도하는 공통 로직 */
    private ApiCallResult<CtpContactUsResult> callWithAuthRetry(
            Function<String, ApiCallResult<CtpContactUsResult>> requester) {
        ApiCallResult<CtpContactUsResult> result = requester.apply(ctpAuthService.getAccessToken());
        if (!result.isSuccess() && result.getStatusCode() == 401) {
            result = requester.apply(ctpAuthService.refreshAccessToken());
        }
        return result;
    }

    /** CTP REST 엔드포인트로 실제 HTTP POST 요청 */
    private ApiCallResult<CtpContactUsResult> postCtp(CtpContactUsPayload payload, String accessToken) {
        ApiCallRequest request = ApiCallRequest.post(ctpProperties.getApiUrl())
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(payload)
                .build();
        return externalApiClient.call(request, CtpContactUsResult.class);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

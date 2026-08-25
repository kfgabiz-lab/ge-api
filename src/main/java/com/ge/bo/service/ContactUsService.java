package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.common.crypto.Aes256Utils;
import com.ge.bo.dto.ContactUsDetailRequest;
import com.ge.bo.dto.ContactUsDetailResponse;
import com.ge.bo.dto.CtpContactUsDetailPayload;
import com.ge.bo.dto.CtpContactUsDetailResult;
import com.ge.bo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * Contact Us 문의결과조회 서비스 (IF_SRR_NAHP_CTP_0002)
 * 접수번호 + 비밀번호로 CTP(Salesforce) 조회해 진행상태/답변을 반환한다
 * ※ 문의 접수(submit)는 신규 폼(ContactUsInquiryService, /api/v1/fo/contact-us)으로 일원화되어
 *   이 서비스에서는 제거됨 — FE에서 호출하지 않는 미사용 엔드포인트였음(2026-08-25 정리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactUsService {

    private final Aes256Utils cryptoUtil;
    private final CtpAuthService ctpAuthService;
    private final ExternalApiClient externalApiClient;
    private final CtpProperties ctpProperties;

    /**
     * 문의 결과 조회 (IF_SRR_NAHP_CTP_0002)
     * 접수번호 + 비밀번호(평문 입력 → 암호화)로 조회해 진행상태/답변을 반환한다
     */
    public ContactUsDetailResponse getDetail(ContactUsDetailRequest req) {
        CtpContactUsDetailPayload payload = new CtpContactUsDetailPayload(
                req.caseNumber(),
                cryptoUtil.encrypt(req.password()));

        CtpContactUsDetailResult result = callCtpDetail(payload);
        return ContactUsDetailResponse.from(result);
    }

    /**
     * CTP 조회 호출 실행 — 401(토큰 만료) 시 토큰 재발급 후 1회 재시도
     * CTP는 접수번호/비밀번호 불일치 시에도 HTTP 200 + 전 필드 null 바디로 응답하므로,
     * HTTP 실패뿐 아니라 status 값이 비어있는 경우도 조회 실패로 간주한다
     */
    private CtpContactUsDetailResult callCtpDetail(CtpContactUsDetailPayload payload) {
        ApiCallResult<CtpContactUsDetailResult> result = callWithAuthRetry(
                token -> postCtp(ctpProperties.getDetailApiUrl(), payload, token, CtpContactUsDetailResult.class));

        if (!result.isSuccess() || result.getData() == null
                || isBlank(result.getData().status()) || result.getData().data() == null) {
            log.warn("CTP 문의결과조회 실패 statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());
            throw BusinessException.notFound("접수번호 또는 비밀번호를 확인해주세요.");
        }
        return result.getData();
    }

    /** CTP 호출 후 401(토큰 만료) 응답 시 토큰 재발급 후 1회 재시도하는 공통 로직 */
    private <T> ApiCallResult<T> callWithAuthRetry(Function<String, ApiCallResult<T>> requester) {
        ApiCallResult<T> result = requester.apply(ctpAuthService.getAccessToken());
        if (!result.isSuccess() && result.getStatusCode() == 401) {
            result = requester.apply(ctpAuthService.refreshAccessToken());
        }
        return result;
    }

    /** CTP REST 엔드포인트로 실제 HTTP POST 요청 */
    private <T> ApiCallResult<T> postCtp(String url, Object payload, String accessToken, Class<T> responseType) {
        ApiCallRequest request = ApiCallRequest.post(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(payload)
                .build();
        return externalApiClient.call(request, responseType);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

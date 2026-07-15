package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.common.crypto.Aes256Utils;
import com.ge.bo.dto.ContactUsDetailRequest;
import com.ge.bo.dto.ContactUsDetailResponse;
import com.ge.bo.dto.CtpContactUsDetailPayload;
import com.ge.bo.dto.CtpContactUsDetailResult;
import com.ge.bo.dto.CtpContactUsPayload;
import com.ge.bo.dto.CtpContactUsResult;
import com.ge.bo.dto.ContactUsRequest;
import com.ge.bo.dto.ContactUsResponse;
import com.ge.bo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contact Us(북미 홈페이지 문의 접수) 서비스 (IF_SRR_NAHP_CTP_0001)
 * 문의 내용은 DB에 저장하지 않고 Connect Portal(Salesforce)로 동기 전송만 하며, 처리 결과는 로그로 남긴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactUsService {

    private static final DateTimeFormatter INQUIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SUBJECT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 북미 접수일시 기준 시간대 — 필요 시 지역별 타임존으로 조정
    private static final ZoneId NAHP_ZONE = ZoneId.of("America/New_York");

    private final Aes256Utils cryptoUtil;
    private final CtpAuthService ctpAuthService;
    private final ExternalApiClient externalApiClient;
    private final CtpProperties ctpProperties;

    /**
     * 문의 접수 처리
     * 입력값 검증 → CTP(Salesforce) 동기 전송 → 처리 결과 로그 기록 → 응답 반환
     * (문의 내용은 DB에 저장하지 않음 — Salesforce가 시스템 오브 레코드)
     */
    public ContactUsResponse submit(ContactUsRequest req) {
        validate(req);

        OffsetDateTime inquiryDateTime = OffsetDateTime.now(NAHP_ZONE);
        ExceptionRouting routing = ExceptionRouting.resolve(req.productCategoryLv1Id(), req.productCategoryLv2Id());

        CtpContactUsPayload payload = buildPayload(req, inquiryDateTime, routing);
        CtpContactUsResult result = callCtp(payload);

        // 개인정보(이메일/이름/문의내용 등)는 로그에 남기지 않고, 추적용 결과값만 기록
        log.info("Contact Us 접수 처리 완료 - type={}, status={}, code={}", req.type(), result.status(), result.returnCode());

        return ContactUsResponse.of(result.status(), resolveMessage(result));
    }

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

    /** 비밀번호 일치 여부, Product Category 필수 여부(Others 제외) 검증 (fo 더블체크) */
    private void validate(ContactUsRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw BusinessException.badRequest("비밀번호가 일치하지 않습니다.");
        }
        // Product Category 는 Others 유형을 제외하고 필수 (폼 상 필수 선택 항목)
        if (!"Others".equals(req.type()) && isBlank(req.productCategoryLv1())) {
            throw BusinessException.badRequest("제품 카테고리를 선택해주세요.");
        }
    }

    /** IF_SRR_NAHP_CTP_0001 Target 필드 규칙에 맞춰 CTP 전송 페이로드 조립 (Subject/ProductCategory/RequesterName 가공 포함) */
    private CtpContactUsPayload buildPayload(ContactUsRequest req, OffsetDateTime inquiryDateTime, ExceptionRouting routing) {

        // Subject - 문의명 텍스트 가공 ("[LS ELECTRIC America][{북미접수일}]" + {inquiryType})
        String subject = "[LS ELECTRIC America][" + inquiryDateTime.format(SUBJECT_DATE_FORMAT) + "] " + req.type();

        // ProductCategory - 북미 제품 레벨 가공 ( {Lv1 Category} + " | " + {Lv2 Category} + " | " + {Lv3 Category} )
        String productCategory = Stream.of(req.productCategoryLv1(), req.productCategoryLv2(), req.productCategoryLv3())
                .filter(v -> !isBlank(v))
                .collect(Collectors.joining(" | "));

        return new CtpContactUsPayload(
                req.type(),
                // TODO: 선택된 제품의 담당자 이메일을 조회해 "@" 앞부분을 반환하도록 구현
                //       (담당자 이메일 조회 소스 — 코드 테이블 등 확정되면 연결, 현재는 미전송)
                null,
                subject,
                productCategory.isEmpty() ? null : productCategory,
                // TODO: Email/RequesterName 암호화 key/iv 확인필요
                cryptoUtil.encrypt(req.email()),
                cryptoUtil.encrypt(requesterName(req)),
                req.companyName(),
                req.country(),
                req.description(),
                cryptoUtil.encrypt(req.password()),
                req.marketingOptInFlag(),
                inquiryDateTime.format(INQUIRY_DATE_FORMAT),
                routing.flag(),
                routing.email());
    }

    /** CTP 호출 실행 — 401(토큰 만료) 시 토큰 재발급 후 1회 재시도 후 실패 시 Status="E"로 응답 */
    private CtpContactUsResult callCtp(CtpContactUsPayload payload) {
        ApiCallResult<CtpContactUsResult> result = callWithAuthRetry(
                token -> postCtp(ctpProperties.getApiUrl(), payload, token, CtpContactUsResult.class));

        if (!result.isSuccess() || result.getData() == null) {
            log.warn("CTP 문의 접수 전송 실패 statusCode={} error={}", result.getStatusCode(), result.getErrorMessage());
            return new CtpContactUsResult("E", "ERROR", "Connect Portal 전송에 실패했습니다. 잠시 후 다시 시도해주세요.");
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

    /** CTP 응답 Status 코드를 프론트에 보여줄 안내 문구로 변환 */
    private String resolveMessage(CtpContactUsResult result) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** RequesterName 가공 ( {First Name} + " " + {Last Name} ) */
    private String requesterName(ContactUsRequest req) {
        return req.firstName() + " " + req.lastName();
    }

    /**
     * ProductCategory Lv1 코드="L06"(Software) 하위 Lv2 코드에 따른 문의 예외 처리
     * - Lv2 = L06-01(SCADA) / L06-02(xEMS) / L06-03(Micro Grid) → usnascada@ls-electric.com
     * - Lv2 = L06-04(Smart Factory) → smartfactory.america@ls-electric.com
     */
    private record ExceptionRouting(Boolean flag, String email) {
        private static final String LV1_SOFTWARE = "L06";
        private static final java.util.Set<String> SCADA_GROUP = java.util.Set.of("L06-01", "L06-02", "L06-03");
        private static final String SMART_FACTORY = "L06-04";

        static ExceptionRouting resolve(String productCategoryLv1Id, String productCategoryLv2Id) {
            if (!LV1_SOFTWARE.equals(productCategoryLv1Id)) {
                return new ExceptionRouting(false, null);
            }
            if (SCADA_GROUP.contains(productCategoryLv2Id)) {
                return new ExceptionRouting(true, "usnascada@ls-electric.com");
            }
            if (SMART_FACTORY.equals(productCategoryLv2Id)) {
                return new ExceptionRouting(true, "smartfactory.america@ls-electric.com");
            }
            return new ExceptionRouting(false, null);
        }
    }
}

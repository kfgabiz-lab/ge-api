package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.crypto.Aes256Utils;
import com.ge.bo.dto.ContactUsInquiryRequest;
import com.ge.bo.dto.ContactUsInquiryResponse;
import com.ge.bo.dto.CtpContactUsPayload;
import com.ge.bo.dto.CtpContactUsResult;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * FO Contact Us 문의 접수 서비스
 * - 공통코드(INQUIRY_TYPE/COUNTRYCODE) 실시간 검증 → CTP(Salesforce) 전송
 * - DB 저장(contact_us_inquiry)은 2026-07-23부로 비활성화(주석 처리) — 추후 재사용 대비 보존
 * ※ 기존 CTP 전용 ContactUsService와는 별개 클래스지만, 실제 CTP 전송은 CtpContactUsClient를 공유한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactUsInquiryService {

    /** 공통코드 그룹 코드 — 문의유형 */
    private static final String GROUP_INQUIRY_TYPE = "INQUIRY_TYPE";
    /** 공통코드 그룹 코드 — 국가(코드값 자체는 ISO 3166-1 alpha-2라 CTP Country로 그대로 사용 가능) */
    private static final String GROUP_COUNTRY = "COUNTRYCODE";

    /** 신규 폼(UPPER_SNAKE_CASE) → CTP(PascalCase) 문의유형 매핑 */
    private static final Map<String, String> TYPE_TO_CTP = Map.of(
            "PRODUCT_INFORMATION", "ProductInformation",
            "QUOTATION_REQUEST", "QuotationRequest",
            "PURCHASE", "Purchase",
            "OTHERS", "Others");

    private static final DateTimeFormatter INQUIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SUBJECT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // private final ContactUsInquiryRepository contactUsInquiryRepository; // DB 저장 비활성화(2026-07-23) — 재사용 대비 보존
    private final CodeDetailRepository codeDetailRepository;
    // private final PasswordEncoder passwordEncoder; // DB 저장 비활성화와 함께 미사용 — 재사용 대비 보존
    private final Aes256Utils cryptoUtil;
    private final PageDataService pageDataService;
    private final CtpContactUsClient ctpContactUsClient;
    private final SiteTimeZoneResolver siteTimeZoneResolver;

    /**
     * 문의 접수 처리 — 공통코드 검증 → CTP(Salesforce) 전송 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 X-Forwarded-For 우선 추출) — DB 저장 비활성화로 현재 미사용
     * @param siteId   X-Site-Id 헤더(devices-tree/productManager-data 조회 시 site 필터링용)
     * @return CTP 전송 결과
     */
    @Transactional
    public ContactUsInquiryResponse submit(ContactUsInquiryRequest request, String clientIp, Long siteId) {

        // 1) 공통코드 검증 — 활성 코드값만 통과 (BO에서 코드 추가/비활성해도 소스 수정 불필요)
        if (!codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_INQUIRY_TYPE, request.type())) {
            throw BusinessException.badRequest("유효하지 않은 문의 유형입니다.");
        }
        if (!codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_COUNTRY, request.country())) {
            throw BusinessException.badRequest("유효하지 않은 국가입니다.");
        }

        // 2) 비밀번호 교차검증 (confirmPassword는 저장하지 않고 일치 여부만 확인)
        if (!request.password().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("비밀번호가 일치하지 않습니다.");
        }

        // ---- DB 저장 비활성화(2026-07-23) — 추후 필요 시 재사용 가능하도록 주석으로 보존 ----
        // String passwordHash = passwordEncoder.encode(request.password());
        // ContactUsInquiry entity = ContactUsInquiry.builder()
        //         .inquiryType(request.type())
        //         .productCategory(request.productCategory())
        //         .email(request.email())
        //         .firstName(request.firstName())
        //         .lastName(request.lastName())
        //         .companyName(request.companyName())
        //         .country(request.country())
        //         .inquiryContent(request.description())
        //         .passwordHash(passwordHash)
        //         .marketingOptInFlag(request.marketingOptInFlag())
        //         .privacyConsentFlag(request.privacyConsentFlag())
        //         .createdIp(clientIp)
        //         .build();
        // ContactUsInquiry saved = contactUsInquiryRepository.save(entity);
        // log.info("Contact Us 문의 접수 저장 완료 - id={}", saved.getId());
        // ---------------------------------------------------------------------------

        // 3) CTP(Salesforce) 전송 — 접수일시는 요청 사이트(X-Site-Id)의 timezone 기준(없으면 서버 기본 zone)
        OffsetDateTime inquiryDateTime = OffsetDateTime.now(siteTimeZoneResolver.resolve(siteId));
        ExceptionRouting routing = ExceptionRouting.resolve(request.productCategory());
        String productInformationInquiryType = resolveProductInformationInquiryType(request.productId(), siteId);

        CtpContactUsPayload payload = buildCtpPayload(request, inquiryDateTime, routing, productInformationInquiryType);
        CtpContactUsResult result = ctpContactUsClient.send(payload);

        // 개인정보(이메일/이름/문의내용 등)는 로그에 남기지 않고, 추적용 결과값만 기록
        log.info("Contact Us 문의 접수 CTP 전송 완료 - type={}, status={}, code={}, returnMessage={}",
                request.type(), result.status(), result.returnCode(), result.returnMessage());

        boolean success = "S".equals(result.status());
        return new ContactUsInquiryResponse(success, null, ctpContactUsClient.resolveMessage(result));
    }

    /** IF_SRR_NAHP_CTP_0001 Target 필드 규칙에 맞춰 CTP 전송 페이로드 조립 */
    private CtpContactUsPayload buildCtpPayload(ContactUsInquiryRequest request, OffsetDateTime inquiryDateTime,
                                                 ExceptionRouting routing, String productInformationInquiryType) {
        String ctpType = TYPE_TO_CTP.getOrDefault(request.type(), request.type());
        String subject = "[LS ELECTRIC America][" + inquiryDateTime.format(SUBJECT_DATE_FORMAT) + "] " + ctpType;

        return new CtpContactUsPayload(
                ctpType,
                productInformationInquiryType,
                subject,
                isBlank(request.productCategory()) ? null : request.productCategory(),
                cryptoUtil.encrypt(request.email()),
                cryptoUtil.encrypt(request.firstName() + " " + request.lastName()),
                request.companyName(),
                request.country(),
                request.description(),
                cryptoUtil.encrypt(request.password()),
                request.marketingOptInFlag(),
                inquiryDateTime.format(INQUIRY_DATE_FORMAT),
                routing.flag(),
                routing.email());
    }

    /**
     * Lv3(제품) product-data id로 담당자 이메일을 조회해 "@" 앞부분을 반환 (없으면 null)
     */
    private String resolveProductInformationInquiryType(Long productId, Long siteId) {
        if (productId == null) {
            return null;
        }
        return pageDataService.findProductManagerEmail(productId, siteId)
                .filter(email -> email.contains("@"))
                .map(email -> email.substring(0, email.indexOf('@')))
                .orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * productCategory("Lv1 | Lv2 | Lv3") 라벨 기준 문의 예외 처리 판정
     * - Lv1 = "Software" 이고 Lv2 = "SCADA"/"xEMS"/"Micro Grid" → usnascada@ls-electric.com
     * - Lv1 = "Software" 이고 Lv2 = "Smart Factory" → smartfactory.america@ls-electric.com
     * (구 CTP 서비스는 "L06"/"L06-01" 같은 코드값으로 판정했으나, 신규 폼은 코드값이 없어 devices-tree 실제 카테고리 라벨로 판정한다)
     */
    private record ExceptionRouting(Boolean flag, String email) {
        private static final String LV1_SOFTWARE = "Software";
        private static final Set<String> SCADA_GROUP = Set.of("SCADA", "xEMS", "Micro Grid");
        private static final String SMART_FACTORY = "Smart Factory";

        static ExceptionRouting resolve(String productCategory) {
            if (productCategory == null || productCategory.isBlank()) {
                return new ExceptionRouting(false, null);
            }
            String[] labels = productCategory.split("\\s*\\|\\s*");
            String lv1 = labels.length > 0 ? labels[0] : null;
            String lv2 = labels.length > 1 ? labels[1] : null;

            if (!LV1_SOFTWARE.equals(lv1)) {
                return new ExceptionRouting(false, null);
            }
            if (SCADA_GROUP.contains(lv2)) {
                return new ExceptionRouting(true, "usnascada@ls-electric.com");
            }
            if (SMART_FACTORY.equals(lv2)) {
                return new ExceptionRouting(true, "smartfactory.america@ls-electric.com");
            }
            return new ExceptionRouting(false, null);
        }
    }
}

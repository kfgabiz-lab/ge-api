package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.crypto.Aes256Utils;
import com.ge.bo.dto.ContactUsInquiryRequest;
import com.ge.bo.dto.ContactUsInquiryResponse;
import com.ge.bo.dto.CtpContactUsPayload;
import com.ge.bo.dto.CtpContactUsResult;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.CodeDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * FO Contact Us 문의 접수 서비스
 * - 문의유형(CTP picklist 고정값)/공통코드(COUNTRYCODE) 검증 → CTP(Salesforce) 전송
 * - DB 저장(contact_us_inquiry)은 2026-07-23부로 비활성화(주석 처리) — 추후 재사용 대비 보존
 * ※ 기존 CTP 전용 ContactUsService와는 별개 클래스지만, 실제 CTP 전송은 CtpContactUsClient를 공유한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactUsInquiryService {

    /** 공통코드 그룹 코드 — 국가(코드값 자체는 ISO 3166-1 alpha-2라 CTP Country로 그대로 사용 가능) */
    private static final String GROUP_COUNTRY = "COUNTRYCODE";
    /** productCategory("Lv1 | Lv2 | Lv3") Lv1 라벨 — Software 여부에 따라 담당자 이메일을 보낼 CTP 필드가 갈린다 */
    private static final String LV1_SOFTWARE = "Software";

    /**
     * 문의유형
     */
    private static final Set<String> VALID_INQUIRY_TYPES =
            Set.of("ProductInformation", "QuotationRequest", "Purchase", "Others");

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
     * @param clientIp 요청자 IP (컨트롤러에서 getRemoteAddr() 기준 추출) — DB 저장 비활성화로 현재 미사용
     * @param siteId   X-Site-Id 헤더(devices-tree/productManager-data 조회 시 site 필터링용)
     * @return CTP 전송 결과
     */
    @Transactional
    public ContactUsInquiryResponse submit(ContactUsInquiryRequest request, String clientIp, Long siteId) {

        // 1) 입력값 검증 — 문의유형은 고정 4개 값(VALID_INQUIRY_TYPES), 국가는 공통코드(활성 코드값만 통과, BO에서 코드 추가/비활성해도 소스 수정 불필요)
        if (!VALID_INQUIRY_TYPES.contains(request.type())) {
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
        // 담당자가 제품에 2건 이상 등록될 수 있어 findProductManagerEmail()이 created_at DESC로 최신 1건만 반환한다
        String managerEmail = request.productId() == null
                ? null
                : pageDataService.findProductManagerEmail(request.productId(), siteId).orElse(null);

        // Software 제품 → 담당자 이메일 그대로 InquiryProcessingExceptionEmail로 전송
        // Software 외 제품 → 담당자 이메일의 "@" 앞부분만 ProductInformationInquiryType으로 전송
        boolean isSoftware = isSoftwareCategory(request.productCategory());
        String exceptionRoutingEmail = isSoftware ? managerEmail : null;
        boolean isSoftwareException = exceptionRoutingEmail != null;
        String productInformationInquiryType = (!isSoftware && managerEmail != null && managerEmail.contains("@"))
                ? managerEmail.substring(0, managerEmail.indexOf('@')).trim()
                : null;

        CtpContactUsPayload payload = buildCtpPayload(request, inquiryDateTime, isSoftwareException, exceptionRoutingEmail, productInformationInquiryType);
        CtpContactUsResult result = ctpContactUsClient.send(payload);

        // 개인정보(이메일/이름/문의내용 등)는 로그에 남기지 않고, 추적용 결과값만 기록
        log.info("Contact Us 문의 접수 CTP 전송 완료 - type={}, status={}, code={}, returnMessage={}",
                request.type(), result.status(), result.returnCode(), result.returnMessage());

        if (!"S".equals(result.status())) {
            throw new BusinessException(ErrorCode.CTP_SUBMIT_FAILED.getStatus(), ErrorCode.CTP_SUBMIT_FAILED.getCode(),
                    ctpContactUsClient.resolveMessage(result));
        }
        return ContactUsInquiryResponse.success(null);
    }

    /** IF_SRR_NAHP_CTP_0001 Target 필드 규칙에 맞춰 CTP 전송 페이로드 조립 */
    private CtpContactUsPayload buildCtpPayload(ContactUsInquiryRequest request, OffsetDateTime inquiryDateTime,
                                                 boolean isSoftwareException, String routingEmail,
                                                 String productInformationInquiryType) {
        String subject = "[LS ELECTRIC America][" + inquiryDateTime.format(SUBJECT_DATE_FORMAT) + "] " + request.type();

        return new CtpContactUsPayload(
                request.type(),
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
                isSoftwareException,
                routingEmail);
    }

    /** productCategory("Lv1 | Lv2 | Lv3")의 Lv1이 Software인지 판정 */
    private boolean isSoftwareCategory(String productCategory) {
        if (isBlank(productCategory)) {
            return false;
        }
        String lv1 = productCategory.split("\\s*\\|\\s*")[0];
        return LV1_SOFTWARE.equals(lv1);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

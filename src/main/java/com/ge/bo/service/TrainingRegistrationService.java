package com.ge.bo.service;

import com.ge.bo.common.mail.MailSendEvent;
import com.ge.bo.dto.TrainingRegistrationRequest;
import com.ge.bo.dto.TrainingRegistrationResponse;
import com.ge.bo.entity.CodeDetail;
import com.ge.bo.entity.TrainingRegistration;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.TrainingRegistrationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * FO 트레이닝 세션 등록(리드 캡처) 접수 서비스
 * - 처리 순서: reCAPTCHA 검증 → typeOfBusiness 공통코드(BUSINESSTYPE) 검증(값 있을 때만) → insert
 * - ContactUsInquiryService 의 레이어 구조를 그대로 본떠 작성(공통코드 검증도 동일 메서드 재사용).
 * - 세션/코스 id 의 부모-자식 일치 검증은 하지 않는다(과설계 방지, 사용자 확정).
 * - 메일 발송은 신청자에게 신청확인 메일, 담당자(EMAIL_RECIPIENT 공통코드)에게 신청접수 알림 메일 — 각자에게 개별 발송한다.
 *   담당자는 Sales/Service 는 단일 코드, Engineering 은 세션의 Automation/Power(양산·수주) 제품 구성에 따라 발송한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRegistrationService {

    /** 공통코드 그룹 코드 — 비즈니스 유형(기존 그룹 재사용) */
    private static final String GROUP_BUSINESS_TYPE = "BUSINESSTYPE";

    /** 신청 세션(currDtlMgmt-data)의 신청수 카운트 증가 대상 slug */
    private static final String SESSION_SLUG = "currDtlMgmt-data";

    /** 커리큘럼(부모) slug — training_course(공통코드 TRAININGCOURSE) 조회용 */
    private static final String CURRICULUM_SLUG = "currMgmt-data";

    /** 공통코드 EMAILSENDTYPE — 정기 Training(세션 상세 Registration Form) */
    private static final String EMAIL_SEND_TYPE_REGULAR_TRAINING = "02";

    /** 담당자 이메일 공통코드 그룹 (name 필드에 수신 이메일 저장, 콤마로 복수 가능) */
    private static final String GROUP_EMAIL_RECIPIENT = "EMAIL_RECIPIENT";

    /** TRAININGCOURSE 코드 */
    private static final String TRAINING_COURSE_ENGINEERING = "01";
    private static final String TRAINING_COURSE_SERVICE = "02";
    private static final String TRAINING_COURSE_SALES = "03";

    /** EMAIL_RECIPIENT 담당자 코드 — Engineering 은 선택 제품(Power 양산/수주, Automation)에 따라 여러 곳에 동시 발송될 수 있다 */
    private static final String RECIPIENT_CODE_SALES = "TRAINING_SALES";
    private static final String RECIPIENT_CODE_SERVICE = "TRAINING_SERVICE";
    private static final String RECIPIENT_CODE_ENGINEERING_AUTO = "TRAINING_ENGINNERING_AUTO";
    private static final String RECIPIENT_CODE_ENGINEERING_POWER_DEVICE = "TRAINING_ENGINNERING_POWER_D";
    private static final String RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM = "TRAINING_ENGINNERING_POWER_S";

    /** VFD 제품 product_code(L01-15-01~06, LV Products & Systems > Variable Frequency Drive 6종) */
    private static final List<String> VFD_PRODUCT_CODES = List.of(
            "L01-15-01", "L01-15-02", "L01-15-03", "L01-15-04", "L01-15-05", "L01-15-06");

    private static final DateTimeFormatter EVENT_DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TrainingRegistrationRepository trainingRegistrationRepository;
    private final CodeDetailRepository codeDetailRepository;
    private final RecaptchaService recaptchaService;
    private final PageDataService pageDataService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    /** 메일 헤더 로고 이미지 — FO 사이트에 호스팅된 절대 URL(메일 클라이언트는 상대경로 이미지를 로드할 수 없다) */
    @Value("${app.mail.logo-url}")
    private String logoUrl;

    /**
     * 등록 접수 처리 — reCAPTCHA 검증 → 코드 검증 → 저장 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 getRemoteAddr() 기준 추출)
     * @return 저장 성공 결과(success/id/message)
     */
    @Transactional
    public TrainingRegistrationResponse submit(TrainingRegistrationRequest request, String clientIp) {

        // 1) reCAPTCHA 검증 (실패 시 BusinessException 발생 → 400)
        recaptchaService.verify(request.recaptchaToken());

        // 2) 비즈니스 유형 검증 — 선택 필드이므로 값이 있을 때만 활성 코드값인지 확인(기존 메서드 재사용)
        if (StringUtils.isNotBlank(request.typeOfBusiness())
                && !codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(
                        GROUP_BUSINESS_TYPE, request.typeOfBusiness())) {
            throw BusinessException.badRequest("유효하지 않은 비즈니스 유형입니다.");
        }

        // 3) 참석 일자 파싱("yyyy-MM-dd")
        LocalDate eventDate = parseEventDate(request.eventDate());

        // 4) 저장 (append-only)
        TrainingRegistration entity = TrainingRegistration.builder()
                .curriculumId(request.curriculumId())
                .sessionId(request.sessionId())
                .studentName(request.studentName())
                .email(request.email())
                .jobTitle(request.jobTitle())
                .phone(request.phone())
                .companyName(request.companyName())
                .eventDate(eventDate)
                .streetAddress(blankToNull(request.streetAddress()))
                .address2(blankToNull(request.address2()))
                .apartment(blankToNull(request.apartment()))
                .city(blankToNull(request.city()))
                .stateProvince(blankToNull(request.stateProvince()))
                .zipCode(blankToNull(request.zipCode()))
                .typeOfBusiness(blankToNull(request.typeOfBusiness()))
                .privacyConsentFlag(request.privacyConsentFlag())
                .createdIp(clientIp)
                .build();

        TrainingRegistration saved = trainingRegistrationRepository.save(entity);

        pageDataService.incrementViewCount(SESSION_SLUG, request.sessionId(), null);

        // 개인정보(이름/이메일 등)는 로그에 남기지 않고 추적용 식별값만 기록
        log.info("Training 세션 등록 접수 저장 완료 - id={}, curriculumId={}, sessionId={}",
                saved.getId(), saved.getCurriculumId(), saved.getSessionId());

        // 신청자에게는 신청확인 메일, 담당자에게는 신청접수 알림 메일 — 각자에게 개별 발송(한 메일에 여러 수신자를 넣지 않는다)
        // 발송 이력 저장(성공/실패 모두)은 MailService 가 각 발송 건마다 공통으로 처리
        // 실제 발송은 트랜잭션 커밋 후 MailSendEventListener 가 비동기로 수행(요청 스레드/DB 커넥션 점유 방지)
        String trainingCourseCode = findTrainingCourseCode(request.curriculumId());
        SessionEmailContext ctx = resolveSessionEmailContext(request.curriculumId(), request.sessionId());

        String customerSubject = "Registration Confirmed: %s-%s".formatted(nz(ctx.sessionTitle()), request.studentName());
        eventPublisher.publishEvent(new MailSendEvent(request.email(), customerSubject, buildCustomerEmail(request, ctx),
                EMAIL_SEND_TYPE_REGULAR_TRAINING, trainingCourseCode, null));

        List<String> recipientCodes = resolveRecipientCodes(trainingCourseCode, ctx);
        if (!recipientCodes.isEmpty()) {
            String adminSubject = "[%s]%s - %s".formatted(nz(ctx.trainingCourseName()), request.studentName(), nz(ctx.sessionTitle()));
            for (String recipientCode : recipientCodes) {
                List<String> managerEmails = resolveManagerEmails(List.of(recipientCode));
                // 담당자 이메일이 미설정이어도 발송 시도 자체는 이력에 남긴다(recipient 자리에 코드명을 넣어 실패건으로 기록 —
                // 조용히 건너뛰면 "왜 담당자 메일이 안 왔는지"를 이력만 보고 추적할 수 없다).
                List<String> recipients = managerEmails.isEmpty() ? List.of(recipientCode) : managerEmails;
                String adminContent = buildAdminEmail(request, ctx, eventDate, recipientCode);
                for (String recipient : recipients) {
                    eventPublisher.publishEvent(new MailSendEvent(recipient, adminSubject, adminContent,
                            EMAIL_SEND_TYPE_REGULAR_TRAINING, trainingCourseCode, null));
                }
            }
        }

        return TrainingRegistrationResponse.success(saved.getId());
    }

    /**
     * TRAININGCOURSE 코드 + 선택 제품 구성(Engineering 만 해당)에 따라 알림 보낼 EMAIL_RECIPIENT 코드 목록을 결정.
     * Engineering 은 세션에 Automation 제품과 Power(양산/수주) 제품이 함께 있으면 해당되는 모든 담당자에게 각각 발송한다.
     */
    private List<String> resolveRecipientCodes(String trainingCourseCode, SessionEmailContext ctx) {
        if (TRAINING_COURSE_SERVICE.equals(trainingCourseCode)) {
            return List.of(RECIPIENT_CODE_SERVICE);
        }
        if (TRAINING_COURSE_SALES.equals(trainingCourseCode)) {
            return List.of(RECIPIENT_CODE_SALES);
        }
        if (TRAINING_COURSE_ENGINEERING.equals(trainingCourseCode)) {
            List<String> codes = new ArrayList<>();
            if (ctx.hasAutomation()) {
                codes.add(RECIPIENT_CODE_ENGINEERING_AUTO);
            }
            if (ctx.hasPowerDevice()) {
                codes.add(RECIPIENT_CODE_ENGINEERING_POWER_DEVICE);
            }
            if (ctx.hasPowerSystem()) {
                codes.add(RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM);
            }
            return codes;
        }
        return List.of();
    }

    /**
     * EMAIL_RECIPIENT 코드 목록 → 수신 이메일(name) 조회 — 코드별 콤마 다중 저장을 개별 주소로 풀고, 코드 여러 개에 걸쳐 중복되면 한 번만 발송.
     * 코드가 없거나(curriculum 조회 실패) 담당자 코드가 미설정이면 빈 목록(신청자에게만 발송).
     */
    private List<String> resolveManagerEmails(List<String> recipientCodes) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String code : recipientCodes) {
            codeDetailRepository.findByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_EMAIL_RECIPIENT, code)
                    .map(CodeDetail::getName)
                    .filter(StringUtils::isNotBlank)
                    .ifPresent(value -> emails.addAll(splitEmails(value)));
        }
        return new ArrayList<>(emails);
    }

    // TODO: 테스트용 — EMAIL_RECIPIENT 라우팅 분기가 실주소로 정상 검증되면 teamLabel/debugRecipientBanner 제거하고
    // buildAdminEmail 인삿말도 "Hi Training Team,"으로 되돌린다.
    /** EMAIL_RECIPIENT 코드 → 관리자 메일 인삿말 표시용 라벨("Hi {label} Team," 형태로 사용) */
    private String teamLabel(String recipientCode) {
        return switch (recipientCode) {
            case RECIPIENT_CODE_SALES -> "Sales Training";
            case RECIPIENT_CODE_SERVICE -> "Service Training";
            case RECIPIENT_CODE_ENGINEERING_AUTO -> "Engineering Training (Automation)";
            case RECIPIENT_CODE_ENGINEERING_POWER_DEVICE -> "Engineering Training (Power - Device)";
            case RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM -> "Engineering Training (Power - System)";
            default -> "Training";
        };
    }

    /** 담당자 라우팅 분기 확인용 디버그 배너 — 현재 테스트 수신 주소가 코드별로 구분되지 않아 메일 본문으로 확인한다 */
    private String debugRecipientBanner(String recipientCode) {
        return "<tr><td style=\"padding:8px 32px 0;font-family:Arial, Helvetica, sans-serif;font-size:12px;"
             + "font-weight:700;color:#c0392b;background:#fff3f3;\">[TEST] EMAIL_RECIPIENT code: "
             + escape(recipientCode) + "</td></tr>";
    }

    /** 콤마로 여러 명 저장된 이메일 문자열 → 개별 주소 리스트(공백 제거, 빈 값 제외) */
    private List<String> splitEmails(String emails) {
        return Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /**
     * 커리큘럼(currMgmt-data)의 training_course 코드(공통코드 TRAININGCOURSE) 조회 — 이메일 이력 상세분류/담당자 조회용.
     * 조회 실패해도 등록 저장 자체는 실패하면 안 되므로 예외를 던지지 않고 null 반환한다.
     */
    private String findTrainingCourseCode(Long curriculumId) {
        try {
            Query query = entityManager.createNativeQuery(
                    "SELECT data_json->'curriculum'->>'training_course'"
                  + " FROM page_data WHERE data_slug = :slug AND id = :id");
            query.setParameter("slug", CURRICULUM_SLUG);
            query.setParameter("id", curriculumId);

            @SuppressWarnings("unchecked")
            List<Object> rows = query.getResultList();
            return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toString();
        } catch (Exception e) {
            log.warn("커리큘럼 training_course 조회 실패 - curriculumId={}", curriculumId);
            return null;
        }
    }

    /**
     * 이메일 본문에 쓰이는 커리큘럼/세션 표시값 묶음.
     * 조회 실패 시(any 예외) 전부 null/false 인 빈 컨텍스트를 반환해 메일 발송 자체가 막히지 않게 한다.
     */
    private record SessionEmailContext(
            String trainingCourseName,   // "Engineering Training" 등 — 공통코드 TRAININGCOURSE 표시명
            String trainingTypeLabel,    // "In-Person" / "Virtual" / "In-Person, Virtual" — 공통코드 TRAININGTYPE 표시명(콤마 결합)
            boolean inPerson,            // training_type 에 In-Person(001) 포함 여부
            String sessionTitle,         // curriculum_detail2.title — "Registered Training"
            String location,             // curriculum_detail2.address_detail/address 결합 — "Training Location"
            String scheduleText,         // training_schedule 배열 → "Jul 14, 2026: 9:00 AM - 4:00 PM, ..." 형태
            String feeAmount,            // curriculum_detail3.training_fee(무료면 null) — "Registration Fee"
            boolean vfd,                 // automation_list 에 VFD 제품(L01-15-01~06) 포함 여부
            boolean hasAutomation,       // automation_list 비어있지 않음 — Engineering 담당자 중 Automation 그룹 발송 대상
            boolean hasPowerDevice,      // power_list 하위 제품 order_method 에 양산(02) 포함 — Engineering 담당자 중 Power Device 그룹 발송 대상
            boolean hasPowerSystem) {    // power_list 하위 제품 order_method 에 수주(01) 포함 — Engineering 담당자 중 Power System 그룹 발송 대상

        static SessionEmailContext empty() {
            return new SessionEmailContext(null, null, false, null, null, null, null, false, false, false, false);
        }
    }

    /**
     * 커리큘럼(currMgmt-data) + 세션(currDtlMgmt-data) 조인으로 이메일 표시값 일괄 조회.
     * CurrDtlExportService.getCurrDetailList() 의 조인/집계 패턴을 그대로 재사용.
     */
    private SessionEmailContext resolveSessionEmailContext(Long curriculumId, Long sessionId) {
        try {
            Query query = entityManager.createNativeQuery("""
                    SELECT
                      (SELECT cd.name FROM code_group cg JOIN code_detail cd ON cd.group_id = cg.id
                         WHERE cg.group_code = 'TRAININGCOURSE'
                           AND cd.code = trim(curr.data_json->'curriculum'->>'training_course')
                           AND cg.is_active = true AND cd.is_active = true) AS training_course_name,
                      (SELECT string_agg(cd.name, ', ' ORDER BY codes.ord)
                         FROM string_to_table(sess.data_json->'curriculum_detail1'->>'training_type', ',') WITH ORDINALITY AS codes(code, ord)
                         JOIN code_group cg ON cg.group_code = 'TRAININGTYPE'
                         JOIN code_detail cd ON cd.group_id = cg.id AND cd.code = trim(codes.code)
                           AND cg.is_active = true AND cd.is_active = true) AS training_type_label,
                      (sess.data_json->'curriculum_detail1'->>'training_type' LIKE '%001%') AS in_person,
                      sess.data_json->'curriculum_detail2'->>'title' AS session_title,
                      NULLIF(CONCAT_WS(', ',
                        NULLIF(TRIM(sess.data_json->'curriculum_detail2'->>'address_detail'), ''),
                        NULLIF(TRIM(sess.data_json->'curriculum_detail2'->>'address'), '')
                      ), '') AS location,
                      (SELECT string_agg(
                           to_char(NULLIF(item->>'date', '')::date, 'FMMon FMDD, YYYY') || ': ' ||
                           to_char(NULLIF(item->>'time_from', '')::time, 'FMHH12:MI AM') || ' - ' ||
                           to_char(NULLIF(item->>'time_to', '')::time, 'FMHH12:MI AM'),
                           E'\n' ORDER BY (item->>'date')::date, (item->>'time_from')::time)
                         FROM jsonb_array_elements(COALESCE(sess.data_json->'training_schedule', '[]'::jsonb)) AS item
                      ) AS schedule_text,
                      CASE WHEN sess.data_json->'curriculum_detail3'->>'training_fee_type' = '001' THEN NULL
                           ELSE NULLIF(btrim(sess.data_json->'curriculum_detail3'->>'training_fee'), '') END AS fee_amount,
                      EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(sess.data_json->'automation_list', '[]'::jsonb)) v
                        JOIN page_data cat ON cat.id = v::bigint AND cat.data_slug = 'category-data'
                        JOIN page_data prod ON prod.id = (cat.data_json->'product'->>'id')::bigint AND prod.data_slug = 'product-data'
                        WHERE prod.data_json->'product'->>'product_code' IN (:vfdCodes)
                      ) AS is_vfd,
                      (jsonb_array_length(COALESCE(sess.data_json->'automation_list', '[]'::jsonb)) > 0) AS has_automation,
                      -- power_list 항목은 depth=2 카테고리(자식 리프의 order_method 를 봐야 함)일 수도 있고,
                      -- product-data/category-data 리프 id 가 직접 들어있을 수도 있어(FE 저장 방식이 혼재) 두 경우 모두 확인한다.
                      (EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(sess.data_json->'power_list', '[]'::jsonb)) pid
                        JOIN page_data leaf ON leaf.id = pid::bigint AND leaf.data_slug IN ('product-data', 'category-data')
                        WHERE leaf.data_json->'product'->>'order_method' = '02'
                      ) OR EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(sess.data_json->'power_list', '[]'::jsonb)) pid
                        JOIN page_data child ON child.data_slug = 'category-data'
                          AND child.data_json->'product'->>'parentId' = pid
                        WHERE child.data_json->'product'->>'order_method' = '02'
                      )) AS has_power_device,
                      (EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(sess.data_json->'power_list', '[]'::jsonb)) pid
                        JOIN page_data leaf ON leaf.id = pid::bigint AND leaf.data_slug IN ('product-data', 'category-data')
                        WHERE leaf.data_json->'product'->>'order_method' = '01'
                      ) OR EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(sess.data_json->'power_list', '[]'::jsonb)) pid
                        JOIN page_data child ON child.data_slug = 'category-data'
                          AND child.data_json->'product'->>'parentId' = pid
                        WHERE child.data_json->'product'->>'order_method' = '01'
                      )) AS has_power_system
                    FROM page_data sess
                    LEFT JOIN page_data curr ON curr.id = :curriculumId AND curr.data_slug = 'currMgmt-data'
                    WHERE sess.id = :sessionId AND sess.data_slug = 'currDtlMgmt-data'
                    """);
            query.setParameter("curriculumId", curriculumId);
            query.setParameter("sessionId", sessionId);
            query.setParameter("vfdCodes", VFD_PRODUCT_CODES);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            if (rows.isEmpty()) {
                return SessionEmailContext.empty();
            }
            Object[] row = rows.get(0);
            return new SessionEmailContext(
                    (String) row[0],
                    (String) row[1],
                    Boolean.TRUE.equals(row[2]),
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    (String) row[6],
                    Boolean.TRUE.equals(row[7]),
                    Boolean.TRUE.equals(row[8]),
                    Boolean.TRUE.equals(row[9]),
                    Boolean.TRUE.equals(row[10]));
        } catch (Exception e) {
            log.warn("세션 이메일 컨텍스트 조회 실패 - curriculumId={}, sessionId={}, error={}",
                    curriculumId, sessionId, e.getMessage());
            return SessionEmailContext.empty();
        }
    }

    /** 신청자에게 보내는 신청확인 메일 본문 — Registration Fee/VFD 첨부 섹션, 오프라인 안내 문구는 조건부 노출 */
    private String buildCustomerEmail(TrainingRegistrationRequest request, SessionEmailContext ctx) {
        StringBuilder body = new StringBuilder();
        body.append(emailOpen("Training Registration Received"))
            .append(introRow("Hi <span style=\"color:#333;font-weight:600;\">" + escape(request.studentName()) + "</span>,<br /><br />"
                    + "Thanks for registering for our <span style=\"color:#333;font-weight:600;\">"
                    + escape(nz(ctx.sessionTitle())) + "</span>. "
                    + "We will follow up with you shortly regarding your training details."));

        body.append(cardTableOpen("Registered Training"))
            .append(row("Training Track", ctx.trainingCourseName()))
            .append(row("Training Type", ctx.trainingTypeLabel()))
            .append(row("Registered Training", ctx.sessionTitle()))
            .append(row("Training Location", ctx.location()))
            .append(row("Training Schedule", ctx.scheduleText()))
            .append(cardTableClose());

        if (StringUtils.isNotBlank(ctx.feeAmount())) {
            body.append(sectionHeading("Registration Fee"))
                .append(sectionParagraph(
                        "A registration fee of <span style=\"color:#333;font-weight:600;\">$" + escape(ctx.feeAmount())
                        + "</span> per person is required to attend.<br /><br />"
                        + "Accepted payment methods include:"
                        + "<ul style=\"margin:10px 0 14px;padding-left:22px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.7;color:#333333;\">"
                        + "<li>ACH / Wire transfer</li><li>Check</li><li>Credit card (4% processing fee applies)</li></ul>"
                        + "Please complete the attached payment authorization form. An invoice can be provided upon request. "
                        + "Payment must be submitted at least one week before the scheduled class date."));
            // TODO: Payment Authorization Form 실제 파일/URL 확정 필요(기획서에도 "개발 검토 필요"로 표시됨)
            body.append(linkRow("#", "Payment Authorization Form"));
        }

        List<String> additionalInfo = new ArrayList<>();
        additionalInfo.add("A reminder email will be sent one week prior to the training.");
        additionalInfo.add("Please take your seats before the training begins.");
        if (ctx.inPerson()) {
            additionalInfo.add("Please note that in-person seating is limited. In the event that maximum capacity is reached, "
                    + "additional registrants may be transitioned to virtual attendance via Microsoft Teams.");
        }
        additionalInfo.add("Class is subject to cancellation if minimum enrollment is not met. In the event of cancellation, "
                + "all registered participants will be notified at least one week in advance.");
        body.append(sectionHeading("Additional Information"))
            .append(bulletListRow(additionalInfo, ctx.vfd()));

        if (ctx.vfd()) {
            // TODO: VFD 전용 첨부파일 실제 파일/URL 확정 필요(기획서에도 "개발 검토 필요"로 표시됨)
            body.append(attachmentLinkRow("#"));
        }

        body.append(closingRow("We look forward to your participation.<br /><br />Sincerely,<br />LS ELECTRIC America"))
            .append(emailClose());
        return body.toString();
    }

    /** 담당자에게 보내는 신청접수 알림 메일 본문 — 신청 정보 전체 나열. recipientCode 에 따라 인삿말이 바뀐다 */
    private String buildAdminEmail(TrainingRegistrationRequest request, SessionEmailContext ctx, LocalDate eventDate, String recipientCode) {
        StringBuilder body = new StringBuilder();
        body.append(emailOpen("New Training Registration Received"))
            .append(debugRecipientBanner(recipientCode))
            .append(introRow("Hi " + escape(teamLabel(recipientCode)) + " Team,<br /><br />"
                    + "A new training registration has been submitted. Please review the details below."));

        body.append(cardTableOpen(null))
            .append(row("Training Track", ctx.trainingCourseName()))
            .append(row("Training Type", ctx.trainingTypeLabel()))
            .append(row("Registered Training", ctx.sessionTitle()))
            .append(row("Event Date to Attend", eventDate == null ? null : eventDate.format(EVENT_DATE_DISPLAY_FORMAT)))
            .append(row("Student Name", request.studentName()))
            .append(row("Email Address", request.email()))
            .append(row("Job Title", request.jobTitle()))
            .append(row("Phone", request.phone()))
            .append(row("Company", request.companyName()))
            .append(row("Street Address", request.streetAddress()))
            .append(row("Apartment, suite, etc", request.apartment()))
            .append(row("City", request.city()))
            .append(row("State/Province", request.stateProvince()))
            .append(row("ZIP / Postal Code", request.zipCode()))
            .append(row("Position", resolvePositionLabel(request.typeOfBusiness())))
            .append(cardTableClose());

        body.append(sectionHeading("Action Required"))
            .append(bulletListRow(List.of(
                    "Confirm seat reservation",
                    "Send a reminder email one week prior to the class"), true))
            .append(closingRow("LS ELECTRIC America"))
            .append(emailClose());
        return body.toString();
    }

    /** BUSINESSTYPE 코드 → 표시명(Position). 값이 없거나 코드 매칭 실패 시 원본 코드/null 그대로 반환 */
    private String resolvePositionLabel(String typeOfBusiness) {
        if (StringUtils.isBlank(typeOfBusiness)) {
            return null;
        }
        return codeDetailRepository.findByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_BUSINESS_TYPE, typeOfBusiness)
                .map(CodeDetail::getName)
                .orElse(typeOfBusiness);
    }

    /** 메일 바깥 테이블 시작 + 로고 + 제목 행까지 — 본문/카드 테이블은 이어서 append */
    private String emailOpen(String title) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
             + "style=\"width:100%;background:#ffffff;margin:0;padding:0;border-collapse:collapse;font-family:Arial, Helvetica, sans-serif;color:#222;-webkit-text-size-adjust:100%;\">"
             + "<tr><td align=\"center\" style=\"padding:24px 16px 40px\">"
             + "<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
             + "style=\"width:100%;max-width:640px;background:#ffffff;border:0;border-collapse:collapse;font-family:Arial, Helvetica, sans-serif;color:#222;\">"
             + "<tr><td style=\"padding:32px 32px 12px\">"
             + "<img src=\"" + logoUrl + "\" alt=\"LS ELECTRIC\" width=\"160\" "
             + "style=\"display:block;border:0;width:160px;height:auto;outline:none;text-decoration:none;\" /></td></tr>"
             + "<tr><td align=\"center\" style=\"padding:24px 32px 18px;font-family:Arial, Helvetica, sans-serif;font-size:24px;"
             + "font-weight:700;line-height:1.3;letter-spacing:-0.01em;color:#0f1f45;\">" + escape(title) + "</td></tr>";
    }

    /** 인삿말/소개 문단 행 — innerHtml 은 이미 조합된 HTML(치환값 escape 는 호출부 책임) */
    private String introRow(String innerHtml) {
        return "<tr><td style=\"padding:0 32px 28px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.7;color:#333333;\">"
             + innerHtml + "</td></tr>";
    }

    /** 자동발신 안내 + 카피라이트 푸터, 바깥 테이블/셀 닫는 태그까지 */
    private String emailClose() {
        return "<tr><td style=\"padding:32px 32px 36px;font-family:Arial, Helvetica, sans-serif;font-size:12px;line-height:1.7;"
             + "color:#888888;text-align:center;border-top:1px solid #e8e8e8;\">"
             + "This is an automated message. Please do not reply to this email.<br />"
             + "Copyright© 2026 LSELECTRIC CO.,LTD. All rights reserved.</td></tr>"
             + "</table></td></tr></table>";
    }

    /** label/value 카드 테이블 시작 — headerTitle 이 있으면 colspan 헤더 행 포함(신청 내역 카드), null 이면 헤더 없이 바로 행 시작(담당자 상세 카드) */
    private String cardTableOpen(String headerTitle) {
        StringBuilder sb = new StringBuilder("<tr><td style=\"padding:8px 32px 12px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"width:100%;background:#ffffff;border:0;border-collapse:collapse;border-spacing:0;table-layout:fixed;"
                + "font-family:Arial, Helvetica, sans-serif;color:#222222;border-top:2px solid #0f1f45;\">");
        if (headerTitle != null) {
            sb.append("<tr><td colspan=\"2\" style=\"padding:14px 16px;font-family:Arial, Helvetica, sans-serif;font-size:15px;"
                    + "font-weight:700;line-height:1.4;letter-spacing:0.01em;color:#0f1f45;text-align:left;vertical-align:middle;"
                    + "background:#ffffff;border-bottom:1px solid #e8e8e8;\">" + escape(headerTitle) + "</td></tr>");
        }
        return sb.toString();
    }

    private String cardTableClose() {
        return "</table></td></tr>";
    }

    /**
     * 카드 테이블 한 줄(label/value) — 값이 없으면 빈 칸으로 표시(행 자체는 유지).
     * 값에 개행이 있으면(예: Training Schedule 다건) escape 후 &lt;br /&gt;로 바꿔 줄바꿈해서 표시한다
     * (escape 가 먼저 실행돼야 원본 개행은 그대로 두고 실제 HTML 특수문자만 이스케이프된다).
     */
    private String row(String label, String value) {
        String escapedValue = escape(nz(value)).replace("\n", "<br />");
        return "<tr>"
             + "<td width=\"38%\" valign=\"top\" style=\"padding:11px 16px;font-family:Arial, Helvetica, sans-serif;font-size:13px;"
             + "font-weight:600;line-height:1.5;color:#555555;text-align:left;vertical-align:top;background:#f7f8fa;"
             + "border-right:1px solid #e8e8e8;border-bottom:1px solid #e8e8e8;\">" + escape(label) + "</td>"
             + "<td valign=\"top\" style=\"padding:11px 16px;font-family:Arial, Helvetica, sans-serif;font-size:15px;font-weight:400;"
             + "line-height:1.55;color:#222222;text-align:left;vertical-align:top;background:#ffffff;border-bottom:1px solid #e8e8e8;\">"
             + "<span style=\"color:#333;font-weight:600;\">" + escapedValue + "</span></td>"
             + "</tr>";
    }

    /** 카드 테이블 밖 섹션 제목(Registration Fee / Additional Information / Action Required 등) */
    private String sectionHeading(String text) {
        return "<tr><td style=\"padding:28px 32px 10px;font-family:Arial, Helvetica, sans-serif;font-size:15px;font-weight:700;"
             + "line-height:1.4;letter-spacing:0.01em;color:#0f1f45;\">" + escape(text) + "</td></tr>";
    }

    /** 섹션 제목 아래 설명 문단 — innerHtml 은 이미 조합된 HTML(치환값 escape 는 호출부 책임) */
    private String sectionParagraph(String innerHtml) {
        return "<tr><td style=\"padding:0 32px 12px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.7;color:#333333;\">"
             + innerHtml + "</td></tr>";
    }

    /** Payment Authorization Form 링크 한 줄 */
    private String linkRow(String url, String label) {
        return "<tr><td style=\"padding:0 32px 8px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.7;\">"
             + "<a href=\"" + escape(url) + "\" style=\"color:#333;font-weight:600;text-decoration:underline;"
             + "font-family:Arial, Helvetica, sans-serif;\">[" + escape(label) + "]</a></td></tr>";
    }

    /** Attachment 링크 한 줄 — Payment Authorization Form 과 달리 위쪽 여백이 4px 있다(원본 디자인 기준) */
    private String attachmentLinkRow(String url) {
        return "<tr><td style=\"padding:4px 32px 8px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.7;\">"
             + "<a href=\"" + escape(url) + "\" style=\"color:#333;font-weight:600;text-decoration:underline;"
             + "font-family:Arial, Helvetica, sans-serif;\">[Attachment]</a></td></tr>";
    }

    /**
     * 섹션 제목 아래 불릿 목록 한 줄(항목들은 고정 문구라 escape 하지 않는다).
     * tight=true — 뒤에 다른 행(Attachment 링크, Action Required 뒤 Closing 등)이 바로 이어질 때(원본 디자인 기준 padding 0 32px 8px)
     * tight=false — 카드 안에서 마지막 콘텐츠일 때(padding 8px 32px 28px)
     */
    private String bulletListRow(List<String> items, boolean tight) {
        StringBuilder ul = new StringBuilder("<ul style=\"margin:0;padding-left:22px;font-family:Arial, Helvetica, sans-serif;"
                + "font-size:15px;line-height:1.7;color:#333333;\">");
        for (String item : items) {
            ul.append("<li>").append(item).append("</li>");
        }
        ul.append("</ul>");
        String padding = tight ? "0 32px 8px" : "8px 32px 28px";
        String lineHeight = tight ? "1.7" : "1.75";
        return "<tr><td style=\"padding:" + padding + ";font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:" + lineHeight + ";"
             + "color:#333333;\">" + ul + "</td></tr>";
    }

    /** 마무리 인사말/안내 문구 행 — innerHtml 은 이미 조합된 HTML(고정 문구라 escape 하지 않는다) */
    private String closingRow(String innerHtml) {
        return "<tr><td style=\"padding:8px 32px 28px;font-family:Arial, Helvetica, sans-serif;font-size:15px;line-height:1.75;"
             + "color:#333333;\">" + innerHtml + "</td></tr>";
    }

    /** HTML 이스케이프(XSS 방지) — 사용자 입력이 그대로 메일 본문에 들어가므로 필수 */
    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** null → 빈 문자열 */
    private String nz(String value) {
        return value == null ? "" : value;
    }

    /** "yyyy-MM-dd" 파싱. 형식 불량 시 400. */
    private LocalDate parseEventDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("참석 일자 형식이 올바르지 않습니다.");
        }
    }

    /** 선택 문자열 필드 — 공백/빈 문자열은 NULL 로 저장 */
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}

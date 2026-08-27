package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.mail.MailSendEvent;
import com.ge.bo.dto.TrainingRequestSubmitRequest;
import com.ge.bo.dto.TrainingRequestSubmitRequest.SelectedProduct;
import com.ge.bo.dto.TrainingRequestSubmitResponse;
import com.ge.bo.entity.CodeDetail;
import com.ge.bo.entity.TrainingRequest;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.TrainingRequestRepository;
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
import java.util.stream.Collectors;

/**
 * FO Training Request(비정기 교육 신청, Step1~4) 접수 서비스
 * - 처리 순서: 자체 캡차 검증 → 날짜 파싱 → selectedProducts JSON 직렬화 → insert
 * - TrainingRegistrationService 의 레이어 구조를 그대로 본떠 작성(캡차 공용 서비스 재사용).
 * - 신청자가 입력한 값을 그대로 보존하는 이력성 저장이라 코드값 변환/공통코드 검증은 하지 않는다.
 * - 메일 발송은 신청자에게 신청확인 메일, 담당자(EMAIL_RECIPIENT 공통코드)에게 신청접수 알림 메일 — 각자에게 개별 발송한다.
 *   담당자 메일은 trainingFormat(In-Person/Virtual)에 따라 현장 위치 필드 포함 여부가 갈리고, 담당자는 Sales/Service 는
 *   단일 코드, Engineering 은 선택 제품(Automation/Power 양산·수주)에 따라 최대 3곳까지 동시 발송될 수 있다
 *   (resolveRecipientCodes, TrainingRegistrationService 와 동일 로직).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRequestService {

    /** 공통코드 EMAILSENDTYPE — 비정기 Training(Training Request) */
    private static final String EMAIL_SEND_TYPE_IRREGULAR_TRAINING = "03";

    /** 담당자 이메일 공통코드 그룹 (extra1 필드에 실제 수신 이메일 저장, 콤마로 복수 가능 — name 은 표시용) */
    private static final String GROUP_EMAIL_RECIPIENT = "EMAIL_RECIPIENT";

    /** 공통코드 그룹 코드 — 교육 트랙(Training Track 표시명 조회용, TrainingRegistrationService 와 동일 그룹) */
    private static final String GROUP_TRAINING_COURSE = "TRAININGCOURSE";

    /** TRAININGCOURSE 코드 */
    private static final String TRAINING_COURSE_ENGINEERING = "01";
    private static final String TRAINING_COURSE_SERVICE = "02";
    private static final String TRAINING_COURSE_SALES = "03";

    /** EMAIL_RECIPIENT 담당자 코드 — Sales/Engineering 은 선택 제품(Power, Automation)에 따라 여러 곳에 동시 발송될 수 있다(TrainingRegistrationService 와 동일 코드 재사용) */
    private static final String RECIPIENT_CODE_SALES_POWER = "TRAINING_SALES_POWER";
    private static final String RECIPIENT_CODE_SALES_AUTO = "TRAINING_SALES_AUTO";
    private static final String RECIPIENT_CODE_SERVICE = "TRAINING_SERVICE";
    private static final String RECIPIENT_CODE_ENGINEERING_AUTO = "TRAINING_ENGINNERING_AUTO";
    private static final String RECIPIENT_CODE_ENGINEERING_POWER_DEVICE = "TRAINING_ENGINNERING_POWER_D";
    private static final String RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM = "TRAINING_ENGINNERING_POWER_S";

    /** selectedProducts 의 Power/Automation 구분 값(SelectedProduct.type) */
    private static final String PRODUCT_TYPE_POWER = "P";
    private static final String PRODUCT_TYPE_AUTOMATION = "A";

    /** trainingFormat 값 — 현장 필드 노출 여부 판단용 */
    private static final String TRAINING_FORMAT_IN_PERSON = "In-Person";

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TrainingRequestRepository trainingRequestRepository;
    private final CaptchaService captchaService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CodeDetailRepository codeDetailRepository;
    private final EntityManager entityManager;

    /** 메일 헤더 로고 이미지 — FO 사이트에 호스팅된 절대 URL(메일 클라이언트는 상대경로 이미지를 로드할 수 없다) */
    @Value("${app.mail.logo-url}")
    private String logoUrl;

    /**
     * 교육 신청 접수 처리 — 자체 캡차 검증 → 저장 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 getRemoteAddr() 기준 추출)
     * @return 저장된 신청 id
     */
    @Transactional
    public TrainingRequestSubmitResponse submit(TrainingRequestSubmitRequest request, String clientIp) {

        // 1) 자체 캡차 검증 (실패 시 BusinessException 발생 → 400)
        captchaService.verify(request.captchaToken(), request.captchaCode());

        // 2) 희망 교육 일자 파싱("yyyy-MM-dd")
        LocalDate scheduleStart = parseDate(request.scheduleStart(), "교육 시작일");
        LocalDate scheduleEnd = parseDate(request.scheduleEnd(), "교육 종료일");

        // 3) 저장 (append-only)
        TrainingRequest entity = TrainingRequest.builder()
                // Step1
                .trainingTrack(blankToNull(request.trainingTrack()))
                .firstName(request.firstName())
                .lastName(blankToNull(request.lastName()))
                .company(request.company())
                .streetAddress(request.streetAddress())
                .address2(blankToNull(request.address2()))
                .city(request.city())
                .state(request.state())
                .zip(request.zip())
                .phone(request.phone())
                .email(request.email())
                .title(blankToNull(request.title()))
                .cellPhone(blankToNull(request.cellPhone()))
                .salesContact(blankToNull(request.salesContact()))
                // Step2
                .sessionCount(request.sessionCount())
                .sessionDays(request.sessionDays())
                .scheduleStart(scheduleStart)
                .scheduleEnd(scheduleEnd)
                .studentCount(request.studentCount())
                // Step3 — Virtual 이면 장소/담당자 항목은 전부 비어 있어 NULL 로 저장된다
                .trainingFormat(request.trainingFormat())
                .locationName(blankToNull(request.locationName()))
                .locationStreetAddress(blankToNull(request.locationStreetAddress()))
                .locationAddress2(blankToNull(request.locationAddress2()))
                .locationCity(blankToNull(request.locationCity()))
                .locationState(blankToNull(request.locationState()))
                .locationZip(blankToNull(request.locationZip()))
                .contactPerson(blankToNull(request.contactPerson()))
                .contactDetails(blankToNull(request.contactDetails()))
                // Step4 — selectedProducts만 구조화된 객체 배열이라 JSONB(JSON 문자열 직렬화) 저장,
                // 나머지 3개는 단순 문자열 배열이라 text[]로 그대로 저장
                .selectedProducts(toJson(request.selectedProducts()))
                .jobTitles(request.jobTitles())
                .studentInvolvement(request.studentInvolvement())
                .vfdUnderstanding(blankToNull(request.vfdUnderstanding()))
                .vfdUnderstandingTopics(request.vfdUnderstandingTopics())
                .comments(blankToNull(request.comments()))
                .consentChecked(request.consentChecked())
                .createdIp(clientIp)
                .build();

        TrainingRequest saved = trainingRequestRepository.save(entity);

        // 개인정보(이름/이메일 등)는 로그에 남기지 않고 추적용 식별값만 기록
        log.info("Training Request 접수 저장 완료 - id={}, trainingFormat={}, productCount={}",
                saved.getId(), saved.getTrainingFormat(), request.selectedProducts().size());

        // 신청자에게는 신청확인 메일, 담당자에게는 신청접수 알림 메일 — 각자에게 개별 발송(한 메일에 여러 수신자를 넣지 않는다)
        // 발송 이력 저장(성공/실패 모두)은 MailService 가 각 발송 건마다 공통으로 처리
        // 실제 발송은 트랜잭션 커밋 후 MailSendEventListener 가 비동기로 수행(요청 스레드/DB 커넥션 점유 방지)
        String trainingCourseCode = toTrainingCourseCode(request.trainingTrack());
        String trainingCourseName = resolveTrainingCourseName(trainingCourseCode);

        String customerSubject = "[LS ELECTRIC] Training Request Received";
        eventPublisher.publishEvent(new MailSendEvent(request.email(), customerSubject,
                buildCustomerEmail(request, trainingCourseName, scheduleStart, scheduleEnd),
                EMAIL_SEND_TYPE_IRREGULAR_TRAINING, trainingCourseCode, null));

        List<String> recipientCodes = resolveRecipientCodes(trainingCourseCode, request.selectedProducts());
        if (!recipientCodes.isEmpty()) {
            boolean inPerson = TRAINING_FORMAT_IN_PERSON.equalsIgnoreCase(request.trainingFormat());
            String adminSubject = "[New Request] Training Registration Confirmation — Action Required";
            for (String recipientCode : recipientCodes) {
                List<String> managerEmails = resolveManagerEmails(List.of(recipientCode));
                // 담당자 이메일이 미설정이어도 발송 시도 자체는 이력에 남긴다(recipient 자리에 코드명을 넣어 실패건으로 기록 —
                // 조용히 건너뛰면 "왜 담당자 메일이 안 왔는지"를 이력만 보고 추적할 수 없다).
                List<String> recipients = managerEmails.isEmpty() ? List.of(recipientCode) : managerEmails;
                String adminContent = buildAdminEmail(request, trainingCourseName, inPerson, scheduleStart, scheduleEnd, recipientCode);
                for (String recipient : recipients) {
                    eventPublisher.publishEvent(new MailSendEvent(recipient, adminSubject, adminContent,
                            EMAIL_SEND_TYPE_IRREGULAR_TRAINING, trainingCourseCode, null));
                }
            }
        }

        return new TrainingRequestSubmitResponse(saved.getId());
    }

    /**
     * TRAININGCOURSE 코드 + 선택 제품 구성(Engineering 만 해당)에 따라 알림 보낼 EMAIL_RECIPIENT 코드 목록을 결정.
     * Engineering 은 Automation 제품과 Power(양산/수주) 제품이 함께 선택되면 해당되는 모든 담당자에게 각각 발송한다.
     */
    private List<String> resolveRecipientCodes(String trainingCourseCode, List<SelectedProduct> selectedProducts) {
        if (TRAINING_COURSE_SERVICE.equals(trainingCourseCode)) {
            return List.of(RECIPIENT_CODE_SERVICE);
        }
        if (TRAINING_COURSE_SALES.equals(trainingCourseCode)) {
            List<String> codes = new ArrayList<>();
            boolean hasAutomation = selectedProducts.stream().anyMatch(p -> PRODUCT_TYPE_AUTOMATION.equals(p.type()));
            if (hasAutomation) {
                codes.add(RECIPIENT_CODE_SALES_AUTO);
            }
            boolean hasPower = selectedProducts.stream().anyMatch(p -> PRODUCT_TYPE_POWER.equals(p.type()));
            if (hasPower) {
                codes.add(RECIPIENT_CODE_SALES_POWER);
            }
            return codes;
        }
        if (TRAINING_COURSE_ENGINEERING.equals(trainingCourseCode)) {
            List<String> codes = new ArrayList<>();
            boolean hasAutomation = selectedProducts.stream().anyMatch(p -> PRODUCT_TYPE_AUTOMATION.equals(p.type()));
            if (hasAutomation) {
                codes.add(RECIPIENT_CODE_ENGINEERING_AUTO);
            }

            List<Long> powerCategoryIds = selectedProducts.stream()
                    .filter(p -> PRODUCT_TYPE_POWER.equals(p.type()))
                    .map(SelectedProduct::id)
                    .distinct()
                    .toList();
            if (!powerCategoryIds.isEmpty()) {
                boolean[] powerOrderMethods = resolvePowerOrderMethods(powerCategoryIds);
                if (powerOrderMethods[0]) {
                    codes.add(RECIPIENT_CODE_ENGINEERING_POWER_DEVICE);
                }
                if (powerOrderMethods[1]) {
                    codes.add(RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM);
                }
            }
            return codes;
        }
        return List.of();
    }

    /**
     * Power 선택 항목(selectedProducts 의 id) 하위/자체 order_method 존재 여부 조회.
     * id 는 보통 depth=2 카테고리(자식 리프의 order_method 를 봐야 함)지만, product-data/category-data 리프 id 가
     * 직접 들어오는 경우도 있어(FE 저장 방식이 혼재) 두 경우 모두 확인한다.
     * 반환값 [0]=양산(02) 포함 여부, [1]=수주(01) 포함 여부 — 두 값이 각각 독립적으로 계산되므로 같은 카테고리 안에
     * 양산/수주 제품이 섞여 있어도(선택된 카테고리 하위에 둘 다 존재해도) 두 담당자 모두에게 정상적으로 발송된다.
     */
    private boolean[] resolvePowerOrderMethods(List<Long> powerCategoryIds) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                  bool_or(order_method = '02') AS has_device,
                  bool_or(order_method = '01') AS has_system
                FROM (
                  SELECT data_json->'product'->>'order_method' AS order_method
                  FROM page_data
                  WHERE id::text IN (:ids) AND data_slug IN ('product-data', 'category-data')
                  UNION ALL
                  SELECT child.data_json->'product'->>'order_method' AS order_method
                  FROM page_data child
                  WHERE child.data_slug = 'category-data'
                    AND child.data_json->'product'->>'parentId' IN (:ids)
                ) leaves
                """);
        query.setParameter("ids", powerCategoryIds.stream().map(String::valueOf).toList());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return new boolean[] {false, false};
        }
        Object[] row = rows.get(0);
        return new boolean[] {Boolean.TRUE.equals(row[0]), Boolean.TRUE.equals(row[1])};
    }

    // TODO: 테스트용 — 실제 외부 주소(engineering.training_device@lselectricamerica.com 등)로 검증 끝나면
    // CodeDetail::getName → CodeDetail::getExtra1 로 되돌린다(실제 수신 이메일은 extra1에 저장돼 있음).
    /**
     * EMAIL_RECIPIENT 코드 목록 → 수신 이메일(name, 테스트용 단일 주소) 조회 — 코드별 콤마 다중 저장을 개별 주소로 풀고, 코드 여러 개에 걸쳐 중복되면 한 번만 발송.
     * 코드가 없거나 담당자 코드가 미설정이면 빈 목록(신청자에게만 발송).
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

    /** EMAIL_RECIPIENT 코드 → 관리자 메일 인삿말 표시용 라벨("Hi {label} Team," 형태로 사용) */
    private String teamLabel(String recipientCode) {
        return switch (recipientCode) {
            case RECIPIENT_CODE_SALES_POWER -> "Sales Training (Power)";
            case RECIPIENT_CODE_SALES_AUTO -> "Sales Training (Automation)";
            case RECIPIENT_CODE_SERVICE -> "Service Training";
            case RECIPIENT_CODE_ENGINEERING_AUTO -> "Engineering Training (Automation)";
            case RECIPIENT_CODE_ENGINEERING_POWER_DEVICE -> "Engineering Training (Power - Device)";
            case RECIPIENT_CODE_ENGINEERING_POWER_SYSTEM -> "Engineering Training (Power - System)";
            default -> "Training";
        };
    }


    /** 콤마로 여러 명 저장된 이메일 문자열 → 개별 주소 리스트(공백 제거, 빈 값 제외) */
    private List<String> splitEmails(String emails) {
        return Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /** TRAININGCOURSE 코드(01/02/03) → 표시명("Engineering Training" 등) 조회. 코드 없거나 매칭 실패 시 null. */
    private String resolveTrainingCourseName(String trainingCourseCode) {
        if (trainingCourseCode == null) {
            return null;
        }
        return codeDetailRepository.findByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_TRAINING_COURSE, trainingCourseCode)
                .map(CodeDetail::getName)
                .orElse(null);
    }

    /** 신청자에게 보내는 신청확인 메일 본문 — 신청 내역만 간략히 표시(분기 없음) */
    private String buildCustomerEmail(TrainingRequestSubmitRequest request, String trainingCourseName,
                                       LocalDate scheduleStart, LocalDate scheduleEnd) {
        StringBuilder body = new StringBuilder();
        body.append(emailOpen("Training Request Received"))
            .append(introRow("Hi <span style=\"color:#333;font-weight:600;\">" + escape(request.firstName()) + "</span>,<br /><br />"
                    + "Your training request has been successfully submitted to LS ELECTRIC. "
                    + "We will follow up with you shortly regarding your training details."));

        body.append(cardTableOpen("Requested Training Details"))
            .append(row("Training Track", trainingCourseName))
            .append(row("Training Type", request.trainingFormat()))
            .append(row("Training Products", buildProductPath(trainingCourseName, request.selectedProducts(), false)))
            .append(row("Company", request.company()))
            .append(row("Preferred Date(s)", formatDateRange(scheduleStart, scheduleEnd)))
            .append(row("Attendees per Session", request.studentCount()))
            .append(row("Training Location", request.locationName()))
            .append(cardTableClose());

        body.append(closingRow("Sincerely,<br />LS ELECTRIC America"))
            .append(emailClose());
        return body.toString();
    }

    /**
     * 담당자에게 보내는 신청접수 알림 메일 본문 — In-Person 이면 현장 위치 필드, VFD 조건부 질문 응답이 있으면 관련 필드를 추가로 표시.
     * Training Track 행은 오프라인(In-Person) 신청일 때만 노출한다(기획서 기준, 온라인은 First Name부터 시작).
     * recipientCode 에 따라 인삿말이 바뀐다.
     */
    private String buildAdminEmail(TrainingRequestSubmitRequest request, String trainingCourseName, boolean inPerson,
                                    LocalDate scheduleStart, LocalDate scheduleEnd, String recipientCode) {
        StringBuilder body = new StringBuilder();
        body.append(emailOpen("New Training Request Received"))
            .append(introRow("Hi " + escape(teamLabel(recipientCode))
                    + " Team,<br /><br />A new training request has been submitted. Please review the details below."));

        body.append(cardTableOpen(null));
        if (inPerson) {
            body.append(row("Training Track", trainingCourseName));
        }
        body.append(row("First Name", request.firstName()))
            .append(row("Last Name", request.lastName()))
            .append(row("Company", request.company()))
            .append(row("Street Address", request.streetAddress()))
            .append(row("City", request.city()))
            .append(row("State/Province", request.state()))
            .append(row("ZIP / Postal Code", request.zip()))
            .append(row("Phone", request.phone()))
            .append(row("Email Address", request.email()))
            .append(row("Title", request.title()))
            .append(row("Cell Phone", request.cellPhone()))
            .append(row("Sales Contact", request.salesContact()))
            .append(row("Number of Training Sessions", request.sessionCount()))
            .append(row("Session Duration (Days)", request.sessionDays()))
            .append(row("Training Session Date", formatDateRange(scheduleStart, scheduleEnd)))
            .append(row("Attendees per Session", request.studentCount()))
            .append(row("Training Type", request.trainingFormat()));

        if (inPerson) {
            body.append(row("Training Location", request.locationName()))
                .append(row("Street Address", request.locationStreetAddress()))
                .append(row("City", request.locationCity()))
                .append(row("State/Province", request.locationState()))
                .append(row("ZIP / Postal Code", request.locationZip()))
                .append(row("On-Site Contact", request.contactPerson()))
                .append(row("Contact Details", request.contactDetails()));
        }

        body.append(row("Training Products", buildProductPath(trainingCourseName, request.selectedProducts(), true)));

        boolean hasVfdAnswers = !isEmpty(request.jobTitles()) || !isEmpty(request.studentInvolvement())
                || StringUtils.isNotBlank(request.vfdUnderstanding());
        if (hasVfdAnswers) {
            body.append(row("Student Job Titles", joinOrNull(request.jobTitles())))
                .append(row("Area of Involvement", joinOrNull(request.studentInvolvement())))
                .append(row("Student Product Knowledge Level", formatVfdUnderstanding(request)));
        }

        body.append(row("Certification Notes", request.comments()))
            .append(cardTableClose());

        body.append(sectionHeading("Action Required"))
            .append(bulletListRow(List.of(
                    "Please review the submitted training request details and contact the customer to coordinate the schedule."), true))
            .append(closingRow("LS ELECTRIC America"))
            .append(emailClose());
        return body.toString();
    }

    /** "{Track} > {Power/Automation 목록} > [{제품그룹 목록} >] {제품명 목록}" — includeGroupTitle=false 면 그룹 단계 생략(고객 메일용) */
    private String buildProductPath(String trainingCourseName, List<SelectedProduct> products, boolean includeGroupTitle) {
        String track = StringUtils.isNotBlank(trainingCourseName)
                ? trainingCourseName.replace(" Training", "") : "";
        String types = products.stream()
                .map(p -> PRODUCT_TYPE_POWER.equals(p.type()) ? "Power" : "Automation")
                .distinct().collect(Collectors.joining(", "));
        String names = products.stream().map(SelectedProduct::name).distinct().collect(Collectors.joining(", "));

        StringBuilder path = new StringBuilder(track).append(" > ").append(types);
        if (includeGroupTitle) {
            String groups = products.stream().map(SelectedProduct::groupTitle)
                    .filter(StringUtils::isNotBlank).distinct().collect(Collectors.joining(", "));
            path.append(" > ").append(groups);
        }
        return path.append(" > ").append(names).toString();
    }

    /** VFD 이해도 응답(Yes/No) + 희망 주제 목록을 한 줄로 결합 */
    private String formatVfdUnderstanding(TrainingRequestSubmitRequest request) {
        if (StringUtils.isBlank(request.vfdUnderstanding())) {
            return null;
        }
        String topics = joinOrNull(request.vfdUnderstandingTopics());
        return topics == null ? request.vfdUnderstanding() : request.vfdUnderstanding() + " (" + topics + ")";
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private String joinOrNull(List<String> values) {
        return isEmpty(values) ? null : String.join(", ", values);
    }

    /** 시작일=종료일이면 한 줄, 다르면 "start - end" 범위로 표시. 파싱 실패 시(호출 전 이미 검증됨) null 없음 */
    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start == null) {
            return null;
        }
        if (end == null || start.equals(end)) {
            return start.format(DATE_DISPLAY_FORMAT);
        }
        return start.format(DATE_DISPLAY_FORMAT) + " - " + end.format(DATE_DISPLAY_FORMAT);
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

    /** 카드 테이블 한 줄(label/value) — 값이 없으면 빈 칸으로 표시(행 자체는 유지) */
    private String row(String label, String value) {
        return "<tr>"
             + "<td width=\"38%\" valign=\"top\" style=\"padding:11px 16px;font-family:Arial, Helvetica, sans-serif;font-size:13px;"
             + "font-weight:600;line-height:1.5;color:#555555;text-align:left;vertical-align:top;background:#f7f8fa;"
             + "border-right:1px solid #e8e8e8;border-bottom:1px solid #e8e8e8;\">" + escape(label) + "</td>"
             + "<td valign=\"top\" style=\"padding:11px 16px;font-family:Arial, Helvetica, sans-serif;font-size:15px;font-weight:400;"
             + "line-height:1.55;color:#222222;text-align:left;vertical-align:top;background:#ffffff;border-bottom:1px solid #e8e8e8;\">"
             + "<span style=\"color:#333;font-weight:600;\">" + escape(nz(value)) + "</span></td>"
             + "</tr>";
    }

    /** 카드 테이블 밖 섹션 제목(Action Required 등) */
    private String sectionHeading(String text) {
        return "<tr><td style=\"padding:28px 32px 10px;font-family:Arial, Helvetica, sans-serif;font-size:15px;font-weight:700;"
             + "line-height:1.4;letter-spacing:0.01em;color:#0f1f45;\">" + escape(text) + "</td></tr>";
    }

    /**
     * 섹션 제목 아래 불릿 목록 한 줄(항목들은 고정 문구라 escape 하지 않는다).
     * tight=true — 뒤에 다른 행(Closing 등)이 바로 이어질 때(원본 디자인 기준 padding 0 32px 8px)
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

    /** trainingTrack(engineering/service/sales) → 공통코드 TRAININGCOURSE(01/02/03) 매핑 — 이메일 이력 상세분류용 */
    private String toTrainingCourseCode(String trainingTrack) {
        if (trainingTrack == null) {
            return null;
        }
        return switch (trainingTrack) {
            case "engineering" -> "01";
            case "service" -> "02";
            case "sales" -> "03";
            default -> null;
        };
    }

    /** "yyyy-MM-dd" 파싱. 형식 불량 시 400. */
    private LocalDate parseDate(String value, String fieldLabel) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest(fieldLabel + " 형식이 올바르지 않습니다.");
        }
    }

    /** 필수 JSONB 컬럼용 — 객체를 JSON 문자열로 직렬화 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Training Request JSON 직렬화 실패: {}", e.getMessage());
            throw BusinessException.badRequest("신청 정보 형식이 올바르지 않습니다.");
        }
    }

    /** 선택 문자열 필드 — 공백/빈 문자열은 NULL 로 저장 */
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}

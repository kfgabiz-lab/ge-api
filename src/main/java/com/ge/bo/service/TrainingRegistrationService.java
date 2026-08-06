package com.ge.bo.service;

import com.ge.bo.common.mail.MailService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * FO 트레이닝 세션 등록(리드 캡처) 접수 서비스
 * - 처리 순서: reCAPTCHA 검증 → typeOfBusiness 공통코드(BUSINESSTYPE) 검증(값 있을 때만) → insert
 * - ContactUsInquiryService 의 레이어 구조를 그대로 본떠 작성(공통코드 검증도 동일 메서드 재사용).
 * - 세션/코스 id 의 부모-자식 일치 검증은 하지 않는다(과설계 방지, 사용자 확정).
 * - 메일 발송은 신청자 + 교육과정별 담당자(EMAIL_RECIPIENT 공통코드)에게 발송하나, 내용은 curriculumId/sessionId만 담는
 *   임시 템플릿 — 템플릿 확정 시 교체 예정.
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

    /** 담당자 이메일 공통코드 그룹 (name 필드에 이메일 저장, 콤마로 복수 가능) */
    private static final String GROUP_EMAIL_RECIPIENT = "EMAIL_RECIPIENT";

    /** TRAININGCOURSE 코드(01/02/03) → EMAIL_RECIPIENT 담당자 코드 매핑 */
    private static final Map<String, String> TRAINING_COURSE_TO_RECIPIENT_CODE = Map.of(
            "01", "ENGINEERINGTRAINING",
            "02", "SERVICETRAINING",
            "03", "SALESTRAINING");

    private final TrainingRegistrationRepository trainingRegistrationRepository;
    private final CodeDetailRepository codeDetailRepository;
    private final RecaptchaService recaptchaService;
    private final PageDataService pageDataService;
    private final MailService mailService;
    private final EntityManager entityManager;

    /**
     * 등록 접수 처리 — reCAPTCHA 검증 → 코드 검증 → 저장 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 X-Forwarded-For 우선 추출)
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

        // 신청자 + 교육과정별 담당자 각자에게 개별 발송(한 메일에 여러 수신자를 넣지 않는다)
        // 내용은 curriculumId/sessionId만 담는 임시 템플릿 — 추후 교체 예정
        // 발송 이력 저장(성공/실패 모두)은 MailService 가 각 발송 건마다 공통으로 처리
        String trainingCourseCode = findTrainingCourseCode(request.curriculumId());
        String subject = "Training Registration Test";
        String content = "<p>curriculumId: %d</p><p>sessionId: %d</p>".formatted(
                request.curriculumId(), request.sessionId());
        for (String recipient : buildRecipients(request.email(), resolveManagerEmail(trainingCourseCode))) {
            mailService.sendMail(recipient, subject, content, EMAIL_SEND_TYPE_REGULAR_TRAINING, trainingCourseCode, null);
        }

        return TrainingRegistrationResponse.success(saved.getId());
    }

    /**
     * TRAININGCOURSE 코드에 해당하는 담당자 이메일(EMAIL_RECIPIENT.name) 조회 — 콤마로 여러 명 저장돼 있어도 그대로 반환.
     * 코드가 없거나(curriculum 조회 실패) 담당자 코드가 미설정이면 null(신청자에게만 발송).
     */
    private String resolveManagerEmail(String trainingCourseCode) {
        String recipientCode = TRAINING_COURSE_TO_RECIPIENT_CODE.get(trainingCourseCode);
        if (recipientCode == null) {
            return null;
        }
        return codeDetailRepository.findByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_EMAIL_RECIPIENT, recipientCode)
                .map(CodeDetail::getName)
                .orElse(null);
    }

    /**
     * 신청자 이메일 + 담당자 이메일(콤마로 여러 명 저장 가능)을 개별 수신자 목록으로 분리.
     * 한 메일에 여러 명을 담지 않고 각자에게 따로 발송하기 위함(수신자별 To 노출 방지).
     */
    private List<String> buildRecipients(String applicantEmail, String managerEmail) {
        List<String> recipients = new ArrayList<>();
        recipients.add(applicantEmail);
        if (StringUtils.isNotBlank(managerEmail)) {
            Arrays.stream(managerEmail.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(recipients::add);
        }
        return recipients;
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

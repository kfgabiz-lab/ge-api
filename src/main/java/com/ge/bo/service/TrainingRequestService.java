package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.mail.MailSendEvent;
import com.ge.bo.dto.TrainingRequestSubmitRequest;
import com.ge.bo.dto.TrainingRequestSubmitResponse;
import com.ge.bo.entity.CodeDetail;
import com.ge.bo.entity.TrainingRequest;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.TrainingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FO Training Request(비정기 교육 신청, Step1~4) 접수 서비스
 * - 처리 순서: reCAPTCHA 검증 → 날짜 파싱 → selectedProducts JSON 직렬화 → insert
 * - TrainingRegistrationService 의 레이어 구조를 그대로 본떠 작성(reCAPTCHA 공용 서비스 재사용).
 * - 신청자가 입력한 값을 그대로 보존하는 이력성 저장이라 코드값 변환/공통코드 검증은 하지 않는다.
 * - 메일 발송은 신청자 + 담당자(EMAIL_RECIPIENT.TRAININGREQUEST)에게 발송하나, 내용은 id/trainingTrack만 담는
 *   임시 템플릿 — 템플릿 확정 시 교체 예정.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRequestService {

    /** 공통코드 EMAILSENDTYPE — 비정기 Training(Training Request) */
    private static final String EMAIL_SEND_TYPE_IRREGULAR_TRAINING = "03";

    /** 담당자 이메일 공통코드 그룹 (name 필드에 이메일 저장, 콤마로 복수 가능) */
    private static final String GROUP_EMAIL_RECIPIENT = "EMAIL_RECIPIENT";

    /** EMAIL_RECIPIENT 담당자 코드 — 비정기 Training Request는 트랙 구분 없이 단일 코드 사용 */
    private static final String EMAIL_RECIPIENT_CODE_TRAINING_REQUEST = "TRAININGREQUEST";

    private final TrainingRequestRepository trainingRequestRepository;
    private final RecaptchaService recaptchaService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CodeDetailRepository codeDetailRepository;

    /**
     * 교육 신청 접수 처리 — reCAPTCHA 검증 → 저장 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 getRemoteAddr() 기준 추출)
     * @return 저장된 신청 id
     */
    @Transactional
    public TrainingRequestSubmitResponse submit(TrainingRequestSubmitRequest request, String clientIp) {

        // 1) reCAPTCHA 검증 (실패 시 BusinessException 발생 → 400)
        recaptchaService.verify(request.recaptchaToken());

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

        // 신청자 + 담당자 각자에게 개별 발송(한 메일에 여러 수신자를 넣지 않는다)
        // 내용은 id/trainingTrack만 담는 임시 템플릿 — 추후 교체 예정
        // 발송 이력 저장(성공/실패 모두)은 MailService 가 각 발송 건마다 공통으로 처리
        // 실제 발송은 트랜잭션 커밋 후 MailSendEventListener 가 비동기로 수행(요청 스레드/DB 커넥션 점유 방지)
        String trainingCourseCode = toTrainingCourseCode(request.trainingTrack());
        String subject = "Training Request Test";
        String content = "<p>id: %d</p><p>trainingTrack: %s</p>".formatted(saved.getId(), request.trainingTrack());
        for (String recipient : buildRecipients(request.email(), resolveManagerEmail())) {
            eventPublisher.publishEvent(new MailSendEvent(recipient, subject, content,
                    EMAIL_SEND_TYPE_IRREGULAR_TRAINING, trainingCourseCode, null));
        }

        return new TrainingRequestSubmitResponse(saved.getId());
    }

    /** 담당자 이메일(EMAIL_RECIPIENT.TRAININGREQUEST.name) 조회 — 콤마로 여러 명 저장돼 있어도 그대로 반환. 미설정 시 null. */
    private String resolveManagerEmail() {
        return codeDetailRepository
                .findByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_EMAIL_RECIPIENT, EMAIL_RECIPIENT_CODE_TRAINING_REQUEST)
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

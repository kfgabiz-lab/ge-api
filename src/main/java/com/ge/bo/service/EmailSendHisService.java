package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.mail.MailService;
import com.ge.bo.dto.EmailSendHisDetailResponse;
import com.ge.bo.dto.EmailSendHisResponse;
import com.ge.bo.entity.EmailSendHis;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.EmailSendHisRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 이메일 발송 이력 조회/재발송 서비스
 * - 저장은 MailService.sendMail() 이 공통으로 처리한다(email_send_his 전용 테이블)
 * - 재발송은 새 이력행을 만들지 않고 원본 행을 UPDATE한다(기획: 재발송 성공 시 원본 발송상태를 '성공'으로 갱신,
 *   최근재발송일시는 JPA Auditing의 updated_at 자동 갱신을 그대로 재사용 — created_at과 다르면 재발송된 것으로 판단).
 */
@Service
@RequiredArgsConstructor
public class EmailSendHisService {

    private final EmailSendHisRepository emailSendHisRepository;
    private final MailService mailService;
    private final AuditorAware<String> auditorAware;
    private final SiteTimeZoneResolver siteTimeZoneResolver;

    /* ══════════ 목록 조회 ══════════ */

    @Transactional(readOnly = true)
    public Page<EmailSendHisResponse> getList(String sendStatus, String emailSendType, String emailSendDetailType,
                                               String recipientEmail, OffsetDateTime startDate, OffsetDateTime endDate,
                                               Pageable pageable) {
        Specification<EmailSendHis> spec = buildSpec(sendStatus, emailSendType, emailSendDetailType,
                recipientEmail, startDate, endDate);
        return emailSendHisRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /* ══════════ 단건 조회 ══════════ */

    @Transactional(readOnly = true)
    public EmailSendHisDetailResponse getOne(Long id) {
        EmailSendHis entity = findByIdOrThrow(id);
        return toDetailResponse(entity);
    }

    /* ══════════ 재발송 ══════════ */

    /**
     * 재발송 — 원본 이력에 저장된 subject/content/recipientEmail 그대로 다시 발송한다.
     * 기획: 새 이력행을 만들지 않고 원본 행을 그대로 갱신한다 — 재발송 성공 시 발송상태를 '성공'으로 갱신,
     * updated_at을 NOW()로 찍어 "최근재발송일시"로 사용한다(재발송 실패 시에도 시도 자체는 기록되도록 항상 갱신).
     */
    @Transactional
    public String resend(Long id) {
        EmailSendHis entity = findByIdOrThrow(id);
        String sendStatus = mailService.sendOnly(entity.getRecipientEmail(), entity.getSubject(), entity.getContent());
        String updatedBy = auditorAware.getCurrentAuditor().orElse("system");
        emailSendHisRepository.updateSendStatus(id, sendStatus, updatedBy);
        return sendStatus;
    }

    /* ══════════ 공통 ══════════ */

    private EmailSendHis findByIdOrThrow(Long id) {
        return emailSendHisRepository.findById(id)
                .orElseThrow(() -> ErrorCode.EMAIL_SEND_HIS_NOT_FOUND.toException());
    }

    private Specification<EmailSendHis> buildSpec(String sendStatus, String emailSendType, String emailSendDetailType,
                                                    String recipientEmail, OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sendStatus != null && !sendStatus.isBlank()) {
                predicates.add(cb.equal(root.get("sendStatus"), sendStatus));
            }
            if (emailSendType != null && !emailSendType.isBlank()) {
                predicates.add(cb.equal(root.get("emailSendType"), emailSendType));
            }
            if (emailSendDetailType != null && !emailSendDetailType.isBlank()) {
                predicates.add(cb.equal(root.get("emailSendDetailType"), emailSendDetailType));
            }
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("recipientEmail")),
                        "%" + recipientEmail.trim().toLowerCase() + "%"));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private EmailSendHisResponse toResponse(EmailSendHis e) {
        return new EmailSendHisResponse(
                e.getId(),
                e.getEmailSendType(),
                e.getEmailSendDetailType(),
                e.getRecipientEmail(),
                e.getSendStatus(),
                toDisplayZone(e.getCreatedAt()),
                toDisplayZone(resendAt(e)));
    }

    private EmailSendHisDetailResponse toDetailResponse(EmailSendHis e) {
        return new EmailSendHisDetailResponse(
                e.getId(),
                e.getEmailSendType(),
                e.getEmailSendDetailType(),
                e.getRecipientEmail(),
                e.getSubject(),
                e.getContent(),
                e.getSendStatus(),
                toDisplayZone(e.getCreatedAt()),
                toDisplayZone(resendAt(e)));
    }

    /** updated_at이 created_at보다 늦으면(=재발송됨) 그 시각을, 아니면 null을 반환 */
    private OffsetDateTime resendAt(EmailSendHis e) {
        return e.getUpdatedAt() != null && e.getUpdatedAt().isAfter(e.getCreatedAt()) ? e.getUpdatedAt() : null;
    }

    /**
     * DB에서 읽어온 시각(항상 UTC)을 응답으로 내보내기 직전에 사이트 시간대로 다시 환산한다.
     * 같은 순간을 가리키는 값이지만, 표시되는 시/분은 사이트 시간대 기준으로 바뀐다.
     */
    private OffsetDateTime toDisplayZone(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(siteTimeZoneResolver.resolveFromContext()).toOffsetDateTime();
    }
}

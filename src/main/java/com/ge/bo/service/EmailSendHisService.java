package com.ge.bo.service;

import com.ge.bo.common.mail.MailService;
import com.ge.bo.dto.EmailSendHisDetailResponse;
import com.ge.bo.dto.EmailSendHisResponse;
import com.ge.bo.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 이메일 발송 이력 조회/재발송 서비스
 * - 저장은 MailService.sendMail() 이 공통으로 처리한다(page_data, data_slug='emailSendHis-data')
 * - 조회 전용 테이블이 아니라 page_data JSONB 라 JPA Specification 대신 네이티브 쿼리로 직접 페이징/필터링한다
 *   (error/login/transaction 로그와 응답 형태(Page&lt;T&gt;)만 동일하게 맞춤)
 * - 재발송은 새 이력행을 만들지 않고 원본 행을 UPDATE한다(기획: 재발송 성공 시 원본 발송상태를 '성공'으로 갱신,
 *   최근재발송일시는 page_data.updated_at을 그대로 재사용 — created_at과 다르면 재발송된 것으로 판단).
 */
@Service
@RequiredArgsConstructor
public class EmailSendHisService {

    private static final String DATA_SLUG = "emailSendHis-data";

    private final EntityManager entityManager;
    private final MailService mailService;

    /* ══════════ 목록 조회 ══════════ */

    @Transactional(readOnly = true)
    public Page<EmailSendHisResponse> getList(String sendStatus, String emailSendType, String emailSendDetailType,
                                               String recipientEmail, OffsetDateTime startDate, OffsetDateTime endDate,
                                               Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE data_slug = :dataSlug");
        if (sendStatus != null && !sendStatus.isBlank()) {
            where.append(" AND data_json->'emailSendHis'->>'sendStatus' = :sendStatus");
        }
        if (emailSendType != null && !emailSendType.isBlank()) {
            where.append(" AND data_json->'emailSendHis'->>'emailSendType' = :emailSendType");
        }
        if (emailSendDetailType != null && !emailSendDetailType.isBlank()) {
            where.append(" AND data_json->'emailSendHis'->>'emailSendDetailType' = :emailSendDetailType");
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            where.append(" AND data_json->'emailSendHis'->>'recipientEmail' ILIKE :recipientEmail");
        }
        if (startDate != null) {
            where.append(" AND created_at >= :startDate");
        }
        if (endDate != null) {
            where.append(" AND created_at <= :endDate");
        }

        Query countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM page_data" + where);
        bindFilters(countQuery, sendStatus, emailSendType, emailSendDetailType, recipientEmail, startDate, endDate);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query listQuery = entityManager.createNativeQuery(
                "SELECT id,"
              + "  data_json->'emailSendHis'->>'emailSendType',"
              + "  data_json->'emailSendHis'->>'emailSendDetailType',"
              + "  data_json->'emailSendHis'->>'recipientEmail',"
              + "  data_json->'emailSendHis'->>'sendStatus',"
              + "  created_at,"
              + "  CASE WHEN updated_at > created_at THEN updated_at ELSE NULL END"
              + " FROM page_data" + where
              + " ORDER BY created_at DESC"
              + " LIMIT :limit OFFSET :offset");
        bindFilters(listQuery, sendStatus, emailSendType, emailSendDetailType, recipientEmail, startDate, endDate);
        listQuery.setParameter("limit", pageable.getPageSize());
        listQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = listQuery.getResultList();

        List<EmailSendHisResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(new EmailSendHisResponse(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    toOffsetDateTime(row[5]),
                    toOffsetDateTime(row[6])));
        }
        return new PageImpl<>(content, pageable, total);
    }

    /* ══════════ 단건 조회 ══════════ */

    @Transactional(readOnly = true)
    public EmailSendHisDetailResponse getOne(Long id) {
        Query query = entityManager.createNativeQuery(
                "SELECT id,"
              + "  data_json->'emailSendHis'->>'emailSendType',"
              + "  data_json->'emailSendHis'->>'emailSendDetailType',"
              + "  data_json->'emailSendHis'->>'recipientEmail',"
              + "  data_json->'emailSendHis'->>'subject',"
              + "  data_json->'emailSendHis'->>'content',"
              + "  data_json->'emailSendHis'->>'sendStatus',"
              + "  created_at,"
              + "  CASE WHEN updated_at > created_at THEN updated_at ELSE NULL END"
              + " FROM page_data"
              + " WHERE data_slug = :dataSlug AND id = :id");
        query.setParameter("dataSlug", DATA_SLUG);
        query.setParameter("id", id);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            throw ErrorCode.EMAIL_SEND_HIS_NOT_FOUND.toException();
        }
        Object[] row = rows.get(0);
        return new EmailSendHisDetailResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                toOffsetDateTime(row[7]),
                toOffsetDateTime(row[8]));
    }

    /* ══════════ 재발송 ══════════ */

    /**
     * 재발송 — 원본 이력에 저장된 subject/content/recipientEmail 그대로 다시 발송한다.
     * 기획: 새 이력행을 만들지 않고 원본 행을 그대로 갱신한다 — 재발송 성공 시 발송상태를 '성공'으로 갱신,
     * updated_at을 NOW()로 찍어 "최근재발송일시"로 사용한다(재발송 실패 시에도 시도 자체는 기록되도록 항상 갱신).
     */
    @Transactional
    public String resend(Long id) {
        EmailSendHisDetailResponse origin = getOne(id);
        String sendStatus = mailService.sendOnly(origin.recipientEmail(), origin.subject(), origin.content());

        Query updateQuery = entityManager.createNativeQuery(
                "UPDATE page_data"
              + " SET data_json = jsonb_set(data_json, '{emailSendHis,sendStatus}', to_jsonb(CAST(:sendStatus AS text))),"
              + "     updated_at = NOW()"
              + " WHERE data_slug = :dataSlug AND id = :id");
        updateQuery.setParameter("sendStatus", sendStatus);
        updateQuery.setParameter("dataSlug", DATA_SLUG);
        updateQuery.setParameter("id", id);
        updateQuery.executeUpdate();

        return sendStatus;
    }

    /* ══════════ 공통 ══════════ */

    private void bindFilters(Query query, String sendStatus, String emailSendType, String emailSendDetailType,
                              String recipientEmail, OffsetDateTime startDate, OffsetDateTime endDate) {
        query.setParameter("dataSlug", DATA_SLUG);
        if (sendStatus != null && !sendStatus.isBlank()) {
            query.setParameter("sendStatus", sendStatus);
        }
        if (emailSendType != null && !emailSendType.isBlank()) {
            query.setParameter("emailSendType", emailSendType);
        }
        if (emailSendDetailType != null && !emailSendDetailType.isBlank()) {
            query.setParameter("emailSendDetailType", emailSendDetailType);
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            query.setParameter("recipientEmail", "%" + recipientEmail.trim() + "%");
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
    }

    /** 네이티브 쿼리 결과(timestamptz 계열 다양한 JDBC 타입) → OffsetDateTime (TrainingRegistrationAdminService와 동일한 방어적 변환) */
    private OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof OffsetDateTime odt) return odt;
        if (obj instanceof ZonedDateTime zdt) return zdt.toOffsetDateTime();
        if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        try {
            return OffsetDateTime.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

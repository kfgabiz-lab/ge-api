package com.ge.bo.common.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.context.SiteTimeZoneResolver;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final SiteTimeZoneResolver siteTimeZoneResolver;

    private static final String EMAIL_SEND_SUCCESS = "S";
	private static final String EMAIL_SEND_FAIL    = "F";
    private static final DateTimeFormatter SENT_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // page_data.site_id 기본값 — 호출부에서 siteId 를 안 넘긴 경우에만 사용(기존 뉴스레터 하드코딩 값 유지)
    private static final Long DEFAULT_SITE_ID = 1L;

    /**
     * 메일 발송 + 발송 이력 저장(성공/실패 모두 기록) — 공통 진입점. 새 이력행을 생성한다(최초 발송용).
     * emailSendType: 공통코드 EMAILSENDTYPE (01=뉴스레터, 02=정기 Training, 03=비정기 Training)
     * emailSendDetailType: 공통코드 TRAININGCOURSE (01=Engineering/02=Service/03=Sales) — Training 계열만 사용, 뉴스레터는 null
     * @param to 수신자 이메일 1명 — 여러 명에게 보내야 하면 호출부에서 각자에게 한 번씩 개별 호출한다(발송 이력도 건별로 남아야 하므로)
     */
    @Transactional
    public String sendMail(String to, String subject, String content, String emailSendType,
                            String emailSendDetailType, Long siteId) {
        String sendStatus = doSend(to, subject, content);
        saveEmailSendHistory(sendStatus, to, subject, content, emailSendType, emailSendDetailType,
                siteId != null ? siteId : DEFAULT_SITE_ID);
        return sendStatus;
    }

    /**
     * 이력 저장 없이 메일만 발송 — 재발송 전용(EmailSendHisService가 원본 이력행을 직접 UPDATE하므로
     * 여기서 새 이력행을 만들면 안 된다).
     */
    public String sendOnly(String to, String subject, String content) {
        return doSend(to, subject, content);
    }

    //이메일 발송 내역 저장을 위해 return 방식 변경 void -> String
    private String doSend(String to, String subject, String content) {

        log.info("메일 전송 Start");
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom("elesmtp@ls-electric.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // false = plain text
            log.info("메일 세팅 완료");
            mailSender.send(message);
            log.info("메일 전송 완료");
            return EMAIL_SEND_SUCCESS;

        } catch (Exception e) {
            log.info("메일 전송 실패");
            log.info(e.getMessage());
            return EMAIL_SEND_FAIL;
        }
    }

    //이메일 발송 내역 저장 — NewsletterInsightsService 에 있던 로직을 공통화하여 이관
    //subject/content 도 함께 저장 — 재발송 기능(BO 이메일 이력 화면)이 저장된 내용 그대로 재사용한다
    private void saveEmailSendHistory(String sendStatus, String recipientEmail, String subject, String content,
                                       String emailSendType, String emailSendDetailType, Long siteId) {
        OffsetDateTime sentAt = OffsetDateTime.now(siteTimeZoneResolver.resolve(siteId));

        Map<String, Object> dataJson = new LinkedHashMap<>();
        Map<String, Object> emailSendHis = new LinkedHashMap<>();
        emailSendHis.put("emailSendType", emailSendType);   //분류(공통코드 EMAILSENDTYPE)
        emailSendHis.put("emailSendDetailType", emailSendDetailType); //상세분류(공통코드 TRAININGCOURSE, 뉴스레터는 null)
        emailSendHis.put("recipientEmail", recipientEmail); //수신Email
        emailSendHis.put("subject", subject);               //메일 제목(재발송용)
        emailSendHis.put("content", content);                //메일 본문 HTML(재발송용)
        emailSendHis.put("sendStatus", sendStatus);          //발송상태(공통코드 SENDSTATUS) — 재발송 성공 시 EmailSendHisService가 이 값을 갱신한다
        emailSendHis.put("sentAt", sentAt.format(SENT_AT_FORMAT)); //발송일시
        emailSendHis.put("lastResendAt", null);              //최근 재발송일시 — 재발송 전까지 null
        dataJson.put("emailSendHis", emailSendHis);

        try {
            String dataJsonStr = objectMapper.writeValueAsString(dataJson);

            Query insertQuery = entityManager.createNativeQuery("""
                    INSERT INTO page_data
                      (template_slug, data_slug, data_json, site_id, created_by, created_at, updated_by, updated_at)
                    VALUES
                      (:templateSlug, :dataSlug, CAST(:dataJson AS jsonb), :siteId, NULL, NOW(), NULL, NOW())
                    RETURNING id
                    """);
            insertQuery.setParameter("templateSlug", "emailSendHis-list");
            insertQuery.setParameter("dataSlug"    , "emailSendHis-data");
            insertQuery.setParameter("dataJson"    , dataJsonStr);
            insertQuery.setParameter("siteId"      , siteId);

            Long newId = ((Number) insertQuery.getSingleResult()).longValue();

            dataJson.put("id", newId);

            Query updateQuery = entityManager.createNativeQuery("""
                    UPDATE page_data
                    SET data_json = CAST(:dataJson AS jsonb)
                    WHERE id = :id
                    """);
            updateQuery.setParameter("dataJson", objectMapper.writeValueAsString(dataJson));
            updateQuery.setParameter("id"      , newId);
            updateQuery.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("이메일 발송 이력 저장 실패", e);
        }
    }
}

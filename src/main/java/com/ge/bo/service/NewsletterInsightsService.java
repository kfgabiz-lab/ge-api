package com.ge.bo.service;

import com.ge.bo.common.mail.MailService;
import com.ge.bo.dto.NewsletterInsightsRequest;
import com.ge.bo.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsletterInsightsService {
	
	//hub-spot 메일 미정으로 테스트
	//메일 수신자 공통코드로 수정
	private static final String EMAIL_RECIPIENT_GROUP_CODE      = "EMAIL_RECIPIENT"; //메일 수신자 공통코드
	private static final String EMAIL_RECIPIENT_NEWSLETTER_CODE = "NEWSLETTER";
    private static final DateTimeFormatter SUBJECT_DATE_FORMAT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MailService mailService;
    //page_data 저장용
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    
    //뉴스레터 메일 전송
    @Transactional
    public void send(NewsletterInsightsRequest request) {
    	// 사용자 접속 위치에 따른 timezone 설정 (FE에서 받아옴)
    	ZoneId KST         = ZoneId.of(request.userTimeZone()); 	
    	OffsetDateTime now = OffsetDateTime.now(KST);
    	
    	//메일 제목 및 내용 세팅
        String subject = "New Newsletter Subscriber (%s)".formatted(now.format(SUBJECT_DATE_FORMAT));
        String content = buildMailContent(request);
        
        //1. 공통코드 EMAIL_RECIPIENT 에서 CODE가 NEWSLETTER 인 수신자 이메일 조회
        String recipientEmail = findNewsletterRecipientEmailName();
        //2. 이메일 발송 후 발송 상태 반환
        String sendStatus     = mailService.sendMail(recipientEmail, subject, content);
        //3. 이메일 발송 내역 저장 호출
        saveEmailSendHistory(now, sendStatus, recipientEmail);
    }
    
    //메일 내용 세팅
    private String buildMailContent(NewsletterInsightsRequest request) {
        String email           = HtmlUtils.htmlEscape(request.email());
        String areasOfInterest = HtmlUtils.htmlEscape(request.areasOfInterest());

        return """
                <div style="font-family: Arial, sans-serif; font-size: 14px; color: #222;">
                    <p>- email : %s</p>
                    <p>- Areas of interest : %s</p>
                </div>
                """.formatted(email, areasOfInterest);
    }
    
    //이메일 발송 내역 저장
    private void saveEmailSendHistory(OffsetDateTime sentAt, String sendStatus, String recipientEmail) {
        Map<String, Object> dataJson = new LinkedHashMap<>();
        dataJson.put("emailSendHis", Map.of(
                     "emailSendType", "01",						  //분류(공통코드 EMAILSENDTYPE)
                     "recipientEmail", recipientEmail, 			  //수신Email
                     "sendStatus", sendStatus,					  //발송상태(공통코드 SENDSTATUS)
                     "sentAt", sentAt.format(SUBJECT_DATE_FORMAT) //발송일시
        ));

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
            insertQuery.setParameter("siteId"      , 1L);

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
    
    //공통코드 사용하여 수신자 이메일 주소 조회
    private String findNewsletterRecipientEmailName() {
        List<?> results = entityManager.createNativeQuery("""
                SELECT cd.name
                FROM code_detail cd
                JOIN code_group cg ON cg.id = cd.group_id
                WHERE cg.group_code = :groupCode
                  AND cd.code = :code
                """)
                .setParameter("groupCode", EMAIL_RECIPIENT_GROUP_CODE)
                .setParameter("code"     , EMAIL_RECIPIENT_NEWSLETTER_CODE)
                .setMaxResults(1)
                .getResultList();

        if (results.isEmpty()) {
            throw ErrorCode.CODE_DETAIL_NOT_FOUND.toException();
        }
        return String.valueOf(results.get(0));
    }
}
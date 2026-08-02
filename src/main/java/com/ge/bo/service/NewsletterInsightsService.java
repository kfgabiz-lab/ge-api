package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.mail.MailService;
import com.ge.bo.dto.NewsletterInsightsRequest;
import com.ge.bo.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsletterInsightsService {

	//hub-spot 메일 미정으로 테스트
	//메일 수신자 공통코드로 수정
	private static final String EMAIL_RECIPIENT_GROUP_CODE      = "EMAIL_RECIPIENT"; //메일 수신자 공통코드
	private static final String EMAIL_RECIPIENT_NEWSLETTER_CODE = "NEWSLETTER";
    private static final DateTimeFormatter SUBJECT_DATE_FORMAT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 공통코드 EMAILSENDTYPE — 뉴스레터
    private static final String EMAIL_SEND_TYPE_NEWSLETTER = "01";

    private final MailService mailService;
    //공통코드(수신자 이메일) 조회용
    private final EntityManager entityManager;
    private final SiteTimeZoneResolver siteTimeZoneResolver;

    //뉴스레터 메일 전송
    @Transactional
    public void send(NewsletterInsightsRequest request, Long siteId) {
    	// 사이트(X-Site-Id) timezone 기준 접수시각 — 없으면 서버 기본 zone으로 폴백
    	OffsetDateTime now = OffsetDateTime.now(siteTimeZoneResolver.resolve(siteId));

    	//메일 제목 및 내용 세팅
        String subject = "New Newsletter Subscriber (%s)".formatted(now.format(SUBJECT_DATE_FORMAT));
        String content = buildMailContent(request);

        //1. 공통코드 EMAIL_RECIPIENT 에서 CODE가 NEWSLETTER 인 수신자 이메일 조회
        String recipientEmail = findNewsletterRecipientEmailName();
        //2. 이메일 발송 — 발송 이력 저장(성공/실패 모두)은 MailService 가 공통으로 처리
        //   뉴스레터는 상세분류(TRAININGCOURSE) 대상이 아니므로 null
        mailService.sendMail(recipientEmail, subject, content, EMAIL_SEND_TYPE_NEWSLETTER, null, siteId);
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
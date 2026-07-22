package com.ge.bo.common.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
  //이메일 발송 내역 발송상태 저장을 위해 return 방식 변경 void -> String
    public String sendMail(String to, String subject, String content) {

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
            return "S"; 

        } catch (Exception e) {
            log.info("메일 전송 실패");
            log.info(e.getMessage());
//            throw new RuntimeException("메일 전송 실패", e);
            return "F";
        }
//        log.info("메일 전송 End");
    }
}



package com.ge.bo.common.mail;

import jakarta.mail.MessagingException;
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

    public void sendMail(String to, String subject, String content) {

        log.info("메일 전송 Start");
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // false = plain text
            log.info("메일 세팅 완료");
            mailSender.send(message);
            log.info("메일 전송 완료");

        } catch (Exception e) {
            log.info("메일 전송 실패");
            log.info(e.getMessage());
            throw new RuntimeException("메일 전송 실패", e);
        }
        log.info("메일 전송 End");
    }
}



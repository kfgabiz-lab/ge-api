package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 이메일 발송 이력 엔티티 — email_send_his 테이블 매핑
 * page_data(emailSendHis-data) FO 비인증 노출 문제로 전용 테이블 분리(docs/db/email-send-his/db_email-send-his.md)
 * 재발송 시 원본 행을 UPDATE해야 해서 BannerData와 동일한 감사(auditing) 자동화 패턴을 사용한다.
 */
@Entity
@Table(name = "email_send_his",
        indexes = {
                @Index(name = "email_send_his_created_at_idx", columnList = "created_at"),
                @Index(name = "email_send_his_recipient_email_idx", columnList = "recipient_email"),
                @Index(name = "email_send_his_send_status_idx", columnList = "send_status"),
                @Index(name = "email_send_his_email_send_type_idx", columnList = "email_send_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EmailSendHis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    /** 발송분류 — 공통코드 EMAILSENDTYPE(01=뉴스레터/02=정기 Training/03=비정기 Training) */
    @Column(name = "email_send_type", nullable = false, length = 10)
    private String emailSendType;

    /** 상세분류 — 공통코드 TRAININGCOURSE(01=Engineering/02=Service/03=Sales), 뉴스레터는 NULL */
    @Column(name = "email_send_detail_type", length = 10)
    private String emailSendDetailType;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    /** 메일 제목(재발송용) */
    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    /** 메일 본문 HTML(재발송용) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 발송상태 — 공통코드 SENDSTATUS(S=성공/F=실패) */
    @Column(name = "send_status", nullable = false, length = 1)
    private String sendStatus;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

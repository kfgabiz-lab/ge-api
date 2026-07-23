package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * FO Contact Us 문의 접수 엔티티 — contact_us_inquiry 테이블 매핑
 * 이력성(append-only) 테이블이므로 수정/삭제 없이 저장만 한다 (DownloadLog 패턴)
 * ※ 기존 CTP(Salesforce) 전송 도메인(ContactUsController/Service)과는 완전히 별개
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contact_us_inquiry",
        indexes = {
                // BO 관리자 조회 단계 대비 인덱스 (DB 설계서 3장)
                @Index(name = "IDX_CONTACT_US_INQUIRY_EMAIL", columnList = "email"),
                @Index(name = "IDX_CONTACT_US_INQUIRY_CREATED", columnList = "created_at")
        })
public class ContactUsInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문의유형 코드값 (공통코드 INQUIRY_TYPE, 예: PRODUCT_INFORMATION) */
    @Column(name = "inquiry_type", nullable = false, length = 30)
    private String inquiryType;

    /** 제품 카테고리 — 선택된 Lv1/Lv2/Lv3 라벨을 "카테고리1 | 카테고리2 | 카테고리3" 형태로 결합한 문자열 */
    @Column(name = "product_category", length = 1000)
    private String productCategory;

    /** 문의자 이메일 */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** 이름 (First Name) */
    @Column(name = "first_name", nullable = false, length = 255)
    private String firstName;

    /** 성 (Last Name) */
    @Column(name = "last_name", nullable = false, length = 255)
    private String lastName;

    /** 업체명 */
    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    /** 국가코드 (공통코드 COUNTRYCODE, ISO 3166-1 alpha-2 대문자, 예: US) */
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    /** 문의 내용 */
    @Column(name = "inquiry_content", nullable = false, columnDefinition = "TEXT")
    private String inquiryContent;

    /** 조회용 비밀번호 BCrypt 단방향 해시 (평문/복호화 저장 안 함) */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 마케팅/뉴스레터 수신 동의 여부 */
    @Column(name = "marketing_opt_in_flag", nullable = false)
    private Boolean marketingOptInFlag;

    /** 개인정보 수집·이용 동의 여부 (필수 동의) */
    @Column(name = "privacy_consent_flag", nullable = false)
    private Boolean privacyConsentFlag;

    /** 요청자 IP (감사/스팸 추적용, X-Forwarded-For 우선) */
    @Column(name = "created_ip", length = 50)
    private String createdIp;

    /** 접수일시 (TIME ZONE 포함) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ContactUsInquiry(String inquiryType, String productCategory,
                            String email, String firstName, String lastName, String companyName,
                            String country, String inquiryContent, String passwordHash,
                            Boolean marketingOptInFlag, Boolean privacyConsentFlag, String createdIp) {
        this.inquiryType = inquiryType;
        this.productCategory = productCategory;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.companyName = companyName;
        this.country = country;
        this.inquiryContent = inquiryContent;
        this.passwordHash = passwordHash;
        this.marketingOptInFlag = marketingOptInFlag;
        this.privacyConsentFlag = privacyConsentFlag;
        this.createdIp = createdIp;
        // 접수일시는 서버가 채운다 (DownloadLog 패턴)
        this.createdAt = OffsetDateTime.now();
    }
}

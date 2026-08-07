package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * FO 트레이닝 세션 등록(리드 캡처) 엔티티 — training_registration 테이블 매핑
 * 이력성(append-only) 테이블이므로 수정/삭제 없이 저장만 한다 (ContactUsInquiry 패턴 그대로).
 * ※ curriculum_id / session_id 는 page_data(id) 를 참조하는 코드값이지만, 과거 접수 데이터 무결성
 *   보존을 위해 JPA 연관관계 대신 scalar Long 컬럼으로 저장한다(ContactUsInquiry 와 동일 정책).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "training_registration",
        indexes = {
                // BO 관리자 조회 단계 대비 인덱스
                @Index(name = "training_registration_email_idx", columnList = "email"),
                @Index(name = "training_registration_created_at_idx", columnList = "created_at")
        })
public class TrainingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 커리큘럼(코스) page_data.id — 세션 상세 route 의 courseId(=curriculum_detail1.curriculum_id) */
    @Column(name = "curriculum_id", nullable = false)
    private Long curriculumId;

    /** 교육회차(세션) page_data.id — currDtlMgmt-data 행 PK(=route 의 sessionId) */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 수강자 이름 */
    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    /** 이메일 */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** 직함 */
    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    /** 전화번호(숫자만) */
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /** 회사명 */
    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    /** 참석 이벤트 일자(세션 시작일) */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** 도로명 주소(선택) */
    @Column(name = "street_address", length = 255)
    private String streetAddress;

    /** 상세 주소 2(선택) */
    @Column(name = "address2", length = 255)
    private String address2;

    /** 아파트/호수 등(선택) */
    @Column(name = "apartment", length = 100)
    private String apartment;

    /** 도시(선택) */
    @Column(name = "city", length = 100)
    private String city;

    /** 주/도(선택) */
    @Column(name = "state_province", length = 100)
    private String stateProvince;

    /** 우편번호(선택) */
    @Column(name = "zip_code", length = 20)
    private String zipCode;

    /** 비즈니스 유형 코드값(공통코드 BUSINESSTYPE: 001/002/003, 선택) */
    @Column(name = "type_of_business", length = 30)
    private String typeOfBusiness;

    /** 개인정보 수집·이용 동의 여부(필수 동의) */
    @Column(name = "privacy_consent_flag", nullable = false)
    private Boolean privacyConsentFlag;

    /** 요청자 IP (감사/스팸 추적용, getRemoteAddr() 기준) */
    @Column(name = "created_ip", length = 50)
    private String createdIp;

    /** 접수일시 (TIME ZONE 포함) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public TrainingRegistration(Long curriculumId, Long sessionId, String studentName, String email,
                                String jobTitle, String phone, String companyName, LocalDate eventDate,
                                String streetAddress, String address2, String apartment, String city,
                                String stateProvince, String zipCode, String typeOfBusiness,
                                Boolean privacyConsentFlag, String createdIp) {
        this.curriculumId = curriculumId;
        this.sessionId = sessionId;
        this.studentName = studentName;
        this.email = email;
        this.jobTitle = jobTitle;
        this.phone = phone;
        this.companyName = companyName;
        this.eventDate = eventDate;
        this.streetAddress = streetAddress;
        this.address2 = address2;
        this.apartment = apartment;
        this.city = city;
        this.stateProvince = stateProvince;
        this.zipCode = zipCode;
        this.typeOfBusiness = typeOfBusiness;
        this.privacyConsentFlag = privacyConsentFlag;
        this.createdIp = createdIp;
        // 접수일시는 서버가 채운다 (ContactUsInquiry/DownloadLog 패턴)
        this.createdAt = OffsetDateTime.now();
    }
}

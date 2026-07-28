package com.ge.bo.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * FO Training Request(비정기 교육 신청) 엔티티 — training_request 테이블 매핑
 * 이력성(append-only) 테이블이므로 수정/삭제 없이 저장만 한다 (TrainingRegistration 패턴 그대로).
 * ※ 신청자가 입력한 값을 그대로 보존하는 것이 목적이라 코드 변환/정규화 없이 문자열 그대로 저장한다.
 * ※ selectedProducts(선택 제품 — {id,name,type,groupId,groupTitle} 객체 배열)만 구조가 있어 PageData/HeroData 와
 *   동일한 hypersistence-utils @Type + String(JSON 문자열) 방식으로 매핑한다. PostgreSQL jsonb 컬럼에 JPA 로 직접
 *   insert 하려면 바인딩을 PGobject(jsonb)로 넘기는 {@code JsonBinaryType} 이어야 한다
 *   ({@code JsonStringType} 은 varchar 로 바인딩되어 jsonb 컬럼 입력 시 42804 오류 발생).
 * ※ jobTitles/studentInvolvement/vfdUnderstandingTopics 는 단순 문자열 배열이라 JSONB 가 아니라
 *   {@code BannerData.imageFileId} 와 동일한 PostgreSQL 네이티브 배열({@code text[]}) 로 매핑한다
 *   (별도 라이브러리·JSON 직렬화 없이 Hibernate 표준 {@code @JdbcTypeCode(SqlTypes.ARRAY)} 만으로 충분).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "training_request",
        indexes = {
                // BO 관리자 조회 단계 대비 인덱스
                @Index(name = "training_request_email_idx", columnList = "email"),
                @Index(name = "training_request_created_at_idx", columnList = "created_at")
        })
public class TrainingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Step1: Basic Information =====

    /** 교육 트랙(Engineering Training 등 옵션 id) */
    @Column(name = "training_track", length = 50)
    private String trainingTrack;

    /** 이름 */
    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    /** 성(선택) */
    @Column(name = "last_name", length = 200)
    private String lastName;

    /** 회사명 */
    @Column(name = "company", nullable = false, length = 200)
    private String company;

    /** 도로명 주소 */
    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    /** 상세 주소 2(선택) */
    @Column(name = "address2", length = 255)
    private String address2;

    /** 도시 */
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** 주/도 */
    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /** 우편번호 */
    @Column(name = "zip", nullable = false, length = 20)
    private String zip;

    /** 전화번호 */
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /** 이메일 */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** 직함(선택) */
    @Column(name = "title", length = 200)
    private String title;

    /** 휴대전화(선택) */
    @Column(name = "cell_phone", length = 20)
    private String cellPhone;

    /** 담당 영업사원 연락처(선택) */
    @Column(name = "sales_contact", length = 20)
    private String salesContact;

    // ===== Step2: Requested Date(s) of Training =====

    /** 요청 교육 횟수 */
    @Column(name = "session_count", nullable = false, length = 200)
    private String sessionCount;

    /** 교육 기간(일수) */
    @Column(name = "session_days", nullable = false, length = 20)
    private String sessionDays;

    /** 희망 교육 시작일 */
    @Column(name = "schedule_start", nullable = false)
    private LocalDate scheduleStart;

    /** 희망 교육 종료일 */
    @Column(name = "schedule_end", nullable = false)
    private LocalDate scheduleEnd;

    /** 참가 예정 인원 */
    @Column(name = "student_count", nullable = false, length = 20)
    private String studentCount;

    // ===== Step3: Training Class Location =====

    /** 교육 형태(In-Person / Virtual) */
    @Column(name = "training_format", nullable = false, length = 20)
    private String trainingFormat;

    /** 교육 장소명 — In-Person 일 때만 입력 */
    @Column(name = "location_name", length = 200)
    private String locationName;

    /** 교육 장소 도로명 주소 — In-Person 일 때만 입력 */
    @Column(name = "location_street_address", length = 255)
    private String locationStreetAddress;

    /** 교육 장소 상세 주소 2 — In-Person 일 때만 입력 */
    @Column(name = "location_address2", length = 255)
    private String locationAddress2;

    /** 교육 장소 도시 — In-Person 일 때만 입력 */
    @Column(name = "location_city", length = 100)
    private String locationCity;

    /** 교육 장소 주/도 — In-Person 일 때만 입력 */
    @Column(name = "location_state", length = 100)
    private String locationState;

    /** 교육 장소 우편번호 — In-Person 일 때만 입력 */
    @Column(name = "location_zip", length = 20)
    private String locationZip;

    /** 현장 담당자 — In-Person 일 때만 입력 */
    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    /** 현장 담당자 연락처/비고 — In-Person 일 때만 입력 */
    @Column(name = "contact_details", length = 400)
    private String contactDetails;

    // ===== Step4: Training Class Details =====

    /** 선택 제품 목록 JSON 배열 — [{id,name,type,groupId,groupTitle}] */
    @Type(JsonBinaryType.class)
    @Column(name = "selected_products", nullable = false, columnDefinition = "jsonb")
    private String selectedProducts;

    /** 수강자 직무 배열(VFD 조건부 질문, 선택) */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_titles", columnDefinition = "text[]")
    private List<String> jobTitles;

    /** 수강자 업무 관여도 배열(VFD 조건부 질문, 선택) */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "student_involvement", columnDefinition = "text[]")
    private List<String> studentInvolvement;

    /** VFD 이해도 응답(Yes/No, 선택) */
    @Column(name = "vfd_understanding", length = 10)
    private String vfdUnderstanding;

    /** VFD 이해 희망 주제 배열(선택) */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "vfd_understanding_topics", columnDefinition = "text[]")
    private List<String> vfdUnderstandingTopics;

    /** 의견/질문(선택) */
    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    /** 개인정보 수집·이용 동의 여부(필수 동의) */
    @Column(name = "consent_checked", nullable = false)
    private Boolean consentChecked;

    // ===== 감사 =====

    /** 요청자 IP (감사/스팸 추적용, X-Forwarded-For 우선) */
    @Column(name = "created_ip", length = 50)
    private String createdIp;

    /** 접수일시 (TIME ZONE 포함) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public TrainingRequest(String trainingTrack, String firstName, String lastName, String company,
                           String streetAddress, String address2, String city, String state, String zip,
                           String phone, String email, String title, String cellPhone, String salesContact,
                           String sessionCount, String sessionDays, LocalDate scheduleStart, LocalDate scheduleEnd,
                           String studentCount, String trainingFormat, String locationName,
                           String locationStreetAddress, String locationAddress2, String locationCity,
                           String locationState, String locationZip, String contactPerson, String contactDetails,
                           String selectedProducts, List<String> jobTitles, List<String> studentInvolvement,
                           String vfdUnderstanding, List<String> vfdUnderstandingTopics, String comments,
                           Boolean consentChecked, String createdIp) {
        this.trainingTrack = trainingTrack;
        this.firstName = firstName;
        this.lastName = lastName;
        this.company = company;
        this.streetAddress = streetAddress;
        this.address2 = address2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.phone = phone;
        this.email = email;
        this.title = title;
        this.cellPhone = cellPhone;
        this.salesContact = salesContact;
        this.sessionCount = sessionCount;
        this.sessionDays = sessionDays;
        this.scheduleStart = scheduleStart;
        this.scheduleEnd = scheduleEnd;
        this.studentCount = studentCount;
        this.trainingFormat = trainingFormat;
        this.locationName = locationName;
        this.locationStreetAddress = locationStreetAddress;
        this.locationAddress2 = locationAddress2;
        this.locationCity = locationCity;
        this.locationState = locationState;
        this.locationZip = locationZip;
        this.contactPerson = contactPerson;
        this.contactDetails = contactDetails;
        this.selectedProducts = selectedProducts;
        this.jobTitles = jobTitles;
        this.studentInvolvement = studentInvolvement;
        this.vfdUnderstanding = vfdUnderstanding;
        this.vfdUnderstandingTopics = vfdUnderstandingTopics;
        this.comments = comments;
        this.consentChecked = consentChecked;
        this.createdIp = createdIp;
        // 접수일시는 서버가 채운다 (TrainingRegistration/ContactUsInquiry 패턴)
        this.createdAt = OffsetDateTime.now();
    }
}

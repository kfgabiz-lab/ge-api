package com.ge.bo.service;

import com.ge.bo.dto.ContactUsInquiryRequest;
import com.ge.bo.dto.ContactUsInquiryResponse;
import com.ge.bo.entity.ContactUsInquiry;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.ContactUsInquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FO Contact Us 문의 접수 저장 서비스
 * - 공통코드(INQUIRY_TYPE/COUNTRY) 실시간 검증 → 비밀번호 교차검증 → BCrypt 해시 → 저장
 * ※ 기존 CTP 전송용 ContactUsService와는 완전히 별개 도메인
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactUsInquiryService {

    /** 공통코드 그룹 코드 — 문의유형 */
    private static final String GROUP_INQUIRY_TYPE = "INQUIRY_TYPE";
    /** 공통코드 그룹 코드 — 국가 */
    private static final String GROUP_COUNTRY = "COUNTRY";

    private final ContactUsInquiryRepository contactUsInquiryRepository;
    private final CodeDetailRepository codeDetailRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 문의 접수 저장
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 X-Forwarded-For 우선 추출)
     * @return 저장 결과 (성공 여부 + 생성 PK)
     */
    @Transactional
    public ContactUsInquiryResponse submit(ContactUsInquiryRequest request, String clientIp) {

        // 1) 공통코드 검증 — 활성 코드값만 통과 (BO에서 코드 추가/비활성해도 소스 수정 불필요)
        if (!codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_INQUIRY_TYPE, request.type())) {
            throw BusinessException.badRequest("유효하지 않은 문의 유형입니다.");
        }
        if (!codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(GROUP_COUNTRY, request.country())) {
            throw BusinessException.badRequest("유효하지 않은 국가입니다.");
        }

        // 2) 비밀번호 교차검증 (confirmPassword는 저장하지 않고 일치 여부만 확인)
        if (!request.password().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("비밀번호가 일치하지 않습니다.");
        }

        // 3) 비밀번호 BCrypt 단방향 해시
        String passwordHash = passwordEncoder.encode(request.password());

        // 4) 엔티티 빌드 (요청 필드 매핑 + 서버 채움값)
        ContactUsInquiry entity = ContactUsInquiry.builder()
                .inquiryType(request.type())
                .productCategoryLv1(request.productCategoryLv1())
                .productCategoryLv2(request.productCategoryLv2())
                .productCategoryLv3(request.productCategoryLv3())
                .productCategoryLv1Id(request.productCategoryLv1Id())
                .productCategoryLv2Id(request.productCategoryLv2Id())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .companyName(request.companyName())
                .country(request.country())
                .inquiryContent(request.description())
                .passwordHash(passwordHash)
                .marketingOptInFlag(request.marketingOptInFlag())
                .privacyConsentFlag(request.privacyConsentFlag())
                .createdIp(clientIp)
                .build();

        // 5) 저장
        ContactUsInquiry saved = contactUsInquiryRepository.save(entity);

        // 개인정보(이메일/이름/문의내용 등)는 로그로 남기지 않는다 — id/성공 여부만 기록
        log.info("Contact Us 문의 접수 저장 완료 - id={}", saved.getId());

        return ContactUsInquiryResponse.success(saved.getId());
    }
}

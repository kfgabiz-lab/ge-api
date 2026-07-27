package com.ge.bo.service;

import com.ge.bo.dto.TrainingRegistrationRequest;
import com.ge.bo.dto.TrainingRegistrationResponse;
import com.ge.bo.entity.TrainingRegistration;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.TrainingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * FO 트레이닝 세션 등록(리드 캡처) 접수 서비스
 * - 처리 순서: reCAPTCHA 검증 → typeOfBusiness 공통코드(BUSINESSTYPE) 검증(값 있을 때만) → insert
 * - ContactUsInquiryService 의 레이어 구조를 그대로 본떠 작성(공통코드 검증도 동일 메서드 재사용).
 * - 메일 발송은 스코프 제외. 세션/코스 id 의 부모-자식 일치 검증은 하지 않는다(과설계 방지, 사용자 확정).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRegistrationService {

    /** 공통코드 그룹 코드 — 비즈니스 유형(기존 그룹 재사용) */
    private static final String GROUP_BUSINESS_TYPE = "BUSINESSTYPE";

    private final TrainingRegistrationRepository trainingRegistrationRepository;
    private final CodeDetailRepository codeDetailRepository;
    private final RecaptchaService recaptchaService;

    /**
     * 등록 접수 처리 — reCAPTCHA 검증 → 코드 검증 → 저장 → 결과 반환
     *
     * @param request  폼 요청 (Bean Validation 통과 후 진입)
     * @param clientIp 요청자 IP (컨트롤러에서 X-Forwarded-For 우선 추출)
     * @return 저장 성공 결과(success/id/message)
     */
    @Transactional
    public TrainingRegistrationResponse submit(TrainingRegistrationRequest request, String clientIp) {

        // 1) reCAPTCHA 검증 (실패 시 BusinessException 발생 → 400)
        recaptchaService.verify(request.recaptchaToken());

        // 2) 비즈니스 유형 검증 — 선택 필드이므로 값이 있을 때만 활성 코드값인지 확인(기존 메서드 재사용)
        if (StringUtils.isNotBlank(request.typeOfBusiness())
                && !codeDetailRepository.existsByGroup_GroupCodeAndCodeAndActiveTrue(
                        GROUP_BUSINESS_TYPE, request.typeOfBusiness())) {
            throw BusinessException.badRequest("유효하지 않은 비즈니스 유형입니다.");
        }

        // 3) 참석 일자 파싱("yyyy-MM-dd")
        LocalDate eventDate = parseEventDate(request.eventDate());

        // 4) 저장 (append-only)
        TrainingRegistration entity = TrainingRegistration.builder()
                .curriculumId(request.curriculumId())
                .sessionId(request.sessionId())
                .studentName(request.studentName())
                .email(request.email())
                .jobTitle(request.jobTitle())
                .phone(request.phone())
                .companyName(request.companyName())
                .eventDate(eventDate)
                .streetAddress(blankToNull(request.streetAddress()))
                .address2(blankToNull(request.address2()))
                .apartment(blankToNull(request.apartment()))
                .city(blankToNull(request.city()))
                .stateProvince(blankToNull(request.stateProvince()))
                .zipCode(blankToNull(request.zipCode()))
                .typeOfBusiness(blankToNull(request.typeOfBusiness()))
                .privacyConsentFlag(request.privacyConsentFlag())
                .createdIp(clientIp)
                .build();

        TrainingRegistration saved = trainingRegistrationRepository.save(entity);

        // 개인정보(이름/이메일 등)는 로그에 남기지 않고 추적용 식별값만 기록
        log.info("Training 세션 등록 접수 저장 완료 - id={}, curriculumId={}, sessionId={}",
                saved.getId(), saved.getCurriculumId(), saved.getSessionId());

        return TrainingRegistrationResponse.success(saved.getId());
    }

    /** "yyyy-MM-dd" 파싱. 형식 불량 시 400. */
    private LocalDate parseEventDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("참석 일자 형식이 올바르지 않습니다.");
        }
    }

    /** 선택 문자열 필드 — 공백/빈 문자열은 NULL 로 저장 */
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}

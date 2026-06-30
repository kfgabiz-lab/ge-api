package com.ge.bo.service;

import com.ge.bo.dto.LoginLogDetailResponse;
import com.ge.bo.dto.LoginLogResponse;
import com.ge.bo.entity.LoginLog;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.LoginLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 접속이력 서비스
 * - @Async: 메인 로그인 응답과 분리하여 비동기로 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     *
     * @param status     로그인 결과 (SUCCESS / FAIL, null이면 전체)
     * @param loginEmail 이메일 키워드 (null이면 전체)
     * @param startDate  시작일시 (null이면 전체)
     * @param endDate    종료일시 (null이면 전체)
     * @param pageable   페이지 정보
     */
    @Transactional(readOnly = true)
    public Page<LoginLogResponse> getList(String status, String loginEmail,
                                          OffsetDateTime startDate, OffsetDateTime endDate,
                                          Pageable pageable) {
        Specification<LoginLog> spec = buildSpec(status, loginEmail, startDate, endDate);
        return loginLogRepository.findAll(spec, pageable).map(LoginLogResponse::from);
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 접속이력 단건 상세 조회 — userAgent 포함
     */
    @Transactional(readOnly = true)
    public LoginLogDetailResponse getOne(Long id) {
        LoginLog loginLog = loginLogRepository.findById(id)
                .orElseThrow(ErrorCode.LOGIN_LOG_NOT_FOUND::toException);
        return LoginLogDetailResponse.from(loginLog);
    }

    /* ══════════ 비동기 저장 ══════════ */

    /**
     * 접속이력 비동기 저장
     *
     * 사용법:
     *   loginLogService.saveAsync(admin.getId(), admin.getEmail(), "SUCCESS", null, clientIp, userAgent);
     *   loginLogService.saveAsync(null, request.getEmail(), "FAIL", "USER_NOT_FOUND", clientIp, userAgent);
     *
     * @param adminUserId 관리자 ID (이메일 미존재 시 null)
     * @param loginEmail  로그인 시도 이메일
     * @param status      SUCCESS / FAIL
     * @param failReason  실패 사유 코드 (성공 시 null)
     * @param clientIp    클라이언트 IP
     * @param userAgent   브라우저 User-Agent
     */
    @Async
    public void saveAsync(Long adminUserId, String loginEmail, String status,
                          String failReason, String clientIp, String userAgent) {
        try {
            LoginLog loginLog = LoginLog.builder()
                    .adminUserId(adminUserId)
                    .loginEmail(loginEmail != null ? loginEmail : "")
                    .status(status)
                    .failReason(failReason)
                    .clientIp(clientIp)
                    // userAgent가 500자를 초과할 경우 잘라냄
                    .userAgent(userAgent != null && userAgent.length() > 500
                            ? userAgent.substring(0, 500) : userAgent)
                    .build();
            loginLogRepository.save(loginLog);
        } catch (Exception e) {
            // 로그 저장 실패가 로그인 기능에 영향을 주지 않도록 예외를 삼킴
            log.warn("접속이력 저장 실패: {}", e.getMessage());
        }
    }

    /* ══════════ 동적 필터 ══════════ */

    private Specification<LoginLog> buildSpec(String status, String loginEmail,
                                              OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (loginEmail != null && !loginEmail.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("loginEmail")),
                        "%" + loginEmail.trim().toLowerCase() + "%"));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

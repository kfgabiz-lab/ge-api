package com.ge.bo.service;

import com.ge.bo.dto.LoginLogDetailResponse;
import com.ge.bo.dto.LoginLogResponse;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.LoginLog;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.AdminRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 접속이력 서비스
 * - @Async: 메인 로그인 응답과 분리하여 비동기로 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;
    private final AdminRepository adminRepository;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     * loginEmail은 더 이상 login_log에 저장되지 않으므로, admin_user.employee_id를
     * 먼저 키워드로 찾아 그 admin_user_id 목록으로 필터링한다.
     *
     * @param status     로그인 결과 (SUCCESS / FAIL, null이면 전체)
     * @param loginEmail 계정(employee_id) 키워드 (null이면 전체)
     * @param startDate  시작일시 (null이면 전체)
     * @param endDate    종료일시 (null이면 전체)
     * @param siteId     사이트 ID (X-Site-Id 헤더, null이면 전체)
     * @param pageable   페이지 정보
     */
    @Transactional(readOnly = true)
    public Page<LoginLogResponse> getList(String status, String loginEmail,
                                          OffsetDateTime startDate, OffsetDateTime endDate,
                                          Long siteId, Pageable pageable) {
        List<Long> matchedAdminIds = null;
        if (loginEmail != null && !loginEmail.isBlank()) {
            matchedAdminIds = adminRepository.findByEmployeeIdContainingIgnoreCase(loginEmail.trim())
                    .stream().map(AdminUser::getId).toList();
            if (matchedAdminIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        Specification<LoginLog> spec = buildSpec(status, matchedAdminIds, startDate, endDate, siteId);
        Page<LoginLog> page = loginLogRepository.findAll(spec, pageable);
        Map<Long, String> employeeIdByAdminId = employeeIdByAdminId(page.getContent());

        return page.map(log -> LoginLogResponse.from(log, employeeIdByAdminId.get(log.getAdminUserId())));
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 접속이력 단건 상세 조회 — userAgent 포함
     */
    @Transactional(readOnly = true)
    public LoginLogDetailResponse getOne(Long id, Long siteId) {
        LoginLog loginLog = loginLogRepository.findById(id)
                .orElseThrow(ErrorCode.LOGIN_LOG_NOT_FOUND::toException);
        if (siteId != null && !siteId.equals(loginLog.getSiteId())) {
            throw ErrorCode.LOGIN_LOG_NOT_FOUND.toException();
        }
        String employeeId = loginLog.getAdminUserId() == null ? null
                : adminRepository.findById(loginLog.getAdminUserId())
                        .map(AdminUser::getEmployeeId).orElse(null);
        return LoginLogDetailResponse.from(loginLog, employeeId);
    }

    /** admin_user_id → employee_id 일괄 조회 (목록 페이지당 1쿼리) */
    private Map<Long, String> employeeIdByAdminId(List<LoginLog> logs) {
        Set<Long> adminIds = logs.stream()
                .map(LoginLog::getAdminUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (adminIds.isEmpty()) {
            return Map.of();
        }
        return adminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getEmployeeId));
    }

    /* ══════════ 비동기 저장 ══════════ */

    /**
     * 접속이력 비동기 저장
     *
     * 사용법:
     *   loginLogService.saveAsync(admin.getId(), admin.getEmail(), "SUCCESS", null, clientIp, userAgent, siteId);
     *   loginLogService.saveAsync(null, request.getEmail(), "FAIL", "USER_NOT_FOUND", clientIp, userAgent, siteId);
     *
     * @param adminUserId 관리자 ID (이메일 미존재 시 null)
     * @param loginEmail  (미사용 — 더 이상 저장하지 않음, 항상 null로 기록. 호출부 시그니처 호환을 위해 유지)
     * @param status      SUCCESS / FAIL
     * @param failReason  실패 사유 코드 (성공 시 null)
     * @param clientIp    클라이언트 IP
     * @param userAgent   브라우저 User-Agent
     * @param siteId      요청 사이트 ID (요청 스레드에서 SiteContext.getSiteId()로 미리 추출 —
     *                    @Async 스레드에는 ThreadLocal이 전파되지 않으므로 반드시 파라미터로 전달)
     */
    @Async
    public void saveAsync(Long adminUserId, String loginEmail, String status,
                          String failReason, String clientIp, String userAgent, Long siteId) {
        try {
            LoginLog loginLog = LoginLog.builder()
                    .adminUserId(adminUserId)
                    .loginEmail(null) // 로그인 시도 계정 문자열은 더 이상 기록하지 않음 (요청에 의해 null 고정)
                    .status(status)
                    .failReason(failReason)
                    .siteId(siteId)
                    // clientIp가 50자를 초과할 경우 잘라냄
                    .clientIp(clientIp != null && clientIp.length() > 50
                            ? clientIp.substring(0, 50) : clientIp)
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

    private Specification<LoginLog> buildSpec(String status, List<Long> matchedAdminIds,
                                              OffsetDateTime startDate, OffsetDateTime endDate,
                                              Long siteId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (matchedAdminIds != null) {
                predicates.add(root.get("adminUserId").in(matchedAdminIds));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (siteId != null) {
                predicates.add(cb.equal(root.get("siteId"), siteId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

package com.ge.bo.service;

import com.ge.bo.dto.TransactionLogDetailResponse;
import com.ge.bo.dto.TransactionLogResponse;
import com.ge.bo.entity.TransactionLog;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.TransactionLogRepository;
import io.micrometer.common.util.StringUtils;
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
import java.util.regex.Pattern;

/**
 * 트랜잭션 로그 서비스
 * - @Async: 메인 응답과 분리하여 비동기로 DB에 저장
 *
 * 사용법:
 *   transactionLogService.saveAsync(method, requestUrl, requestBody, httpStatus, clientIp, durationMs, loginUser, siteId);
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLogService {

  private final TransactionLogRepository transactionLogRepository;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     *
     * @param httpStatus  상태코드 (null이면 전체)
     * @param startDate   시작일시 (null이면 전체)
     * @param endDate     종료일시 (null이면 전체)
     * @param actionType  변경유형 키워드 (null이면 전체)
     * @param loginUser   사용자 키워드 (null이면 전체)
     * @param pageable    페이지 정보
     */
    @Transactional(readOnly = true)
    public Page<TransactionLogResponse> getList(Integer httpStatus, OffsetDateTime startDate,
                                                OffsetDateTime endDate, String actionType,
                                                String loginUser, Pageable pageable) {
        Specification<TransactionLog> spec = buildSpec(httpStatus, startDate, endDate, actionType, loginUser);
        return transactionLogRepository.findAll(spec, pageable).map(TransactionLogResponse::from);
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 트랜잭션 로그 단건 상세 조회 — requestBody 포함
     */
    @Transactional(readOnly = true)
    public TransactionLogDetailResponse getOne(Long id) {
        TransactionLog transactionLog = transactionLogRepository.findById(id)
                .orElseThrow(ErrorCode.TRANSACTION_LOG_NOT_FOUND::toException);
        return TransactionLogDetailResponse.from(transactionLog);
    }

    /* ══════════ 동적 필터 ══════════ */

    private Specification<TransactionLog> buildSpec(Integer httpStatus, OffsetDateTime startDate,
                                                    OffsetDateTime endDate, String actionType,
                                                    String loginUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (httpStatus != null) {
                predicates.add(cb.equal(root.get("httpStatus"), httpStatus));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("actionType")),
                        "%" + actionType.trim().toUpperCase() + "%"));
            }
            if (loginUser != null && !loginUser.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("loginUser")),
                        "%" + loginUser.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /* ══════════ 비동기 저장 ══════════ */

  /** 민감 정보 필드 마스킹 패턴 (password, passwordHash, passwd, pwd, secret, credentials) */
  private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
      "(?i)(\"(?:password|passwordHash|passwd|pwd|secret|credentials)\"\\s*:\\s*\")([^\"]*)(\")",
      Pattern.CASE_INSENSITIVE);

  /**
   * 트랜잭션 로그 비동기 저장
   *
   * @param method      HTTP 메서드 (POST / PUT / PATCH / DELETE)
   * @param requestUrl  요청 URL
   * @param requestBody 요청 바디 원문
   * @param httpStatus  응답 상태코드
   * @param clientIp    클라이언트 IP
   * @param durationMs  처리 시간(ms)
   * @param loginUser   로그인 사용자 이메일 (요청 스레드에서 미리 추출 — @Async 스레드 SecurityContext 미전파 방지)
   * @param siteId      요청 사이트 ID (요청 스레드에서 SiteContext.getSiteId()로 미리 추출 — 위와 동일 사유)
   */
  @Async
  public void saveAsync(String method, String requestUrl, String requestBody,
      int httpStatus, String clientIp, long durationMs, String loginUser, Long siteId) {
    try {
      TransactionLog transactionLog = TransactionLog.builder()
          .actionType(resolveActionType(method))
          .method(method)
          .requestUrl(requestUrl)
          .requestBody(maskSensitiveFields(requestBody))
          .httpStatus(httpStatus)
          .loginUser(loginUser)
          .clientIp(clientIp)
          .durationMs(durationMs)
          .siteId(siteId)
          .build();

      transactionLogRepository.save(transactionLog);
    } catch (Exception e) {
      // 로그 저장 실패가 메인 기능에 영향을 주지 않도록 예외를 삼킴
      log.warn("트랜잭션 로그 저장 실패: {}", e.getMessage());
    }
  }

  /** HTTP 메서드 → action_type 변환 */
  private String resolveActionType(String method) {
    return switch (method.toUpperCase()) {
      case "POST"          -> "CREATE";
      case "PUT", "PATCH"  -> "UPDATE";
      case "DELETE"        -> "DELETE";
      default              -> "UNKNOWN";
    };
  }

  /** 요청 바디에서 민감 필드값을 **** 로 치환 */
  private String maskSensitiveFields(String body) {
    if (StringUtils.isBlank(body)) {
      return body;
    }
    return SENSITIVE_PATTERN.matcher(body).replaceAll("$1****$3");
  }

}

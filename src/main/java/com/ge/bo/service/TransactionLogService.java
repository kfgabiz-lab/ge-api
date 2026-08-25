package com.ge.bo.service;

import com.ge.bo.common.crypto.Aes256Utils;
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
import java.util.function.Function;
import java.util.regex.Matcher;
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
  private final Aes256Utils aes256Utils;

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
        return TransactionLogDetailResponse.from(transactionLog, decryptSensitiveFields(transactionLog.getRequestBody()));
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

  /** 민감 정보 필드 마스킹 패턴(비가역) — 자격증명류는 복호화해서 볼 필요가 없어 계속 **** 처리 */
  private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
      "(?i)(\"(?:password|passwordHash|passwd|pwd|secret|credentials|confirmPassword)\"\\s*:\\s*\")([^\"]*)(\")",
      Pattern.CASE_INSENSITIVE);

  /**
   * 개인정보 필드 암호화 패턴(가역, AES256) — FO 리드캡처 폼(Contact Us/Training/Newsletter) 실제 필드명 전수
   * requesterName: CtpContactUsPayload(서버→CTP 전송 payload) 전용 필드 — 호출부에서 이미 암호화되어 들어오므로
   * saveExternalAsync 경로에서는 이중암호화 없이 조회 시 복호화 대상으로만 사용된다
   */
  private static final Pattern PII_ENCRYPT_PATTERN = Pattern.compile(
      "(?i)(\"(?:email|firstName|lastName|studentName|companyName|company|phone|cellPhone|jobTitle|"
      + "streetAddress|address2|apartment|city|stateProvince|state|zipCode|zip|contactPerson|contactDetails|"
      + "requesterName)\""
      + "\\s*:\\s*\")([^\"]*)(\")",
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
          .requestBody(encryptSensitiveFields(maskSensitiveFields(requestBody)))
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

  /**
   * 외부 API(서버→서버) 호출 트랜잭션 로그 비동기 저장
   * FE→BE 요청(TransactionLogFilter)과 달리 clientIp/loginUser/siteId가 없고,
   * 개인정보 필드는 호출부에서 이미 암호화(cryptoUtil.encrypt)된 상태로 넘어오므로
   * 이중 암호화를 피하기 위해 encryptSensitiveFields는 적용하지 않고 마스킹만 적용한다.
   *
   * @param method      HTTP 메서드
   * @param requestUrl  외부 API 요청 URL
   * @param requestBody 요청 바디 원문(JSON) — PII 필드는 호출부에서 이미 암호화됨
   * @param httpStatus  외부 API 응답 상태코드
   * @param durationMs  처리 시간(ms)
   */
  @Async
  public void saveExternalAsync(String method, String requestUrl, String requestBody,
      int httpStatus, long durationMs) {
    try {
      TransactionLog transactionLog = TransactionLog.builder()
          .actionType("EXTERNAL")
          .method(method)
          .requestUrl(requestUrl)
          .requestBody(maskSensitiveFields(requestBody))
          .httpStatus(httpStatus)
          .loginUser(null)
          .clientIp(null)
          .durationMs(durationMs)
          .siteId(null)
          .build();

      transactionLogRepository.save(transactionLog);
    } catch (Exception e) {
      // 로그 저장 실패가 메인 기능에 영향을 주지 않도록 예외를 삼킴
      log.warn("외부 API 트랜잭션 로그 저장 실패: {}", e.getMessage());
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

  /**
   * 개인정보 필드값을 AES256으로 암호화 (저장 시점).
   * 암호화 키(CONNECT_PORTAL_ENC_KEY/IV) 미설정 등으로 개별 필드 암호화가 실패해도
   * 로그 저장 자체가 막히면 안 되므로, 그 필드만 **** 마스킹으로 대체한다.
   */
  private String encryptSensitiveFields(String body) {
    return transformSensitiveFields(body, value -> {
      try {
        return aes256Utils.encrypt(value);
      } catch (Exception e) {
        log.warn("PII 필드 암호화 실패, 마스킹으로 대체: {}", e.getMessage());
        return "****";
      }
    });
  }

  /**
   * 개인정보 필드값을 복호화 (조회 시점).
   * 이 필드가 암호화 적용 이전(레거시)에 저장된 평문이면 복호화가 실패하므로,
   * 그 경우 원문을 그대로 반환한다 — 과거 로그도 계속 볼 수 있어야 하기 때문.
   */
  private String decryptSensitiveFields(String body) {
    return transformSensitiveFields(body, value -> {
      try {
        return aes256Utils.decrypt(value);
      } catch (Exception e) {
        return value;
      }
    });
  }

  private String transformSensitiveFields(String body, Function<String, String> transform) {
    if (StringUtils.isBlank(body)) {
      return body;
    }
    Matcher matcher = PII_ENCRYPT_PATTERN.matcher(body);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value = matcher.group(2);
      String replacement = value.isEmpty() ? value : transform.apply(value);
      matcher.appendReplacement(result,
          Matcher.quoteReplacement(matcher.group(1) + replacement + matcher.group(3)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

}

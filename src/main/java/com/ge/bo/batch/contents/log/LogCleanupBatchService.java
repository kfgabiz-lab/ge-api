package com.ge.bo.batch.contents.log;


import com.ge.bo.repository.LoginLogRepository;
import com.ge.bo.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 로그인 및 트랜잭션 로그 정리 배치 서비스.
 *
 * - 접속 로그(login_log)는 1년간 보관한다.
 * - 일반 행위 로그(transaction_log)는 1년간 보관한다.
 * - 계정/권한 생성·변경·삭제 로그는 3년간 보관한다.
 * - 북미 동부시간(America/New_York)의 오늘 날짜를 기준으로
 *   각 보관기간이 지난 데이터를 삭제한다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class LogCleanupBatchService {

    /** 로그 보관기간 계산 기준 시간대 — 북미 동부시간 */
    private static final ZoneId ZONE_ID = ZoneId.of("America/New_York");

    /** 일반 접속/행위 로그 보관기간 — 1년 */
    private static final long GENERAL_RETENTION_YEARS = 1L;

    /** 계정/권한 생성·변경·삭제 로그 보관기간 — 3년 */
    private static final long ACCOUNT_RETENTION_YEARS = 3L;

    /** 계정/권한 관리 API URL */
    private static final String ADMIN_URL = "/api/v1/admins";
    private static final String ADMIN_URL_PATTERN = "/api/v1/admins/%";

    private final TransactionLogRepository transactionLogRepository;
    private final LoginLogRepository loginLogRepository;

    /**
     * 보관기간이 지난 로그인 로그 및 트랜잭션 로그를 삭제한다.
     *
     * - 로그인 로그 및 일반 행위 로그는 1년간 보관한다.
     * - 계정/권한 생성·변경·삭제 로그는 3년간 보관한다.
     * - 북미 동부시간 기준으로 각 보관기간이 지난 데이터를 삭제한다.
     */
    @Transactional
    public void cleanup() {

        LocalDate today = LocalDate.now(ZONE_ID);

        // 일반 접속/행위 로그의 삭제 기준일시를 계산한다.
        OffsetDateTime oneYearCutoff = today
                .minusYears(GENERAL_RETENTION_YEARS)
                .atStartOfDay(ZONE_ID)
                .toOffsetDateTime();

        // 계정/권한 생성·변경·삭제 로그의 삭제 기준일시를 계산한다.
        OffsetDateTime threeYearCutoff = today
                .minusYears(ACCOUNT_RETENTION_YEARS)
                .atStartOfDay(ZONE_ID)
                .toOffsetDateTime();



        /*
         * 일반 트랜잭션 로그 삭제
         *
         * - 1년이 지난 트랜잭션 로그를 삭제한다.
         * - 단, 계정/권한 생성·변경·삭제 로그는 3년 보관 대상이므로 제외한다.
         *
         * 계정/권한 로그 판단 기준
         * - requestUrl : /api/v1/admins 또는 /api/v1/admins/**
         * - actionType : CREATE, UPDATE, DELETE
         */
        long transactionCount = transactionLogRepository.delete(
                (root, query, cb) -> {

                    // 계정/권한 관리 API 요청 여부
                    var adminUrl = cb.or(
                            cb.equal(root.get("requestUrl"), ADMIN_URL),
                            cb.like(root.get("requestUrl"), ADMIN_URL_PATTERN)
                    );

                    // 생성/수정/삭제 행위 여부
                    var adminAction = root.get("actionType")
                            .in("CREATE", "UPDATE", "DELETE");

                    // 3년 보관 대상인 계정/권한 생성·변경·삭제 로그
                    var accountPermissionLog = cb.and(
                            adminUrl,
                            adminAction
                    );

                    // 1년 이전 데이터 중 계정/권한 로그가 아닌 일반 행위 로그만 삭제한다.
                    return cb.and(
                            cb.lessThan(root.get("createdAt"), oneYearCutoff),
                            cb.not(accountPermissionLog)
                    );
                }
        );

        /*
         * 계정/권한 트랜잭션 로그 삭제
         *
         * - /api/v1/admins 계열의 생성·변경·삭제 로그는 3년간 보관한다.
         * - 3년이 지난 계정/권한 로그만 삭제한다.
         */
        long accountPermissionCount = transactionLogRepository.delete(
                (root, query, cb) -> {

                    // 계정/권한 관리 API 요청 여부
                    var adminUrl = cb.or(
                            cb.equal(root.get("requestUrl"), ADMIN_URL),
                            cb.like(root.get("requestUrl"), ADMIN_URL_PATTERN)
                    );

                    // 생성/수정/삭제 행위 여부
                    var adminAction = root.get("actionType")
                            .in("CREATE", "UPDATE", "DELETE");

                    // 3년 이전의 계정/권한 생성·변경·삭제 로그만 삭제한다.
                    return cb.and(
                            cb.lessThan(root.get("createdAt"), threeYearCutoff),
                            adminUrl,
                            adminAction
                    );
                }
        );

        /*
         * 로그인 로그 삭제
         *
         * - 접속 로그는 1년간 보관한다.
         * - 1년이 지난 로그인 로그를 삭제한다.
         */
        long loginCount = loginLogRepository.delete(
                (root, query, cb) ->
                        cb.lessThan(root.get("createdAt"), oneYearCutoff)
        );

    }
}


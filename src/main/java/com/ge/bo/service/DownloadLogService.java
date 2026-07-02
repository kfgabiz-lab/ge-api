package com.ge.bo.service;

import com.ge.bo.entity.DownloadLog;
import com.ge.bo.repository.DownloadLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 다운로드 이력 서비스
 * - @Async: 메인 export 응답과 분리하여 비동기로 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadLogService {

    private final DownloadLogRepository downloadLogRepository;

    /**
     * 다운로드 이력 비동기 저장
     *
     * 사용법:
     *   downloadLogService.saveAsync(slug, reason, format, createdBy, ipAddress);
     *
     * @param templateSlug 다운로드한 페이지 slug
     * @param reason       개인정보 다운로드 사유 (10~50자)
     * @param format       파일 형식 (xlsx / csv)
     * @param createdBy    다운로드 사용자 이메일
     * @param ipAddress    요청 IP
     */
    @Async
    public void saveAsync(String templateSlug, String reason, String format,
                          String createdBy, String ipAddress) {
        try {
            downloadLogRepository.save(
                DownloadLog.builder()
                    .templateSlug(templateSlug)
                    .reason(reason)
                    .format(format)
                    .createdBy(createdBy)
                    .ipAddress(ipAddress)
                    .build()
            );
        } catch (Exception e) {
            // 로그 저장 실패가 다운로드에 영향을 주지 않도록 예외를 삼킴
            log.warn("다운로드 이력 저장 실패: {}", e.getMessage());
        }
    }
}

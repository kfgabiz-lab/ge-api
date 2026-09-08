package com.ge.bo.batch.serverresource;

import com.ge.bo.repository.ServerResourceMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerResourceMetricRetentionService {

  private static final long RETENTION_DAYS = 7L;

  private final ServerResourceMetricRepository serverResourceMetricRepository;

  public void cleanupOldMetrics() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
    int deletedCount = serverResourceMetricRepository.deleteByCapturedAtBefore(cutoff);
    log.info("서버 리소스 모니터링 - 보관기간 경과 데이터 삭제 완료: deletedCount={}", deletedCount);
  }
}

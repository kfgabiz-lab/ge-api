package com.ge.bo.batch.serverresource;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServerResourceMetricScheduler {

  private final ServerResourceMetricCollectService serverResourceMetricCollectService;
  private final ServerResourceMetricRetentionService serverResourceMetricRetentionService;

  @Scheduled(fixedRate = 300000L)
  public void collectMetrics() {
    serverResourceMetricCollectService.collectAndSave();
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "America/New_York")
  public void cleanupOldMetrics() {
    serverResourceMetricRetentionService.cleanupOldMetrics();
  }
}

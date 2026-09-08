package com.ge.bo.service;

import com.ge.bo.dto.ServerResourceDailyTrendResponse;
import com.ge.bo.dto.ServerResourceMetricDetailResponse;
import com.ge.bo.dto.ServerResourceMonitorResponse;
import com.ge.bo.dto.ServerResourceProcessSummaryResponse;
import com.ge.bo.entity.ServerResourceMetric;
import com.ge.bo.repository.ServerResourceMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerResourceMetricService {

  private static final long WEEKLY_TREND_DAYS = 6L;

  private final ServerResourceMetricRepository serverResourceMetricRepository;

  public ServerResourceMonitorResponse getMonitor(LocalDate date, String ip) {
    ZoneId zone = ZoneId.systemDefault();
    OffsetDateTime rangeStart = date.minusDays(WEEKLY_TREND_DAYS).atStartOfDay(zone).toOffsetDateTime();
    OffsetDateTime rangeEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

    List<ServerResourceMetric> metrics = findMetrics(ip, rangeStart, rangeEnd);

    List<ServerResourceProcessSummaryResponse> dailySummary = buildDailySummary(metrics, date, zone);
    List<ServerResourceDailyTrendResponse> weeklyTrend = buildWeeklyTrend(metrics, zone);
    return new ServerResourceMonitorResponse(date, dailySummary, weeklyTrend);
  }

  public List<ServerResourceMetricDetailResponse> getDetail(LocalDate date, String ip) {
    ZoneId zone = ZoneId.systemDefault();
    OffsetDateTime rangeStart = date.atStartOfDay(zone).toOffsetDateTime();
    OffsetDateTime rangeEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

    return findMetrics(ip, rangeStart, rangeEnd)
        .stream()
        .map(metric -> new ServerResourceMetricDetailResponse(
            metric.getProcessName(),
            metric.getCpuPercent(),
            metric.getMemoryMb(),
            metric.getCapturedAt(),
            metric.getServerIp()))
        .toList();
  }

  public List<String> getServerIps() {
    return serverResourceMetricRepository.findDistinctServerIps();
  }

  private List<ServerResourceMetric> findMetrics(String ip, OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
    if (ip != null && !ip.isBlank()) {
      return serverResourceMetricRepository
          .findByServerIpAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
              ip, rangeStart, rangeEnd);
    }
    return serverResourceMetricRepository
        .findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(rangeStart, rangeEnd);
  }

  private List<ServerResourceProcessSummaryResponse> buildDailySummary(List<ServerResourceMetric> metrics,
      LocalDate date, ZoneId zone) {
    Map<String, List<ServerResourceMetric>> grouped = metrics.stream()
        .filter(metric -> metric.getCapturedAt().atZoneSameInstant(zone).toLocalDate().equals(date))
        .collect(Collectors.groupingBy(ServerResourceMetric::getProcessName, LinkedHashMap::new, Collectors.toList()));

    return grouped.entrySet().stream()
        .map(entry -> new ServerResourceProcessSummaryResponse(
            entry.getKey(),
            average(entry.getValue(), ServerResourceMetric::getCpuPercent),
            max(entry.getValue(), ServerResourceMetric::getCpuPercent),
            average(entry.getValue(), ServerResourceMetric::getMemoryMb),
            max(entry.getValue(), ServerResourceMetric::getMemoryMb)))
        .sorted(Comparator.comparing(ServerResourceProcessSummaryResponse::processName))
        .toList();
  }

  private List<ServerResourceDailyTrendResponse> buildWeeklyTrend(List<ServerResourceMetric> metrics, ZoneId zone) {
    Map<TrendKey, List<ServerResourceMetric>> grouped = metrics.stream()
        .collect(Collectors.groupingBy(
            metric -> new TrendKey(metric.getCapturedAt().atZoneSameInstant(zone).toLocalDate(),
                metric.getProcessName()),
            LinkedHashMap::new, Collectors.toList()));

    return grouped.entrySet().stream()
        .map(entry -> new ServerResourceDailyTrendResponse(
            entry.getKey().date(),
            entry.getKey().processName(),
            average(entry.getValue(), ServerResourceMetric::getCpuPercent),
            average(entry.getValue(), ServerResourceMetric::getMemoryMb)))
        .sorted(Comparator.comparing(ServerResourceDailyTrendResponse::date)
            .thenComparing(ServerResourceDailyTrendResponse::processName))
        .toList();
  }

  private BigDecimal average(List<ServerResourceMetric> rows, Function<ServerResourceMetric, BigDecimal> extractor) {
    BigDecimal sum = rows.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
  }

  private BigDecimal max(List<ServerResourceMetric> rows, Function<ServerResourceMetric, BigDecimal> extractor) {
    return rows.stream().map(extractor).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
  }

  private record TrendKey(LocalDate date, String processName) {
  }
}

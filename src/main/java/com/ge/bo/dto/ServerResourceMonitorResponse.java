package com.ge.bo.dto;

import java.time.LocalDate;
import java.util.List;

public record ServerResourceMonitorResponse(
    LocalDate date,
    List<ServerResourceProcessSummaryResponse> dailySummary,
    List<ServerResourceDailyTrendResponse> weeklyTrend) {
}

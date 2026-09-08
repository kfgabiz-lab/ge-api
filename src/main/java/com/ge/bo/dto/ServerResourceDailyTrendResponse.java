package com.ge.bo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ServerResourceDailyTrendResponse(
    LocalDate date,
    String processName,
    BigDecimal avgCpuPercent,
    BigDecimal avgMemoryMb) {
}

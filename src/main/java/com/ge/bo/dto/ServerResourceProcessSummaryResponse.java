package com.ge.bo.dto;

import java.math.BigDecimal;

public record ServerResourceProcessSummaryResponse(
    String processName,
    BigDecimal avgCpuPercent,
    BigDecimal maxCpuPercent,
    BigDecimal avgMemoryMb,
    BigDecimal maxMemoryMb) {
}

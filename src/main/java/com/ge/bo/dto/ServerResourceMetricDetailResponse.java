package com.ge.bo.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ServerResourceMetricDetailResponse(
    String processName,
    BigDecimal cpuPercent,
    BigDecimal memoryMb,
    OffsetDateTime capturedAt,
    String serverIp) {
}

package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "server_resource_metric",
        indexes = {
                @Index(name = "server_resource_metric_process_captured_idx",
                        columnList = "process_name, captured_at")
        })
public class ServerResourceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_name", nullable = false, length = 20)
    private String processName;

    @Column(name = "cpu_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal cpuPercent;

    @Column(name = "memory_mb", nullable = false, precision = 10, scale = 2)
    private BigDecimal memoryMb;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "server_ip", nullable = false, length = 45)
    private String serverIp;

    @Builder
    public ServerResourceMetric(String processName, BigDecimal cpuPercent,
                                BigDecimal memoryMb, OffsetDateTime capturedAt, String serverIp) {
        this.processName = processName;
        this.cpuPercent = cpuPercent;
        this.memoryMb = memoryMb;
        this.capturedAt = capturedAt != null ? capturedAt : OffsetDateTime.now();
        this.serverIp = serverIp;
    }
}

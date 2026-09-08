package com.ge.bo.controller;

import com.ge.bo.dto.ServerResourceMetricDetailResponse;
import com.ge.bo.dto.ServerResourceMonitorResponse;
import com.ge.bo.service.ServerResourceMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/server-resource-metrics")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
public class ServerResourceMetricController {

  private final ServerResourceMetricService serverResourceMetricService;

  @GetMapping
  public ResponseEntity<ServerResourceMonitorResponse> getMonitor(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) String ip) {
    return ResponseEntity.ok(serverResourceMetricService.getMonitor(date, ip));
  }

  @GetMapping("/detail")
  public ResponseEntity<List<ServerResourceMetricDetailResponse>> getDetail(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) String ip) {
    return ResponseEntity.ok(serverResourceMetricService.getDetail(date, ip));
  }

  @GetMapping("/ips")
  public ResponseEntity<List<String>> getServerIps() {
    return ResponseEntity.ok(serverResourceMetricService.getServerIps());
  }
}

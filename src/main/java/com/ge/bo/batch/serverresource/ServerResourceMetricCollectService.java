package com.ge.bo.batch.serverresource;

import com.ge.bo.entity.ServerResourceMetric;
import com.ge.bo.repository.ServerResourceMetricRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerResourceMetricCollectService {

  private static final String SELF_PROCESS_NAME = "bo-api";
  private static final long CPU_SAMPLE_INTERVAL_MS = 600L;
  private static final long BYTES_PER_MB = 1024L * 1024L;

  private final ServerResourceMetricRepository serverResourceMetricRepository;
  private final ServerResourceMonitorProperties serverResourceMonitorProperties;
  private final ServerResourceProcessFinder serverResourceProcessFinder;

  private String serverIp;

  @PostConstruct
  void resolveServerIp() {
    this.serverIp = resolveLocalServerIp();
    log.info("서버 리소스 모니터링 - 수집 서버 IP 확정: serverIp={}", serverIp);
  }

  private String resolveLocalServerIp() {
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
          continue;
        }
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
            return address.getHostAddress();
          }
        }
      }
    } catch (SocketException e) {
      log.warn("서버 리소스 모니터링 - 네트워크 인터페이스 조회 실패", e);
    }

    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      log.warn("서버 리소스 모니터링 - 서버 IP 조회 실패, loopback 주소로 대체", e);
      return "127.0.0.1";
    }
  }

  public void collectAndSave() {
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
    OffsetDateTime capturedAt = OffsetDateTime.now();
    List<ServerResourceMetric> metrics = new ArrayList<>();

    OSProcess selfProcess = operatingSystem.getProcess((int) ProcessHandle.current().pid());
    addMetricIfPresent(metrics, SELF_PROCESS_NAME, selfProcess, operatingSystem, capturedAt);

    List<OSProcess> allProcesses = operatingSystem.getProcesses();
    for (Map.Entry<String, String> entry : serverResourceMonitorProperties.getProcessPathPatterns().entrySet()) {
      OSProcess matched = serverResourceProcessFinder.findByCommandLinePattern(allProcesses, entry.getValue());
      addMetricIfPresent(metrics, entry.getKey(), matched, operatingSystem, capturedAt);
    }

    if (!metrics.isEmpty()) {
      serverResourceMetricRepository.saveAll(metrics);
    }
  }

  private void addMetricIfPresent(List<ServerResourceMetric> metrics, String processName, OSProcess process,
      OperatingSystem operatingSystem, OffsetDateTime capturedAt) {
    if (process == null) {
      log.warn("서버 리소스 모니터링 - 대상 프로세스를 찾지 못함: processName={}", processName);
      return;
    }

    BigDecimal cpuPercent = measureCpuPercent(process, operatingSystem);
    BigDecimal memoryMb = BigDecimal.valueOf(process.getResidentSetSize())
        .divide(BigDecimal.valueOf(BYTES_PER_MB), 2, RoundingMode.HALF_UP);

    metrics.add(ServerResourceMetric.builder()
        .processName(processName)
        .cpuPercent(cpuPercent)
        .memoryMb(memoryMb)
        .capturedAt(capturedAt)
        .serverIp(serverIp)
        .build());
  }

  private BigDecimal measureCpuPercent(OSProcess priorSnapshot, OperatingSystem operatingSystem) {
    try {
      Thread.sleep(CPU_SAMPLE_INTERVAL_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return BigDecimal.ZERO;
    }

    OSProcess refreshed = operatingSystem.getProcess(priorSnapshot.getProcessID());
    if (refreshed == null) {
      return BigDecimal.ZERO;
    }

    double load = refreshed.getProcessCpuLoadBetweenTicks(priorSnapshot) * 100;
    return BigDecimal.valueOf(load).setScale(2, RoundingMode.HALF_UP);
  }
}

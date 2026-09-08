package com.ge.bo.repository;

import com.ge.bo.entity.ServerResourceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

public interface ServerResourceMetricRepository extends JpaRepository<ServerResourceMetric, Long> {

  List<ServerResourceMetric> findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
      OffsetDateTime start, OffsetDateTime end);

  List<ServerResourceMetric> findByServerIpAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
      String serverIp, OffsetDateTime start, OffsetDateTime end);

  @Query("SELECT DISTINCT m.serverIp FROM ServerResourceMetric m ORDER BY m.serverIp ASC")
  List<String> findDistinctServerIps();

  @Modifying
  @Transactional
  @Query("DELETE FROM ServerResourceMetric m WHERE m.capturedAt < :cutoff")
  int deleteByCapturedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}

package com.ge.bo.common.context;

import com.ge.bo.entity.Site;
import com.ge.bo.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사이트별 시간대(timezone) 조회 공통 헬퍼
 * - PageDataService, ContactUsService 등 여러 곳에서 "현재 사이트 기준 시각/날짜"를 구할 때 공통으로 사용
 * - siteId → Site.timezone 조회는 매 요청 DB 조회를 피하기 위해 간단한 인메모리 캐시(ConcurrentHashMap)를 둔다
 *   (TTL/무효화 없는 단순 캐시 — 사이트 timezone은 자주 바뀌지 않는 값이라 과설계를 피함.
 *    운영 중 값 변경 시 앱 재기동 전까지는 이전 값이 유지될 수 있음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteTimeZoneResolver {

  private final SiteRepository siteRepository;

  private final Map<Long, Optional<ZoneId>> zoneCache = new ConcurrentHashMap<>();

    /**
     * siteId로 zone을 조회 — 사이트/timezone이 없거나 파싱 실패 시 서버 기본 zone(zone 미지정과 동일 동작)으로 폴백
     * siteId가 명시적으로 있는 서비스 로직(ContactUsInquiryService 등)에서 사용
     */
  public ZoneId resolve(Long siteId) {
    return lookup(siteId).orElseGet(ZoneId::systemDefault);
  }

    /**
     * SiteContextFilter가 채워둔 SiteContext 기준으로 zone을 조회 — 값이 없으면 서버 기본 zone
     * 컨트롤러가 siteId를 별도로 받지 않는 공통 로직(JpaConfig 감사 컬럼 등)에서 사용
     */
  public ZoneId resolveFromContext() {
    return SiteContext.get().orElseGet(ZoneId::systemDefault);
  }

    /**
     * siteId로 zone을 조회 — 조회/파싱 실패 시 빈 Optional (폴백 여부는 호출부가 결정)
     * SiteContextFilter처럼 "없으면 아무것도 하지 않음"이 필요한 곳에서 사용
     */
  public Optional<ZoneId> lookup(Long siteId) {
    if (siteId == null) {
      return Optional.empty();
    }
    return zoneCache.computeIfAbsent(siteId, this::loadZone);
  }

  private Optional<ZoneId> loadZone(Long siteId) {
    return siteRepository.findById(siteId)
                .map(Site::getTimezone)
                .filter(StringUtils::hasText)
                .flatMap(this::safeParse);
  }

  private Optional<ZoneId> safeParse(String timezone) {
    try {
      return Optional.of(ZoneId.of(timezone));
    } catch (DateTimeException e) {
      log.warn("사이트 timezone 파싱 실패 - value={}", timezone, e);
      return Optional.empty();
    }
  }

    /**
     * 캐시 무효화 — 사이트 timezone이 변경/삭제될 때 반드시 호출해야 한다.
     * computeIfAbsent 특성상 한 번 캐시되면(Optional.empty()라도) 재조회가 일어나지 않으므로,
     * 값을 갱신한 쪽(SiteService)이 책임지고 이 메서드로 캐시를 비워야 다음 조회 시 최신값을 반영한다.
     */
  public void evict(Long siteId) {
    zoneCache.remove(siteId);
  }
}

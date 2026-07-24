package com.ge.bo.common.context;

import java.time.ZoneId;
import java.util.Optional;

/**
 * 요청 스코프의 "현재 사이트 시간대"를 스레드 로컬로 보관하는 컨텍스트
 * - SiteContextFilter가 요청 시작 시 X-Site-Id 헤더로 조회한 zone을 set()하고, 요청 종료 시 반드시 clear() 한다
 * - siteId가 없거나 timezone이 설정되지 않은 사이트면 아무 값도 set 하지 않는다(빈 상태 유지)
 *   → 소비하는 쪽(SiteTimeZoneResolver 등)이 "값 없음"을 서버 기본 zone으로 처리(fallback)
 */
public final class SiteContext {

  private static final ThreadLocal<ZoneId> CURRENT_ZONE = new ThreadLocal<>();

  private SiteContext() {
  }

  public static void set(ZoneId zone) {
    CURRENT_ZONE.set(zone);
  }

  public static Optional<ZoneId> get() {
    return Optional.ofNullable(CURRENT_ZONE.get());
  }

  /** 스레드풀 재사용 시 다른 요청으로 값이 새는 것을 막기 위한 필수 정리 — 반드시 요청 종료 시(finally) 호출 */
  public static void clear() {
    CURRENT_ZONE.remove();
  }
}

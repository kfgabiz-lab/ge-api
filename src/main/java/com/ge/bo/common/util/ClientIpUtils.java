package com.ge.bo.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출 공통 유틸
 * bo(Next.js) 프록시(middleware.ts)가 Azure App Service가 실어준 X-Forwarded-For의
 * 마지막(검증된) 값만 남기고 덮어써서 넘겨주므로, 이 헤더를 신뢰해서 사용한다.
 * 값이 없으면(로컬 직접 호출 등) getRemoteAddr()로 폴백한다.
 */
public final class ClientIpUtils {

  private ClientIpUtils() {
  }

  public static String resolve(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.trim();
    }
    return request.getRemoteAddr();
  }
}

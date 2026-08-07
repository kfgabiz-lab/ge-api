package com.ge.bo.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출 공통 유틸
 * X-Forwarded-For는 의도적으로 사용하지 않는다 — 같은 호스트의 bo(Next.js) 프록시가 헤더를 검증 없이 그대로 전달하므로 신뢰할 수 없다.
 */
public final class ClientIpUtils {

  private ClientIpUtils() {
  }

  public static String resolve(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}

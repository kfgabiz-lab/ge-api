package com.ge.bo.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 로그 메시지에서 비밀번호·인증 정보를 자동 마스킹하는 Logback 컨버터
 * logback-spring.xml의 conversionRule에 등록 후 패턴에서 %maskedMsg 로 사용
 *
 * 마스킹 대상: password, passwordHash, passwd, pwd, credentials, secret, token
 * 마스킹 형태: key=value → key=****
 * 실제 치환 규칙은 SensitiveDataMasker 가 보유 (애플리케이션 코드에서도 동일 규칙 재사용)
 */
public class MaskingConverter extends MessageConverter {

  @Override
  public String convert(ILoggingEvent event) {
    return SensitiveDataMasker.mask(super.convert(event));
  }
}

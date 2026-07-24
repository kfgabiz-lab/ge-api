package com.ge.bo.config;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class JpaConfig {

    /** OffsetDateTime 타입의 @CreatedDate / @LastModifiedDate 지원 — SiteContext에 사이트 timezone이 있으면 그 기준, 없으면 기존과 동일하게 서버 기본 zone */
  @Bean
    public DateTimeProvider offsetDateTimeProvider(SiteTimeZoneResolver siteTimeZoneResolver) {
    return () -> Optional.of(OffsetDateTime.now(siteTimeZoneResolver.resolveFromContext()));
  }

    /**
     * @CreatedBy, @LastModifiedBy에 현재 인증된 사용자 이메일 주입
     */
  @Bean
    public AuditorAware<String> auditorProvider() {
    return () -> {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
        return Optional.of("system");
      }
      return Optional.of(auth.getName());
    };
  }
}

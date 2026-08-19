package com.ge.bo.config;

import com.ge.bo.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ge.bo.security.JwtAuthenticationFilter;
import com.ge.bo.security.LoginRateLimitFilter;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Spring Security 설정 - JWT Stateless 방식, CORS 허용, RBAC 적용
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 어노테이션 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    private final LoginRateLimitFilter loginRateLimitFilter;

  @Value("${cors.allowed-origins}")
    private String allowedOrigins;

  @Value("${ls.redis-enabled:false}")
    private boolean redisEnabled;

  @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
      // local이면 jwt토큰 사용. 나머진 redis session 사용(false : local, true : dev, prd)
      if(redisEnabled){
          http
                  // CSRF 비활성화 (REST API Stateless 방식)
                  .csrf(AbstractHttpConfigurer::disable)
                  // CORS 설정 적용
                  .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                  // 세션 활성화
//                  .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                  .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId())
                          .maximumSessions(1)
                          .maxSessionsPreventsLogin(false)
                          .sessionRegistry(sessionRegistry)
                          .expiredSessionStrategy(event -> {
                              HttpServletResponse response = event.getResponse();

                              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                              response.setContentType("application/json;charset=UTF-8");
                              response.getWriter().write("""
                                      {
                                        "code": "SESSION_EXPIRED",
                                        "message": "다른 기기에서 로그인되어 세션이 종료되었습니다."
                                      }
                                      """);
                          })
                  )
                  // URL 접근 권한 설정
                  .authorizeHttpRequests(auth -> auth
                          .requestMatchers("/api/v1/auth/**").permitAll() // 로그인, TOTP 2FA 엔드포인트 — 인증 없이 허용
                          .requestMatchers("/api/v1/health").permitAll() // 헬스 체크 허용
                          .requestMatchers("/api/v1/public/**").permitAll() // 공개 API — 인증 없이 허용
                          .requestMatchers("/api/v1/redisTest/**").permitAll() // redis 체크 허용
                          .requestMatchers("/api/v1/fo/**").permitAll() // FO API — 비로그인 전체 허용
                          // 콘텐츠배치 EAI 트리거 — IF 적재 완료 후 EAI가 직접 호출
                          .requestMatchers("/api/v1/contents-batch/run-all").permitAll()
                          .requestMatchers("/api/v1/contents-batch/catalog/run").permitAll()
                          .requestMatchers("/api/v1/contents-batch/ssq/run").permitAll()
                          .requestMatchers("/api/v1/contents-batch/certi/run").permitAll()
                          // 다국어 리소스 — 비로그인 조회 허용
                          .requestMatchers(HttpMethod.GET, "/api/v1/message-resources").permitAll()
                          // 관리자 API — 인증 필요. 실제 인가는 AdminController 클래스 레벨
                          // @PreAuthorize("@securityService.isSystemAdmin(authentication)")가 담당한다.
                          // 판정 기준은 역할명(SUPER_ADMIN 등)이 아니라 role.is_system=true(현재 SYSTEM_ADMIN)다.
                          // 단, GET /admins/{id}/sites만 본인 조회(isSelf) 예외를 메서드 레벨로 허용.
                          .requestMatchers("/api/v1/admins/**").authenticated()
                          // 역할 API — 인증 필요. 실제 인가는 RoleController 클래스 레벨
                          // @PreAuthorize("@securityService.isSystemAdmin(authentication)")가 담당하며,
                          // 마찬가지로 역할명이 아닌 role.is_system=true 기준이다.
                          .requestMatchers("/api/v1/roles/**").authenticated()
                          .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                  )
                  // 미인증 요청 시 403 대신 401 반환 (FE 인터셉터의 토큰 갱신 로직이 동작하도록)
                  .exceptionHandling(ex -> ex
                          .authenticationEntryPoint((request, response, authException) -> {
                              response.setStatus(401);
                              response.setContentType("application/json;charset=UTF-8");
                              response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}");
                          })
                  )
                  // 로그인 IP 단위 Rate Limiting — 인증 처리 이전 단계에서 즉시 차단
                  .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
      }else{
          http
                  // CSRF 비활성화 (REST API Stateless 방식)
                  .csrf(AbstractHttpConfigurer::disable)
                  // CORS 설정 적용
                  .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                  // 세션 비활성화 (JWT 사용)
                  .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                  // URL 접근 권한 설정
                  .authorizeHttpRequests(auth -> auth
                          .requestMatchers("/api/v1/auth/**").permitAll() // 로그인, TOTP 2FA 엔드포인트 — 인증 없이 허용
                          .requestMatchers("/api/v1/health").permitAll() // 헬스 체크 허용
                          .requestMatchers("/api/v1/redisTest/**").permitAll() // redis 체크 허용
                          .requestMatchers("/api/v1/public/**").permitAll() // 공개 API — 인증 없이 허용
                          .requestMatchers("/api/v1/fo/**").permitAll() // FO API — 비로그인 전체 허용
                          // 콘텐츠배치 EAI 트리거 — IF 적재 완료 후 EAI가 직접 호출
                          .requestMatchers("/api/v1/contents-batch/run-all").permitAll()
                          .requestMatchers("/api/v1/contents-batch/catalog/run").permitAll()
                          .requestMatchers("/api/v1/contents-batch/ssq/run").permitAll()
                          .requestMatchers("/api/v1/contents-batch/certi/run").permitAll()
                          // 다국어 리소스 — 비로그인 조회 허용
                          .requestMatchers(HttpMethod.GET, "/api/v1/message-resources").permitAll()
                          // 관리자 API — 인증 필요. 실제 인가는 AdminController 클래스 레벨
                          // @PreAuthorize("@securityService.isSystemAdmin(authentication)")가 담당한다.
                          // 판정 기준은 역할명(SUPER_ADMIN 등)이 아니라 role.is_system=true(현재 SYSTEM_ADMIN)다.
                          // 단, GET /admins/{id}/sites만 본인 조회(isSelf) 예외를 메서드 레벨로 허용.
                          .requestMatchers("/api/v1/admins/**").authenticated()
                          // 역할 API — 인증 필요. 실제 인가는 RoleController 클래스 레벨
                          // @PreAuthorize("@securityService.isSystemAdmin(authentication)")가 담당하며,
                          // 마찬가지로 역할명이 아닌 role.is_system=true 기준이다.
                          .requestMatchers("/api/v1/roles/**").authenticated()
                          .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                  )
                  // 미인증 요청 시 403 대신 401 반환 (FE 인터셉터의 토큰 갱신 로직이 동작하도록)
                  .exceptionHandling(ex -> ex
                          .authenticationEntryPoint((request, response, authException) -> {
                              response.setStatus(401);
                              response.setContentType("application/json;charset=UTF-8");
                              response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}");
                          })
                  )
                  // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                  .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                  // 로그인 IP 단위 Rate Limiting — JWT 필터보다 앞에서 즉시 차단
                  .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class);
      }

    return http.build();
  }

  // @Component Filter 빈이 서블릿 컨테이너에 자동 등록되어 SecurityFilterChain 밖에서 한 번 더 실행되는 것을 막는다.
  // (실제 적용은 위 filterChain()의 addFilterBefore 등록분만 사용)
  @Bean
  public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(
      LoginRateLimitFilter filter) {
    FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
    public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12); // BCrypt rounds=12
  }

  @Bean
    public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  @ConditionalOnProperty(name = "ls.redis-enabled", havingValue = "true")
  SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository){
      return new SpringSessionBackedSessionRegistry<>(sessionRepository);
  }

  // ls.redis-enabled=false(local/developer, JWT 방식)일 때 대체 — filterChain의 redis 분기에서는 사용되지 않지만
  // Spring이 @Bean 메서드를 항상 인스턴스화하려 하므로, Redis 없이도 SessionRegistry 타입 빈 자체는 존재해야 부팅됨.
  @Bean
  @ConditionalOnProperty(name = "ls.redis-enabled", havingValue = "false", matchIfMissing = true)
  SessionRegistry sessionRegistryInMemory(){
      return new SessionRegistryImpl();
  }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            SessionRegistry sessionRegistry
    ) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);

        concurrent.setMaximumSessions(1);

        // false: 새 로그인 허용, 기존 로그인 세션 만료
        concurrent.setExceptionIfMaximumExceeded(false);

        SessionFixationProtectionStrategy fixation =
                new SessionFixationProtectionStrategy();

        RegisterSessionAuthenticationStrategy register =
                new RegisterSessionAuthenticationStrategy(sessionRegistry);

        return new CompositeSessionAuthenticationStrategy(
                List.of(concurrent, fixation, register)
        );
    }

    @Bean
    @ConditionalOnProperty(name = "ls.redis-enabled", havingValue = "true")
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}

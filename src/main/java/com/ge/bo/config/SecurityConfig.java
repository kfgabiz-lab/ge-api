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
import org.springframework.security.core.session.SessionRegistry;
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
                                      """);
                          })
                  )
                  .exceptionHandling(exception -> exception
                          .authenticationEntryPoint((request, response, authException) -> {
                              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                              response.setContentType("application/json;charset=UTF-8");
                              response.getWriter().write("""
                                        {
                                          "code": "UNAUTHORIZED",
                                          "message": "로그인이 필요합니다."
                                        }
                                        """);
                          })
                  )
                  // URL 접근 권한 설정
                  .authorizeHttpRequests(auth -> auth
                          .requestMatchers("/api/v1/auth/**").permitAll() // 로그인, TOTP 2FA 엔드포인트 — 인증 없이 허용
                          .requestMatchers("/api/v1/health").permitAll() // 헬스 체크 허용
                          .requestMatchers("/api/v1/redisTest/**").permitAll() // redis 체크 허용
                          .requestMatchers("/api/v1/cryptoTest/**").permitAll() // 암복호화 테스트 허용
                          .requestMatchers("/api/v1/public/**").permitAll() // 공개 API — 인증 없이 허용
                          .requestMatchers("/api/v1/fo/**").permitAll() // FO API — 비로그인 전체 허용
                          // 다국어 리소스 — 비로그인 조회 허용
                          .requestMatchers(HttpMethod.GET, "/api/v1/message-resources").permitAll()
                          // 관리자 API — 인증 필요 (@PreAuthorize로 SUPER_ADMIN 제한)
                          .requestMatchers("/api/v1/admins/**").authenticated()
                          // 역할 API — 인증 필요 (@PreAuthorize로 SUPER_ADMIN 제한)
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
                  );
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
                          .requestMatchers("/api/v1/cryptoTest/**").permitAll() // 암복호화 테스트 허용
                          .requestMatchers("/api/v1/public/**").permitAll() // 공개 API — 인증 없이 허용
                          .requestMatchers("/api/v1/fo/**").permitAll() // FO API — 비로그인 전체 허용
                          // 다국어 리소스 — 비로그인 조회 허용
                          .requestMatchers(HttpMethod.GET, "/api/v1/message-resources").permitAll()
                          // 관리자 API — 인증 필요 (@PreAuthorize로 SUPER_ADMIN 제한)
                          .requestMatchers("/api/v1/admins/**").authenticated()
                          // 역할 API — 인증 필요 (@PreAuthorize로 SUPER_ADMIN 제한)
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
                  .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
      }

    return http.build();
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
  SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository){
      return new SpringSessionBackedSessionRegistry<>(sessionRepository);
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
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}

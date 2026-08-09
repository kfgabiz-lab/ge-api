package com.ge.bo.config;

import com.ge.bo.security.AccessValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 인터셉터 등록 전용 설정
 * ⚠️ @EnableWebMvc를 붙이면 Boot의 MVC 자동설정이 꺼져 JacksonConfig 기반 직렬화 등이 깨지므로 절대 추가하지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final AccessValidationInterceptor accessValidationInterceptor;

  @Override
    public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(accessValidationInterceptor);
  }
}

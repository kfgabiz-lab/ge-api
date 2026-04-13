package com.ge.bo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리 설정
 * - @Async 어노테이션 활성화
 * - 오류로그 저장 등 메인 흐름과 분리할 작업에 사용
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

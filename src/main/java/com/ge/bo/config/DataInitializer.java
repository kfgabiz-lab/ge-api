package com.ge.bo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ge.bo.repository.AdminRepository;

@Component
public class DataInitializer implements ApplicationRunner {

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;

  public DataInitializer(AdminRepository adminRepository,
            @Lazy PasswordEncoder passwordEncoder) {
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
    public void run(ApplicationArguments args) {
        // 초기 관리자 시드가 필요하면 employeeId 기준으로 직접 추가할 것 (email은 더 이상 조회 키가 아님)
  }
}

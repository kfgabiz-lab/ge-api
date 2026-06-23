package com.ge.bo.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ge.bo.dto.LoginRequest;
import com.ge.bo.dto.LoginResponse;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.RoleRepository;
import com.ge.bo.security.JwtTokenProvider;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCK_DURATION_MINUTES = 30;
  private static final long REFRESH_TOKEN_DAYS = 7L;

  /** application.yml ls.totp.enabled — false 시 2차인증 스킵하고 바로 JWT 발급 */
  @Value("${ls.totp.enabled:true}")
  private boolean totpEnabled;

  private final AdminRepository adminRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RecaptchaService recaptchaService;

  /**
   * 1단계 로그인 (reCAPTCHA + 비밀번호 검증)
   * totp.enabled=true  → tempToken 발급 후 2FA 단계 진행
   * totp.enabled=false → 바로 accessToken 발급 (2FA 스킵)
   *
   * @param request 로그인 요청 DTO (이메일, 비밀번호, reCAPTCHA 토큰)
   * @return LoginResponse (2FA 활성 시 tempToken, 비활성 시 accessToken)
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    // reCAPTCHA 토큰 검증
    recaptchaService.verify(request.getRecaptchaToken());

    AdminUser admin = adminRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> BusinessException.unauthorized("이메일 또는 비밀번호가 일치하지 않습니다."));

    // 임시 잠금 확인
    if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(OffsetDateTime.now())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_TEMPORARILY_LOCKED",
          "로그인 시도 횟수를 초과했습니다. " + LOCK_DURATION_MINUTES + "분 후 다시 시도해주세요.");
    }

    // 계정 비활성화 확인
    if (!admin.isActive()) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
          "잠긴 계정입니다. 관리자에게 문의하세요.");
    }

    // 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
      int attempts = admin.getFailedLoginAttempts() + 1;
      admin.setFailedLoginAttempts(attempts);
      if (attempts >= MAX_FAILED_ATTEMPTS) {
        admin.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_TEMPORARILY_LOCKED",
            "로그인 시도 횟수(" + MAX_FAILED_ATTEMPTS + "회)를 초과했습니다. "
                + LOCK_DURATION_MINUTES + "분 후 다시 시도해주세요.");
      }
      throw BusinessException.unauthorized("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    // 비밀번호 검증 성공 — 실패 카운터 초기화
    admin.setFailedLoginAttempts(0);
    admin.setLockedUntil(null);

    // 2차인증 비활성화 시 바로 accessToken 발급
    if (!totpEnabled) {
      boolean isSystem = roleRepository.findByCode(admin.getRole())
          .map(role -> role.isSystem())
          .orElse(false);
      String accessToken = jwtTokenProvider.generateAccessToken(admin.getEmail(), admin.getRole());
      return LoginResponse.builder()
          .accessToken(accessToken)
          .expiresIn(3600L)
          .adminInfo(LoginResponse.AdminInfo.builder()
              .id(admin.getId())
              .name(admin.getName())
              .email(admin.getEmail())
              .role(admin.getRole())
              .isSystem(isSystem)
              .build())
          .build();
    }

    // 2FA 미완료 상태 임시 토큰 발급 (10분 유효)
    String tempToken = jwtTokenProvider.generateTotpPendingToken(admin.getEmail());

    if (!admin.isTotpEnabled()) {
      // 2FA 미등록 → QR 등록 화면으로
      return LoginResponse.builder()
          .tempToken(tempToken)
          .requireTotpSetup(true)
          .requireTotpVerify(false)
          .build();
    }

    // 2FA 등록 완료 → OTP 입력 화면으로
    return LoginResponse.builder()
        .tempToken(tempToken)
        .requireTotpSetup(false)
        .requireTotpVerify(true)
        .build();
  }

  /**
   * Refresh Token으로 액세스 토큰 재발급
   *
   * @param refreshToken httpOnly 쿠키에서 읽은 Refresh Token
   * @return 새 액세스 토큰 및 관리자 정보 응답 DTO
   */
  @Transactional(readOnly = true)
  public LoginResponse refresh(String refreshToken) {
    if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
      throw BusinessException.unauthorized("유효하지 않은 Refresh Token입니다.");
    }

    String email = jwtTokenProvider.getEmailFromToken(refreshToken);
    AdminUser admin = adminRepository.findByEmail(email)
        .orElseThrow(() -> BusinessException.unauthorized("사용자를 찾을 수 없습니다."));

    if (!admin.isActive()) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "잠긴 계정입니다. 관리자에게 문의하세요.");
    }

    boolean isSystem = roleRepository.findByCode(admin.getRole())
        .map(role -> role.isSystem())
        .orElse(false);

    String newAccessToken = jwtTokenProvider.generateAccessToken(admin.getEmail(), admin.getRole());
    return LoginResponse.builder()
        .accessToken(newAccessToken)
        .expiresIn(3600L)
        .adminInfo(LoginResponse.AdminInfo.builder()
            .id(admin.getId())
            .name(admin.getName())
            .email(admin.getEmail())
            .role(admin.getRole())
            .isSystem(isSystem)
            .build())
        .build();
  }

  /**
   * 로그아웃 처리 (Refresh Token 쿠키 만료)
   *
   * @param response HTTP 응답 객체 (쿠키 만료 설정용)
   */
  public void logout(HttpServletResponse response) {
    clearRefreshTokenCookie(response);
  }

  /**
   * Refresh Token을 httpOnly 쿠키로 설정
   * TotpService 2FA 완료 후 AuthController에서 호출
   *
   * @param response HTTP 응답 객체
   * @param email 관리자 이메일 (refreshToken 생성용)
   */
  public void issueRefreshTokenCookie(HttpServletResponse response, String email) {
    String refreshToken = jwtTokenProvider.generateRefreshToken(email);
    ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(false) // 운영 환경에서는 true로 변경
        .path("/api/v1/auth")
        .maxAge(Duration.ofDays(REFRESH_TOKEN_DAYS))
        .sameSite("Strict")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearRefreshTokenCookie(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
        .httpOnly(true)
        .secure(false)
        .path("/api/v1/auth")
        .maxAge(0)
        .sameSite("Strict")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}

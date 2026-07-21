package com.ge.bo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ge.bo.dto.LoginRequest;
import com.ge.bo.dto.LoginResponse;
import com.ge.bo.dto.TotpDto;
import com.ge.bo.service.AuthService;
import com.ge.bo.service.TotpService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final TotpService totpService;

  @Value("${ls.redis-enabled:false}")
  private boolean redisEnabled;

  /**
   * 1단계 로그인 (이메일/비밀번호/reCAPTCHA 검증)
   * totp.enabled=true  → tempToken 반환 (2FA 단계 진행)
   * totp.enabled=false → accessToken 직접 반환 + refreshToken 쿠키 발급
   */
  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request,
                             HttpServletResponse response,
                             HttpServletRequest httpRequest) {
    String clientIp = extractClientIp(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");

    LoginResponse result = authService.login(request, clientIp, userAgent, httpRequest);
    if (result.getAccessToken() != null && result.getAdminInfo() != null) {
      if (redisEnabled) {
        // session 및 auth 생성
        authService.createLoginSession(httpRequest, response, result.getAdminInfo());
      } else {
        // 2FA 비활성화 시 accessToken이 바로 발급되므로 refreshToken 쿠키도 함께 발급
        authService.issueRefreshTokenCookie(response, result.getAdminInfo().getEmail());
      }
    }

    return result;
  }

  /** X-Forwarded-For 우선으로 실제 클라이언트 IP 추출 */
  private String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  /**
   * TOTP QR 코드 발급 (최초 2FA 미등록 계정 전용)
   * POST /auth/totp/qr
   */
  @PostMapping("/totp/qr")
  public TotpDto.SetupResponse totpQr(@RequestBody TotpDto.SetupRequest request, HttpServletRequest req) {
    return totpService.setup(request, req);
  }

  /**
   * TOTP 등록 완료 (6자리 코드 검증 → JWT 발급)
   * POST /auth/totp/registrations
   */
  @PostMapping("/totp/registrations")
  public TotpDto.VerifyResponse totpRegistration(
      @RequestBody TotpDto.ConfirmRequest request, HttpServletResponse response, HttpServletRequest req) {
    TotpDto.VerifyResponse result = null;
    if(redisEnabled){
      result = totpService.confirm(request, req);
      authService.createLoginSession(req, response, result.getAdminInfo());
    }else{
      result = totpService.confirmWithJwt(request);
      authService.issueRefreshTokenCookie(response, result.getAdminInfo().getEmail());
    }

    return result;
  }

  /**
   * TOTP 로그인 세션 발급 (OTP 또는 복구코드 검증 → JWT 발급)
   * POST /auth/totp/sessions
   */
  @PostMapping("/totp/sessions")
  public TotpDto.VerifyResponse totpSession(
      @RequestBody TotpDto.VerifyRequest request, HttpServletResponse response, HttpServletRequest req) {
    TotpDto.VerifyResponse result = null;
    if(redisEnabled){
      result = totpService.verify(request, req);
      authService.createLoginSession(req, response, result.getAdminInfo());
    }else{
      result = totpService.verifyWithJwt(request);
      authService.issueRefreshTokenCookie(response, result.getAdminInfo().getEmail());
    }
    return result;
  }

  /**
   * 액세스 토큰 재발급 (Refresh Token 쿠키 기반)
   */
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "refreshToken", required = false) String refreshToken,HttpServletRequest request,
      Authentication authentication) {

    return ResponseEntity.ok(redisEnabled? authService.refresh(request, authentication):authService.refreshWithJwt(refreshToken));
  }

  /**
   * 로그아웃 (Refresh Token 쿠키 만료 처리)
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    if(redisEnabled){
      authService.logout(request);
    }else {
      authService.logoutWithJwt(response);
    }

    return ResponseEntity.ok().build();
  }
}

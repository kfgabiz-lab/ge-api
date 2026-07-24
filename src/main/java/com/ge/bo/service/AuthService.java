package com.ge.bo.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ge.bo.dto.LoginRequest;
import com.ge.bo.dto.LoginResponse;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.RoleRepository;
import com.ge.bo.security.JwtTokenProvider;
import com.ge.bo.sso.LseSsoService;
import com.ge.bo.sso.SsoResult;
import com.ge.bo.sso.SsoResultCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final long REFRESH_TOKEN_DAYS = 7L;
  private static final String PENDING_ROLE = "PENDING_ADMIN";

  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;

  /** 공통코드 LOGIN_LOCK_MAX_ATTEMPTS.name 에서 로드, 기본값 5 */
  private int maxFailedAttempts = 5;

  /** 공통코드 LOGIN_LOCK_ENABLED.name 에서 로드 (Y/N), 기본값 true */
  private boolean lockEnabled = true;

  /** ls.totp.enabled — false 시 2차인증 스킵하고 바로 JWT 발급 */
  @Value("${ls.totp.enabled:true}")
  private boolean totpEnabled;

  /** true = LS Electric SSO 서버 인증 / false = 로컬 BCrypt 로그인 */
  @Value("${ls.isApiLogin:false}")
  private boolean isApiLogin;

  @Value("${ls.redis-enabled:false}")
  private boolean redisEnabled;

  @Value("${ls.lse.sso.sysName:NAHP}")
  private String ssoSysName;

  /** SSO 자동 생성 계정의 기본 역할 코드 */
  @Value("${ls.lse.sso.defaultRole:USER}")
  private String ssoDefaultRole;

  /** session 만료 시간 **/
  @Value("${spring.session.timeout}")
  private Duration sessionTimeout;

  private final AdminRepository adminRepository;
  private final RoleRepository roleRepository;
  private final CodeDetailRepository codeDetailRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RecaptchaService recaptchaService;
  private final LseSsoService lseSsoService;
  private final LoginAdminService loginAdminService;
  private final LoginLogService loginLogService;

  @PostConstruct
  void loadConfig() {
    log.info("[AuthService] totpEnabled={}, isApiLogin={}", totpEnabled, isApiLogin);
    codeDetailRepository.findFirstByGroup_GroupCodeAndActiveTrue("LOGIN_LOCK_MAX_ATTEMPTS")
        .map(cd -> {
          try { return Integer.parseInt(cd.getName()); } catch (NumberFormatException e) { return null; }
        })
        .ifPresent(v -> {
          maxFailedAttempts = v;
          log.info("LOGIN_LOCK_MAX_ATTEMPTS 공통코드에서 로드: {}", v);
        });

    codeDetailRepository.findFirstByGroup_GroupCodeAndActiveTrue("LOGIN_LOCK_ENABLED")
        .ifPresent(cd -> {
          lockEnabled = "Y".equalsIgnoreCase(cd.getName());
          log.info("LOGIN_LOCK_ENABLED 공통코드에서 로드: {}", cd.getName());
        });
  }

  /**
   * 1단계 로그인 (reCAPTCHA + 비밀번호 검증)
   * ls.isApiLogin=true  → LS Electric SSO 서버 인증 후 JWT 발급 (TOTP 스킵)
   * totp.enabled=true   → tempToken 발급 후 2FA 단계 진행
   * totp.enabled=false  → 바로 accessToken 발급 (2FA 스킵)
   *
   * @param request   로그인 요청 DTO (이메일/사원번호, 비밀번호, reCAPTCHA 토큰)
   * @param clientIp  요청자 IP (AuthController에서 X-Forwarded-For 우선 추출)
   * @param userAgent 브라우저 User-Agent
   * @return LoginResponse
   */
  @Transactional
  public LoginResponse login(LoginRequest request, String clientIp, String userAgent, HttpServletRequest req) {
    if (request.getEmail() == null || request.getEmail().isBlank()
            || request.getPassword() == null || request.getPassword().isBlank()) {
      loginLogService.saveAsync(null, request.getEmail(), "FAIL", "INVALID_CREDENTIALS", clientIp, userAgent);
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
              "ID or password가 일치하지 않습니다.");
    }

    if (isApiLogin) {
      return ssoLogin(request, clientIp, userAgent, req);
    }

    // reCAPTCHA 토큰 검증
    recaptchaService.verify(request.getRecaptchaToken());

    // 이메일 없음 체크 — orElseThrow 대신 분기로 변환하여 로그 저장 후 throw
    Optional<AdminUser> adminOpt = adminRepository.findByEmail(request.getEmail());
    if (adminOpt.isEmpty()) {
      loginLogService.saveAsync(null, request.getEmail(), "FAIL", "USER_NOT_FOUND", clientIp, userAgent);
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
              "ID or password가 일치하지 않습니다.");
    }
    AdminUser admin = adminOpt.get();

    // 계정 비활성화 확인
    if (!admin.isActive()) {
      loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "ACCOUNT_INACTIVE", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "로그인 권한이 없습니다.");
    }

    // 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
      if (lockEnabled) {
        int attempts = loginAdminService.incrementFailure(admin.getId(), maxFailedAttempts);
        if (attempts >= maxFailedAttempts) {
          loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "ACCOUNT_DISABLED", clientIp, userAgent);
          throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "계정이 비활성화되었습니다.");
        }
        loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "INVALID_PASSWORD", clientIp, userAgent);
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "비밀번호 " + attempts + "회 실패 하셨습니다. " + maxFailedAttempts + "회 실패 시 계정 비활성화됩니다.");
      }
      loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "INVALID_PASSWORD", clientIp, userAgent);
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
              "ID or password가 일치하지 않습니다.");
    }

    // 비밀번호 검증 성공 — 실패 카운터 초기화 + lastLoginAt 갱신
    loginAdminService.recordSuccess(admin.getId());

    // 승인 대기 확인 (SUCCESS 로그보다 먼저 체크하여 PENDING은 FAIL로 기록)
    if (PENDING_ROLE.equals(admin.getRole())) {
      loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "PENDING_APPROVAL", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "PENDING_APPROVAL", "관리자 승인을 기다리고 있습니다.");
    }

    // 로그인 성공 이력 저장
    loginLogService.saveAsync(admin.getId(), admin.getEmail(), "SUCCESS", null, clientIp, userAgent);

    // 2차인증 비활성화 시 바로 accessToken 발급
    if (!totpEnabled) {
      return directLoginResponse(admin);
    }else{
      return tempLoginResponse(req, admin);
    }
  }

  /**
   * Refresh Token으로 액세스 토큰 재발급
   *
   * @param refreshToken httpOnly 쿠키에서 읽은 Refresh Token
   * @return 새 액세스 토큰 및 관리자 정보 응답 DTO
   */
  @Transactional(readOnly = true)
  public LoginResponse refreshWithJwt(String refreshToken) {
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
   * 로그아웃 처리 (session 삭제)
   *
   * @param request HTTP 응답 객체 (쿠키 만료 설정용)
   */
  public void logout(HttpServletRequest request) {
    invalidateLoginSession(request);
  }

  /**
   * 로그아웃 처리 (Refresh Token 쿠키 만료)
   *
   * @param response HTTP 응답 객체 (쿠키 만료 설정용)
   */
  public void logoutWithJwt(HttpServletResponse response) {
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

  /**
   * SSO 로그인
   * OK    → 기존 TOTP 흐름 (totpEnabled 설정 따름)
   * FAIL  → 실패 누적 없이 SSO 메시지 반환 (퇴사자/휴직자 등)
   * ERROR → 실패횟수 +1 후 오류 메시지 반환
   */
  private LoginResponse ssoLogin(LoginRequest request, String clientIp, String userAgent, HttpServletRequest req) {
    Optional<AdminUser> existing = adminRepository.findByEmail(request.getEmail());
    if (existing.isPresent()) {
      AdminUser a = existing.get();
      if (!a.isActive()) {
        loginLogService.saveAsync(a.getId(), a.getEmail(), "FAIL", "ACCOUNT_INACTIVE", clientIp, userAgent);
        throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "로그인 권한이 없습니다.");
      }
    }

    // SSO 서버 호출
    SsoResult sso;
    try {
      sso = lseSsoService.login(request.getEmail(), request.getPassword(), ssoSysName);
    } catch (Exception e) {
      loginLogService.saveAsync(null, request.getEmail(), "FAIL", "SSO_SERVER_ERROR", clientIp, userAgent);
      throw BusinessException.unauthorized("SSO 서버 연결에 실패했습니다.");
    }

    if (!sso.success()) {
      if (sso.result() == SsoResultCode.ERROR) {
        // 비밀번호 오류 — lock ON이고 기존 계정이 있으면 실패횟수 증가
        if (lockEnabled && existing.isPresent()) {
          AdminUser a = existing.get();
          int attempts = loginAdminService.incrementFailure(a.getId(), maxFailedAttempts);
          if (attempts >= maxFailedAttempts) {
            loginLogService.saveAsync(a.getId(), a.getEmail(), "FAIL", "ACCOUNT_DISABLED", clientIp, userAgent);
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "계정이 비활성화되었습니다.");
          }
          loginLogService.saveAsync(a.getId(), a.getEmail(), "FAIL", "INVALID_PASSWORD", clientIp, userAgent);
          throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
              "비밀번호 " + attempts + "회 실패 하셨습니다. " + maxFailedAttempts + "회 실패 시 계정 비활성화됩니다.");
        }
        loginLogService.saveAsync(null, request.getEmail(), "FAIL", "INVALID_PASSWORD", clientIp, userAgent);
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "ID or password가 일치하지 않습니다.");
      }
      // FAIL (퇴사자/비회원 등) — 기존 계정 있으면 is_active false 처리
      existing.ifPresent(a -> loginAdminService.deactivateUser(a.getId()));
      Long userId = existing.map(AdminUser::getId).orElse(null);
      loginLogService.saveAsync(userId, request.getEmail(), "FAIL", "ACCESS_DENIED", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "로그인 권한이 없습니다.");
    }

    // 신규 유저
    if (existing.isEmpty()) {
      AdminUser newUser = buildNewSsoUser(request.getEmail(), sso);
      loginAdminService.saveNewSsoUser(newUser);
      loginLogService.saveAsync(null, request.getEmail(), "FAIL", "PENDING_APPROVAL", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "PENDING_APPROVAL", "관리자 승인을 기다리고 있습니다.");
    }

    AdminUser admin = existing.get();
    loginAdminService.recordSuccess(admin.getId());

    // 부서 변경
    if (sso.deptCode() != null && !sso.deptCode().equals(admin.getDeptCode())) {
      loginAdminService.updateDeptChange(
          admin.getId(), sso.deptCode(), sso.deptName(), sso.userName());
      loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "DEPT_CHANGED", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "로그인 권한이 없습니다.");
    }

    // 이름/부서 동기화
    if (sso.deptCode() != null) admin.setDeptCode(sso.deptCode());
    if (sso.deptName() != null) admin.setDeptName(sso.deptName());
    if (sso.userName() != null) admin.setName(sso.userName());
    adminRepository.save(admin);

    // 승인 대기 확인
    if (PENDING_ROLE.equals(admin.getRole())) {
      loginLogService.saveAsync(admin.getId(), admin.getEmail(), "FAIL", "PENDING_APPROVAL", clientIp, userAgent);
      throw new BusinessException(HttpStatus.FORBIDDEN, "PENDING_APPROVAL", "관리자 승인을 기다리고 있습니다.");
    }

    // SSO 로그인 성공 이력 저장
    loginLogService.saveAsync(admin.getId(), admin.getEmail(), "SUCCESS", null, clientIp, userAgent);

    // 기존 TOTP 흐름과 동일
    if (!totpEnabled) {
      return directLoginResponse(admin);
    }else{
      return tempLoginResponse(req, admin);
    }
  }

  /** SSO 최초 로그인 유저 엔티티 빌드 */
  private AdminUser buildNewSsoUser(String email, SsoResult sso) {
    return AdminUser.builder()
        .email(email)
        .name(sso.userName() != null ? sso.userName() : sso.userId())
        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
        .role(ssoDefaultRole)
        .deptCode(sso.deptCode())
        .deptName(sso.deptName())
        .isActive(false)
        .lastLoginAt(LocalDateTime.now())
        .build();
  }

  /* 2차 로그인 사용 안함(로컬용) */
  private LoginResponse directLoginResponse(AdminUser admin){
    boolean isSystem = roleRepository.findByCode(admin.getRole())
            .map(role -> role.isSystem())
            .orElse(false);
    if(redisEnabled){
      return LoginResponse.builder()
              .accessToken("SUCCESS")
              .adminInfo(LoginResponse.AdminInfo.builder()
                      .id(admin.getId())
                      .name(admin.getName())
                      .email(admin.getEmail())
                      .role(admin.getRole())
                      .isSystem(isSystem)
                      .build())
              .build();
    }else {
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
  }

  /* 1차 로그인 임시 로그인(2차 로그인 사용 시) */
  private LoginResponse tempLoginResponse(HttpServletRequest req, AdminUser admin){
    String tempToken = null;
    if(redisEnabled){
      startMfa(req, admin.getEmail());
    }else {
      // 2FA 미완료 상태 임시 토큰 발급 (10분 유효)
      tempToken = jwtTokenProvider.generateTotpPendingToken(admin.getEmail());
    }
    if (!admin.isTotpEnabled()) {
      // 2FA 미등록 → QR 등록 화면으로
      return LoginResponse.builder()
              .tempToken(tempToken)
              .requireTotpSetup(true)
              .requireTotpVerify(false)
              .build();
    } else {
      // 2FA 등록 완료 → OTP 입력 화면으로
      return LoginResponse.builder()
              .tempToken(tempToken)
              .requireTotpSetup(false)
              .requireTotpVerify(true)
              .build();
    }
  }

  // 임시 session 발급(
  public void startMfa(
          HttpServletRequest request,
          String email
  ) {
    HttpSession session = request.getSession(true);

    session.setAttribute("MFA_EMAIL", email);
    session.setAttribute("MFA_VERIFIED", false);
    session.setMaxInactiveInterval(600); // 2차 인증 제한 10분

  }

  // session 생성 및 로그인 처리
  public void makeSessionAndAuth(
          HttpServletRequest request,
          HttpServletResponse response,
          String email,
          String role
  ) {
    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority(authority))
            );

    // 중복 세션 검사 및 세션 등록
    sessionAuthenticationStrategy.onAuthentication(
            authentication,
            request,
            response
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);

    SecurityContextHolder.setContext(context);

//    HttpSession session = request.getSession(true);
//    session.setAttribute(
//            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
//            context
//    );

    securityContextRepository.saveContext(
            context,
            request,
            response
    );

  }

  public void createLoginSession(
          HttpServletRequest request,
          HttpServletResponse response,
          LoginResponse.AdminInfo adminInfo
  ) {
    HttpSession session = request.getSession(true);

    String email = adminInfo.getEmail();
    String role = adminInfo.getRole();

    session.setAttribute("email", email);
    session.setAttribute("role", role);
    session.setMaxInactiveInterval(
            Math.toIntExact(
                    sessionTimeout.toSeconds()
            )
    );
    makeSessionAndAuth(request, response, email, role);
  }

  /**
   * Refresh Session
   */
  @Transactional(readOnly = true)
  public LoginResponse refresh(HttpServletRequest request, Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw BusinessException.unauthorized("인증이 필요합니다.");
    }

    HttpSession session = request.getSession(false);
    if (session == null) {
      throw BusinessException.unauthorized("세션이 만료되었습니다.");
    }

    String email = authentication.getName();

    AdminUser admin = adminRepository.findByEmail(email)
            .orElseThrow(() -> BusinessException.unauthorized("사용자를 찾을 수 없습니다."));

    if (!admin.isActive()) {
      throw new BusinessException(
              HttpStatus.FORBIDDEN,
              "ACCOUNT_LOCKED",
              "잠긴 계정입니다. 관리자에게 문의하세요."
      );
    }

    boolean isSystem = roleRepository.findByCode(admin.getRole())
            .map(role -> role.isSystem())
            .orElse(false);

    return LoginResponse.builder()
            .accessToken("SUCCESS") // 세션 방식에서는 accessToken 발급 안 함
            .adminInfo(LoginResponse.AdminInfo.builder()
                    .id(admin.getId())
                    .name(admin.getName())
                    .email(admin.getEmail())
                    .role(admin.getRole())
                    .isSystem(isSystem)
                    .build())
            .build();
  }

  public void invalidateLoginSession(HttpServletRequest request)
  {
    HttpSession session = request.getSession(false);

    if (session != null) {
      session.invalidate();
    }
  }
}

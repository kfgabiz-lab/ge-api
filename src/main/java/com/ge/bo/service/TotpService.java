package com.ge.bo.service;

import com.ge.bo.dto.LoginResponse;
import com.ge.bo.dto.TotpDto;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.RoleRepository;
import com.ge.bo.security.JwtTokenProvider;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP 2차 인증 서비스 (QR 등록, 코드 검증) */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

  private static final long ACCESS_TOKEN_EXPIRES_IN = 3600L;

  private final AdminRepository adminRepository;
  private final RoleRepository roleRepository;
  private final JwtTokenProvider jwtTokenProvider;

  @Value("${ls.totp.isUser}")
  private String totpIssuer;

  @Value("${ls.redis-enabled:false}")
  private boolean redisEnabled;

  /**
   * TOTP 설정 시작 — 비밀키 생성 + QR 코드 URI 반환
   * tempToken 검증 후 DB에 비밀키 저장, FE는 QR 코드를 렌더링해 앱에 등록
   *
   * @param request tempToken 포함 요청
   * @return QR 코드 URI 및 Base32 비밀키
   */
  @Transactional
  public TotpDto.SetupResponse setup(TotpDto.SetupRequest request, HttpServletRequest req) {
    String email = getEmail(req, request.getTempToken());
    AdminUser admin = findAdmin(email);

    if (admin.isTotpEnabled()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_ALREADY_ENABLED",
          "이미 2FA가 등록된 계정입니다.");
    }

    // 비밀키 생성 (기존에 없거나 재발급 시 새로 생성)
    String secret = new DefaultSecretGenerator().generate();
    admin.setTotpSecret(secret);

    QrData qrData = new QrData.Builder()
        .label(email)
        .secret(secret)
        .issuer(totpIssuer)
        .algorithm(HashingAlgorithm.SHA1)
        .digits(6)
        .period(30)
        .build();

    return TotpDto.SetupResponse.builder()
        .qrCodeUrl(qrData.getUri())
        .secret(secret)
        .build();
  }

  /**
   * TOTP 등록 확인 — 6자리 코드 검증 후 복구코드 발급
   * 최초 QR 등록 완료 단계
   *
   * @param request 6자리 코드
   * @return accessToken + 복구코드 10개 (1회성)
   */
  @Transactional
  public TotpDto.VerifyResponse confirm(TotpDto.ConfirmRequest request, HttpServletRequest req) {
    HttpSession session = req.getSession(false);

    if (session == null) {
      throw BusinessException.unauthorized("세션이 만료되었습니다.");
    }

    String email = (String) session.getAttribute("MFA_EMAIL");

    if (email == null) {
      throw BusinessException.unauthorized("세션에 이메일 정보가 없습니다.");
    }

    AdminUser admin = findAdmin(email);

    if (admin.getTotpSecret() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_SETUP_REQUIRED",
              "TOTP 설정을 먼저 시작해주세요.");
    }

    // 6자리 코드 검증
    verifyTotpCode(admin.getTotpSecret(), request.getTotpCode());

    admin.setTotpEnabled(true);
    admin.setLastLoginAt(OffsetDateTime.now());
    admin.setFailedLoginAttempts(0);
    admin.setLockedUntil(null);

    return TotpDto.VerifyResponse.builder()
            .accessToken("SUCCESS")
            .adminInfo(buildAdminInfo(admin))
            .build();
  }

  /**
   * TOTP 등록 확인 — 6자리 코드 검증 후 복구코드 발급 + JWT 발급
   * 최초 QR 등록 완료 단계
   *
   * @param request tempToken + 6자리 코드
   * @return accessToken + 복구코드 10개 (1회성)
   */
  @Transactional
  public TotpDto.VerifyResponse confirmWithJwt(TotpDto.ConfirmRequest request) {
    String email = extractEmailFromTempToken(request.getTempToken());
    AdminUser admin = findAdmin(email);

    if (admin.getTotpSecret() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_SETUP_REQUIRED",
              "TOTP 설정을 먼저 시작해주세요.");
    }

    // 6자리 코드 검증
    verifyTotpCode(admin.getTotpSecret(), request.getTotpCode());

    admin.setTotpEnabled(true);
    admin.setLastLoginAt(OffsetDateTime.now());
    admin.setFailedLoginAttempts(0);
    admin.setLockedUntil(null);

    String accessToken = jwtTokenProvider.generateAccessToken(email, admin.getRole());

    return TotpDto.VerifyResponse.builder()
            .accessToken(accessToken)
            .expiresIn(ACCESS_TOKEN_EXPIRES_IN)
            .adminInfo(buildAdminInfo(admin))
            .build();
  }

  /**
   * TOTP 로그인 검증 — OTP 코드 또는 복구코드로 2차 인증 후 JWT 발급
   *
   * @param request tempToken + totpCode (또는 recoveryCode)
   * @return accessToken
   */
  @Transactional
  public TotpDto.VerifyResponse verify(TotpDto.VerifyRequest request, HttpServletRequest req) {
    HttpSession session = req.getSession(false);

    if (session == null) {
      throw BusinessException.unauthorized("세션이 만료되었습니다.");
    }

    String email = (String) session.getAttribute("MFA_EMAIL");

    if (email == null) {
      throw BusinessException.unauthorized("세션에 이메일 정보가 없습니다.");
    }

    AdminUser admin = findAdmin(email);

    if (!admin.isTotpEnabled()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_NOT_ENABLED",
              "2FA가 등록되지 않은 계정입니다. QR 등록을 먼저 진행해주세요.");
    }

    if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_CODE_REQUIRED",
              "OTP 코드를 입력해주세요.");
    }
    verifyTotpCode(admin.getTotpSecret(), request.getTotpCode());

    admin.setLastLoginAt(OffsetDateTime.now());
    admin.setFailedLoginAttempts(0);
    admin.setLockedUntil(null);

    return TotpDto.VerifyResponse.builder()
            .accessToken("SUCCESS")
            .adminInfo(buildAdminInfo(admin))
            .build();
  }

  /**
   * TOTP 로그인 검증 — OTP 코드 또는 복구코드로 2차 인증 후 JWT 발급
   *
   * @param request tempToken + totpCode (또는 recoveryCode)
   * @return accessToken
   */
  @Transactional
  public TotpDto.VerifyResponse verifyWithJwt(TotpDto.VerifyRequest request) {
    String email = extractEmailFromTempToken(request.getTempToken());
    AdminUser admin = findAdmin(email);

    if (!admin.isTotpEnabled()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_NOT_ENABLED",
              "2FA가 등록되지 않은 계정입니다. QR 등록을 먼저 진행해주세요.");
    }

    if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_CODE_REQUIRED",
              "OTP 코드를 입력해주세요.");
    }
    verifyTotpCode(admin.getTotpSecret(), request.getTotpCode());

    admin.setLastLoginAt(OffsetDateTime.now());
    admin.setFailedLoginAttempts(0);
    admin.setLockedUntil(null);

    String accessToken = jwtTokenProvider.generateAccessToken(email, admin.getRole());

    return TotpDto.VerifyResponse.builder()
            .accessToken(accessToken)
            .expiresIn(ACCESS_TOKEN_EXPIRES_IN)
            .adminInfo(buildAdminInfo(admin))
            .build();
  }

  /** tempToken 파싱 + 이메일 추출 */
  private String extractEmailFromTempToken(String tempToken) {
    try {
      return jwtTokenProvider.getEmailFromTotpPendingToken(tempToken);
    } catch (Exception e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_INVALID_TEMP_TOKEN",
          "유효하지 않거나 만료된 인증 세션입니다. 다시 로그인해주세요.");
    }
  }

  /** 이메일로 관리자 조회 */
  private AdminUser findAdmin(String email) {
    return adminRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "사용자를 찾을 수 없습니다."));
  }

  /** TOTP 6자리 코드 검증 */
  private void verifyTotpCode(String secret, String code) {
    DefaultCodeVerifier verifier = new DefaultCodeVerifier(
        new DefaultCodeGenerator(), new SystemTimeProvider());
    verifier.setAllowedTimePeriodDiscrepancy(2);

    if (!verifier.isValidCode(secret, code)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TOTP_CODE_INVALID",
          "인증 코드가 올바르지 않습니다. 앱의 최신 코드를 입력해주세요.");
    }
  }

  /** AdminInfo DTO 생성 */
  private LoginResponse.AdminInfo buildAdminInfo(AdminUser admin) {
    boolean isSystem = roleRepository.findByCode(admin.getRole())
        .map(role -> role.isSystem())
        .orElse(false);

    return LoginResponse.AdminInfo.builder()
        .id(admin.getId())
        .name(admin.getName())
        .email(admin.getEmail())
        .role(admin.getRole())
        .isSystem(isSystem)
        .build();
  }

  /** jwtToken 또는 redis Session에서 email 가져오기 **/
  private String getEmail(HttpServletRequest req, String tempToken){
    String email = null;

    if(redisEnabled){
      HttpSession session = req.getSession(false);

      if (session == null) {
        throw BusinessException.unauthorized("세션이 만료되었습니다.");
      }

      email = (String) session.getAttribute("MFA_EMAIL");
    }else {
      email = extractEmailFromTempToken(tempToken);
    }

    return email;
  }
}

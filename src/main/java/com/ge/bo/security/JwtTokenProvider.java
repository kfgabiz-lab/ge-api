package com.ge.bo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증 유틸리티
 */
@Slf4j
@Component
public class JwtTokenProvider {

  @Value("${app.jwt.secret}")
    private String jwtSecret;

  @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

  @Value("${app.jwt.refresh-expiration}")
    private long jwtRefreshExpirationMs;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

    /**
     * Access Token 생성
     */
  public String generateAccessToken(String email, String role) {
    return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs * 1000))
                .signWith(getSigningKey())
                .compact();
  }

    /**
     * Refresh Token 생성
     */
  public String generateRefreshToken(String email) {
    return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtRefreshExpirationMs * 1000))
                .signWith(getSigningKey())
                .compact();
  }

    /**
     * TOTP 임시 토큰 생성 (10분 유효, 2FA 미완료 상태 표시)
     */
  public String generateTotpPendingToken(String email) {
    return Jwts.builder()
                .subject(email)
                .claim("type", "TOTP_PENDING")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 10L * 60 * 1000))
                .signWith(getSigningKey())
                .compact();
  }

    /**
     * 미리보기 토큰 생성 (5분 유효, 지정된 slug+recordId 1건만 조회 가능)
     */
  public String generatePreviewToken(String email, String slug, String recordId) {
    return Jwts.builder()
                .subject(email)
                .claim("purpose", "preview")
                .claim("slug", slug)
                .claim("recordId", recordId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 5L * 60 * 1000))
                .signWith(getSigningKey())
                .compact();
  }

    /**
     * 미리보기 토큰 검증 (purpose=preview + slug/recordId 일치 여부까지 확인)
     */
  public boolean validatePreviewToken(String token, String slug, String recordId) {
    try {
      Claims claims = parseToken(token);
      return "preview".equals(claims.get("purpose", String.class))
          && slug.equals(claims.get("slug", String.class))
          && recordId.equals(claims.get("recordId", String.class));
    } catch (JwtException | IllegalArgumentException e) {
      log.error("유효하지 않은 미리보기 토큰: {}", e.getMessage());
      return false;
    }
  }

    /**
     * TOTP 임시 토큰에서 이메일 추출 (type=TOTP_PENDING 검증 포함)
     */
  public String getEmailFromTotpPendingToken(String token) {
    Claims claims = parseToken(token);
    if (!"TOTP_PENDING".equals(claims.get("type", String.class))) {
      throw new io.jsonwebtoken.JwtException("TOTP_PENDING 토큰이 아닙니다.");
    }
    return claims.getSubject();
  }

    /**
     * 토큰에서 이메일 추출
     */
  public String getEmailFromToken(String token) {
    return parseToken(token).getSubject();
  }

    /**
     * 토큰에서 권한 추출
     */
  public String getRoleFromToken(String token) {
    return parseToken(token).get("role", String.class);
  }

    /**
     * 토큰 유효성 검증
     */
  public boolean validateToken(String token) {
    try {
      parseToken(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.error("유효하지 않은 JWT 토큰: {}", e.getMessage());
      return false;
    }
  }

  private Claims parseToken(String token) {
    return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
  }
}

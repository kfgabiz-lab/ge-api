package com.ge.bo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 모든 요청에서 JWT 토큰을 추출하고 인증 처리하는 필터
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
    try {
      String token = extractTokenFromRequest(request);

      if (StringUtils.hasText(token)) {
        if (jwtTokenProvider.validateToken(token)) {
          String email = jwtTokenProvider.getEmailFromToken(token);
          String role = jwtTokenProvider.getRoleFromToken(token);

                    // 토큰에서 role 클레임 추출하여 권한 설정 (ROLE_ 접두사 중복 방지)
          String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

          UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null,
                            List.of(new SimpleGrantedAuthority(authority)));

          SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
          log.warn("유효하지 않거나 만료된 JWT 토큰입니다. 401 Unauthorized 응답을 반환합니다.");
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType("application/json;charset=UTF-8");
          String errBody = "{\"status\": 401, \"error\": \"UNAUTHORIZED\","
              + " \"message\": \"세션이 만료되었거나 유효하지 않은 요청입니다. 다시 로그인해주세요.\"}";
          response.getWriter().write(errBody);
          return; // 필터 체인 종료
        }
      }
    } catch (Exception e) {
      log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter()
                    .write("{\"status\": 401, \"error\": \"UNAUTHORIZED\", \"message\": \"인증 처리 중 오류가 발생했습니다.\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
  private String extractTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}

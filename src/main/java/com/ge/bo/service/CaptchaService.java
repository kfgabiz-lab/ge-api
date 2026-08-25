package com.ge.bo.service;

import com.ge.bo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 자체 구현 이미지 캡차 서비스 (reCAPTCHA 대체)
 * 세션/서버 상태 없이 AES 암호화 토큰에 정답+발급시각을 담아 클라이언트에 왕복시키는 stateless 챌린지 방식
 * (ls.redis-enabled 분기와 무관하게 동일하게 동작, FO가 다른 오리진이어도 세션/쿠키 불필요).
 * 다른 사이트(Salesforce Apex DE_FrontEndController#generateCaptcha/validateCaptcha)의 구현을 이식.
 */
@Service
public class CaptchaService {

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 16; // AES-128
    private static final long EXPIRY_MILLIS = 5 * 60 * 1000L; // 300초
    private static final int SVG_WIDTH = 150;
    private static final int SVG_HEIGHT = 40;

    @Value("${ls.lse.outApi.captchaKey}")
    private String encryptionKey;

    private final SecureRandom random = new SecureRandom();

    public record CaptchaResponse(String captchaImage, String captchaToken) {}

    /**
     * 캡차 이미지(SVG data URI)와 정답+발급시각을 담은 암호화 토큰을 생성한다.
     */
    public CaptchaResponse generate() {
        String code = String.valueOf(random.nextInt(9000) + 1000); // 1000~9999
        String payload = code + "|" + System.currentTimeMillis();
        String token = encrypt(payload);

        String svg = buildSvg(code);
        String dataUri = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

        return new CaptchaResponse(dataUri, token);
    }

    /**
     * 캡차 토큰 검증 — 복호화 후 정답 일치 + 만료(300초) 여부를 확인한다.
     *
     * @param captchaToken 캡차 발급 시 받은 암호화 토큰(FE가 그대로 되돌려줌)
     * @param inputCaptcha 사용자 입력값
     */
    public void verify(String captchaToken, String inputCaptcha) {
        if (captchaToken == null || captchaToken.isBlank() || inputCaptcha == null || inputCaptcha.isBlank()) {
            throw captchaFailed();
        }

        String payload;
        try {
            payload = decrypt(captchaToken);
        } catch (Exception e) {
            throw captchaFailed();
        }

        String[] parts = payload.split("\\|", 2);
        if (parts.length < 2) {
            throw captchaFailed();
        }

        String code = parts[0];
        long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw captchaFailed();
        }

        if (System.currentTimeMillis() - issuedAt > EXPIRY_MILLIS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CAPTCHA_EXPIRED",
                    "캡차가 만료되었습니다. 새로고침 후 다시 시도해주세요.");
        }

        if (!code.equals(inputCaptcha.trim())) {
            throw captchaFailed();
        }
    }

    private BusinessException captchaFailed() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "CAPTCHA_FAILED",
                "캡차 인증에 실패했습니다. 다시 시도해주세요.");
    }

    private String encrypt(String payload) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // IV를 암호문 앞에 붙여서 함께 전달 (복호화 시 그대로 분리)
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "CAPTCHA_GENERATION_FAILED",
                    "캡차 생성에 실패했습니다.");
        }
    }

    private String decrypt(String token) throws Exception {
        byte[] combined = Base64.getDecoder().decode(token);
        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Invalid captcha token");
        }

        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec(), new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKeySpec keySpec() {
        byte[] keyBytes = new byte[KEY_LENGTH];
        byte[] source = encryptionKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(source, 0, keyBytes, 0, Math.min(source.length, KEY_LENGTH));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** SVG 기반 캡차 이미지 생성 — 배경 그라데이션 + 곡선/직선/점 노이즈 + 회전된 문자 */
    private String buildSvg(String code) {
        StringBuilder svg = new StringBuilder();

        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(SVG_WIDTH)
                .append("\" height=\"").append(SVG_HEIGHT).append("\">");

        svg.append("<defs><linearGradient id=\"bg\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">")
                .append("<stop offset=\"0%\" style=\"stop-color:#003777\"/>")
                .append("<stop offset=\"100%\" style=\"stop-color:#00274f\"/>")
                .append("</linearGradient></defs>");
        svg.append("<rect width=\"").append(SVG_WIDTH).append("\" height=\"").append(SVG_HEIGHT)
                .append("\" fill=\"url(#bg)\"/>");

        // 흐릿한 곡선 3개
        for (int i = 0; i < 3; i++) {
            int y1 = randomInt(SVG_HEIGHT);
            int y2 = randomInt(SVG_HEIGHT);
            int cy = randomInt(SVG_HEIGHT);
            double alpha = (15 + randomInt(25)) / 100.0;
            svg.append("<path d=\"M0,").append(y1).append(" Q").append(SVG_WIDTH / 2).append(',').append(cy)
                    .append(' ').append(SVG_WIDTH).append(',').append(y2).append("\" ")
                    .append("stroke=\"rgba(255,255,255,").append(fmt(alpha)).append(")\" stroke-width=\"1\" fill=\"none\"/>");
        }

        // 노이즈 직선 20개
        for (int i = 0; i < 20; i++) {
            int x1 = randomInt(SVG_WIDTH);
            int y1 = randomInt(SVG_HEIGHT);
            int x2 = x1 + randomInt(12) - 6;
            int y2 = y1 + randomInt(12) - 6;
            double alpha = (20 + randomInt(40)) / 100.0;
            svg.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1).append("\" x2=\"").append(x2)
                    .append("\" y2=\"").append(y2).append("\" stroke=\"rgba(255,255,255,").append(fmt(alpha))
                    .append(")\" stroke-width=\"1\"/>");
        }

        // 노이즈 점 60개
        for (int i = 0; i < 60; i++) {
            int x = randomInt(SVG_WIDTH);
            int y = randomInt(SVG_HEIGHT);
            double alpha = (30 + randomInt(50)) / 100.0;
            svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"1\" fill=\"rgba(255,255,255,")
                    .append(fmt(alpha)).append(")\"/>");
        }

        int charSpacing = 26;
        int totalTextWidth = code.length() * charSpacing;
        int startX = (SVG_WIDTH - totalTextWidth) / 2 + 5;
        int baseY = 28;

        for (int i = 0; i < code.length(); i++) {
            int angle = randomInt(30) - 15;
            int x = startX + i * charSpacing + randomInt(4) - 2;
            int y = baseY + randomInt(6) - 3;
            char ch = code.charAt(i);

            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" transform=\"rotate(").append(angle).append(' ').append(x).append(' ').append(y)
                    .append(")\" fill=\"white\" font-size=\"22\" font-weight=\"bold\" font-family=\"Arial,sans-serif\"")
                    .append(" style=\"user-select:none\">").append(ch).append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private int randomInt(int bound) {
        return random.nextInt(bound);
    }

    private String fmt(double alpha) {
        return String.format(Locale.US, "%.2f", alpha);
    }
}

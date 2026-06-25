package com.ge.bo.sso;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LseSsoService {

    private final SsoClient ssoClient;
    private final ObjectMapper objectMapper;

    /**
     * LS Electric SSO 로그인 수행
     * 1. encPWD 생성
     * 2. id / encPWD / sysName 각각 암호화
     * 3. SSO 서버(userAccount.do) 호출
     * 4. 응답 복호화 후 SsoResult 반환
     */
    public SsoResult login(String userId, String password, String sysName) {
        try {
            String encPwd       = SsoCryptoUtil.makeEncPwd(userId, password);
            String encUserId    = ssoClient.encrypt(userId);
            String encPwdCipher = ssoClient.encrypt(encPwd);
            String encSysName   = ssoClient.encrypt(sysName);

            String response  = ssoClient.callSSO(encUserId, encPwdCipher, encSysName);
            String decrypted = ssoClient.decrypt(response);

            Map<String, String> parsed = objectMapper.readValue(decrypted, new TypeReference<>() {});
            SsoResultCode resultCode = SsoResultCode.from(parsed.get("result"));

            return new SsoResult(
                    resultCode == SsoResultCode.OK,
                    resultCode,
                    userId,
                    parsed.get("user_nm"),
                    parsed.get("dept_cd"),
                    parsed.get("dept_nm"),
                    parsed.get("message")
            );
        } catch (Exception e) {
            throw new SsoException("SSO 로그인 처리 중 오류가 발생했습니다.", e);
        }
    }
}

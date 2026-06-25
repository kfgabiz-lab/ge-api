package com.ge.bo.sso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

@Component
public class SsoClient {

    private static final String CIPHER_KEY = "LSeWPwork";
    private static final String SSO_A_PARAM = "8c7537cbfb56e9db4958c58e4fb00c02";

    private final String baseUrl;
    private final RestClient restClient;

    public SsoClient(
            @Value("${ls.lse.sso.baseUrl:https://lsesso.ls-electric.com/service}") String baseUrl,
            @Value("${ls.lse.sso.connectTimeoutMs:3000}") int connectTimeoutMs,
            @Value("${ls.lse.sso.readTimeoutMs:3000}") int readTimeoutMs) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String encrypt(String value) { return getCipher(value, "E"); }
    public String decrypt(String value) { return getCipher(value, "D"); }

    /** userAccount.do SSO 로그인 검증 */
    public String callSSO(String encUserId, String encPassword, String encSysName) {
        URI uri = URI.create(baseUrl + "/userAccount.do"
                + "?a=" + SSO_A_PARAM
                + "&b=" + SsoCryptoUtil.urlEncode(encUserId)
                + "&c=" + SsoCryptoUtil.urlEncode(encPassword)
                + "&d=" + SsoCryptoUtil.urlEncode(encSysName));
        return restClient.get().uri(uri).retrieve().body(String.class);
    }

    /** tripledes.do 암호화(E) / 복호화(D) */
    private String getCipher(String value, String mode) {
        URI uri = URI.create(baseUrl + "/tripledes.do"
                + "?key=" + CIPHER_KEY
                + "&val=" + SsoCryptoUtil.urlEncode(value)
                + "&mode=" + mode);
        return restClient.get().uri(uri).retrieve().body(String.class);
    }
}

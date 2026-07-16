package com.ge.bo.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Connect Portal(CTP) 연동 설정 — IF_SRR_NAHP_CTP_0001/_0002 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ls.lse.out-api.ctp")
public class CtpProperties {

    private String tokenUrl;
    private String apiUrl;
    private String detailApiUrl;
    private String clientId;
    private String clientSecret;
    private String grantType;
}

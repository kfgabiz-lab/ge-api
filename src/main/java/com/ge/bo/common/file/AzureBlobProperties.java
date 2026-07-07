package com.ge.bo.common.file;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ls.lse.azure.blob-storage")
public class AzureBlobProperties {

    private String endpointUrl;
    private String containerName;
    private String sasToken;
}

package com.ge.bo.batch.serverresource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ls.server-resource-monitor")
public class ServerResourceMonitorProperties {

  private Map<String, String> processPathPatterns = new LinkedHashMap<>();
}

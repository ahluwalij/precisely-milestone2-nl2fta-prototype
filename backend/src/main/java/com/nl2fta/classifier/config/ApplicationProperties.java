package com.nl2fta.classifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "fta")
public class ApplicationProperties {

  private String version;
  private int detectWindow;
  private int maxCardinality;
  private boolean enableDefaultSemanticTypes;
  private String customTypesFile;
  private String pluginResource;
  private String pluginRegistrationName;

  private Cache cache = new Cache();

  @Data
  public static class Cache {
    private boolean enabled;
    private long maxSize;
    private long expireAfterWriteMinutes;
  }
}

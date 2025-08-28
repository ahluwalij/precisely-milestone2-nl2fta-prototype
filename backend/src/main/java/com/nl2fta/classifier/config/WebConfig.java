package com.nl2fta.classifier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${cors.allowed-origins}")
  private String[] allowedOrigins;

  private static final String[] DEFAULT_METHODS =
      new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
  private static final String[] DEFAULT_HEADERS = new String[] {"*"};
  private static final boolean DEFAULT_ALLOW_CREDENTIALS = true;

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/", "/swagger-ui/index.html");
    registry.setOrder(1);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    var mapping = registry.addMapping("/**");

    // If no explicit origins configured, allow all origins by default using patterns
    // (compatible with credentials)
    if (allowedOrigins == null || allowedOrigins.length == 0) {
      mapping.allowedOriginPatterns("*");
    } else {
      mapping.allowedOriginPatterns(allowedOrigins);
    }

    mapping
        .allowedMethods(DEFAULT_METHODS)
        .allowedHeaders(DEFAULT_HEADERS)
        .allowCredentials(DEFAULT_ALLOW_CREDENTIALS);
  }
}

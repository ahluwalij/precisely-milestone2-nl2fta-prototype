package com.nl2fta.classifier.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

public class WebConfigTest {

  private WebConfig webConfig;

  @BeforeEach
  public void setUp() {
    webConfig = new WebConfig();

    // Set field values using reflection
    ReflectionTestUtils.setField(
        webConfig,
        "allowedOrigins",
        new String[] {"http://localhost:3000", "http://localhost:4200"});
  }

  @Test
  public void testAddViewControllers() {
    ViewControllerRegistry registry = mock(ViewControllerRegistry.class);

    webConfig.addViewControllers(registry);

    verify(registry).addRedirectViewController("/", "/swagger-ui/index.html");
    verify(registry).setOrder(1);
  }

  @Test
  public void testAddCorsMappings() {
    CorsRegistry registry = mock(CorsRegistry.class);
    CorsRegistration registration = mock(CorsRegistration.class);

    when(registry.addMapping("/**")).thenReturn(registration);
    when(registration.allowedOriginPatterns(any(String[].class))).thenReturn(registration);
    when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
    when(registration.allowedHeaders(any(String[].class))).thenReturn(registration);
    when(registration.allowCredentials(anyBoolean())).thenReturn(registration);

    webConfig.addCorsMappings(registry);

    verify(registry).addMapping("/**");
    verify(registration)
        .allowedOriginPatterns(new String[] {"http://localhost:3000", "http://localhost:4200"});
    verify(registration).allowedMethods(new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"});
    verify(registration).allowedHeaders(new String[] {"*"});
    verify(registration).allowCredentials(true);
  }

  @Test
  public void testAddCorsMappingsWithDifferentValues() {
    WebConfig config = new WebConfig();
    ReflectionTestUtils.setField(config, "allowedOrigins", new String[] {"https://example.com"});

    CorsRegistry registry = mock(CorsRegistry.class);
    CorsRegistration registration = mock(CorsRegistration.class);

    when(registry.addMapping("/**")).thenReturn(registration);
    when(registration.allowedOriginPatterns(any(String[].class))).thenReturn(registration);
    when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
    when(registration.allowedHeaders(any(String[].class))).thenReturn(registration);
    when(registration.allowCredentials(anyBoolean())).thenReturn(registration);

    config.addCorsMappings(registry);

    verify(registration).allowedOriginPatterns(new String[] {"https://example.com"});
    verify(registration).allowedMethods(new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"});
    verify(registration).allowedHeaders(new String[] {"*"});
    verify(registration).allowCredentials(true);
  }

  @Test
  public void testEmptyAllowedOrigins() {
    WebConfig config = new WebConfig();
    ReflectionTestUtils.setField(config, "allowedOrigins", new String[] {});

    CorsRegistry registry = mock(CorsRegistry.class);
    CorsRegistration registration = mock(CorsRegistration.class);

    when(registry.addMapping("/**")).thenReturn(registration);
    when(registration.allowedOriginPatterns(any(String[].class))).thenReturn(registration);
    when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
    when(registration.allowedHeaders(any(String[].class))).thenReturn(registration);
    when(registration.allowCredentials(anyBoolean())).thenReturn(registration);

    config.addCorsMappings(registry);

    // When allowedOrigins is empty, it should default to allowing all origins with "*"
    verify(registration).allowedOriginPatterns("*");
    verify(registration).allowedMethods(new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"});
    verify(registration).allowedHeaders(new String[] {"*"});
    verify(registration).allowCredentials(true);
  }
}

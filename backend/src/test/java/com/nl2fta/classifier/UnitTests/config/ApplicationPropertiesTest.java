package com.nl2fta.classifier.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = ApplicationProperties.class)
@EnableConfigurationProperties(ApplicationProperties.class)
@TestPropertySource(
    properties = {
      "fta.version=1.0.0-test",
      "fta.detect-window=100",
      "fta.max-cardinality=1000",
      "fta.enable-default-semantic-types=true",
      "fta.custom-types-file=custom-types-test.json",
      "fta.plugin-resource=plugins-test.json",
      "fta.plugin-registration-name=test-registration",
      "fta.cache.enabled=true",
      "fta.cache.max-size=500",
      "fta.cache.expire-after-write-minutes=30"
    })
public class ApplicationPropertiesTest {

  @Autowired private ApplicationProperties applicationProperties;

  @Test
  public void testPropertiesLoading() {
    assertNotNull(applicationProperties);
    assertEquals("1.0.0-test", applicationProperties.getVersion());
    assertEquals(100, applicationProperties.getDetectWindow());
    assertEquals(1000, applicationProperties.getMaxCardinality());
    assertTrue(applicationProperties.isEnableDefaultSemanticTypes());
    assertEquals("custom-types-test.json", applicationProperties.getCustomTypesFile());
    assertEquals("plugins-test.json", applicationProperties.getPluginResource());
    assertEquals("test-registration", applicationProperties.getPluginRegistrationName());
  }

  @Test
  public void testCacheProperties() {
    assertNotNull(applicationProperties.getCache());
    assertTrue(applicationProperties.getCache().isEnabled());
    assertEquals(500, applicationProperties.getCache().getMaxSize());
    assertEquals(30, applicationProperties.getCache().getExpireAfterWriteMinutes());
  }

  @Test
  public void testDefaultCacheInitialization() {
    ApplicationProperties props = new ApplicationProperties();
    assertNotNull(props.getCache());
    assertFalse(props.getCache().isEnabled());
    assertEquals(0, props.getCache().getMaxSize());
    assertEquals(0, props.getCache().getExpireAfterWriteMinutes());
  }
}

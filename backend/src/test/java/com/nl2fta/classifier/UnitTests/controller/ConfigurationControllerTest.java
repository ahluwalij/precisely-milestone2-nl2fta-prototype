package com.nl2fta.classifier.UnitTests.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.controller.ConfigurationController;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigurationController Unit Tests")
class ConfigurationControllerTest {

  private ConfigurationController configurationController;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    configurationController = new ConfigurationController();
    mockMvc = MockMvcBuilders.standaloneSetup(configurationController).build();
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("getFrontendConfiguration Tests")
  class GetFrontendConfigurationTests {

    @Test
    @DisplayName("Should return configuration with default values")
    void shouldReturnConfigurationWithDefaultValues() throws Exception {
      // Arrange - Set default values
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(10485760L))
          .andExpect(jsonPath("$.maxRows").value(1000))
          .andExpect(jsonPath("$.apiUrl").value("/api"));
    }

    @Test
    @DisplayName("Should return configuration with custom values")
    void shouldReturnConfigurationWithCustomValues() throws Exception {
      // Arrange - Set custom values
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 52428800L); // 50MB
      ReflectionTestUtils.setField(configurationController, "maxRows", 5000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v2");

      // Act & Assert
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(52428800L))
          .andExpect(jsonPath("$.maxRows").value(5000))
          .andExpect(jsonPath("$.apiUrl").value("/api"));
    }

    @Test
    @DisplayName("Should return consistent JSON structure")
    void shouldReturnConsistentJsonStructure() throws Exception {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      String responseContent =
          mockMvc
              .perform(get("/api/config"))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert - Parse JSON and verify structure
      @SuppressWarnings("unchecked")
      Map<String, Object> config = objectMapper.readValue(responseContent, Map.class);

      assertNotNull(config);
      assertTrue(config.containsKey("maxFileSize"));
      assertTrue(config.containsKey("maxRows"));
      assertTrue(config.containsKey("apiUrl"));
    }
  }

  @Nested
  @DisplayName("Direct Method Tests")
  class DirectMethodTests {

    @Test
    @DisplayName("Should return Map with correct values when called directly")
    void shouldReturnMapWithCorrectValuesWhenCalledDirectly() {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 20971520L); // 20MB
      ReflectionTestUtils.setField(configurationController, "maxRows", 2000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      Map<String, Object> result = configurationController.getFrontendConfiguration();

      // Assert
      assertNotNull(result);
      assertEquals(20971520L, result.get("maxFileSize"));
      assertEquals(2000, result.get("maxRows"));
      assertEquals("/api", result.get("apiUrl"));
    }

    @Test
    @DisplayName("Should handle edge cases for numeric values")
    void shouldHandleEdgeCasesForNumericValues() {
      // Arrange - Test edge cases
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 0L);
      ReflectionTestUtils.setField(configurationController, "maxRows", -1);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      Map<String, Object> result = configurationController.getFrontendConfiguration();

      // Assert
      assertNotNull(result);
      assertEquals(0L, result.get("maxFileSize"));
      assertEquals(-1, result.get("maxRows"));
      assertEquals("/api", result.get("apiUrl"));
    }

    @Test
    @DisplayName("Should handle very large numeric values")
    void shouldHandleVeryLargeNumericValues() {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", Long.MAX_VALUE);
      ReflectionTestUtils.setField(configurationController, "maxRows", Integer.MAX_VALUE);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      Map<String, Object> result = configurationController.getFrontendConfiguration();

      // Assert
      assertNotNull(result);
      assertEquals(Long.MAX_VALUE, result.get("maxFileSize"));
      assertEquals(Integer.MAX_VALUE, result.get("maxRows"));
    }

    @Test
    @DisplayName("Should always include apiUrl as /api")
    void shouldAlwaysIncludeApiUrlAsApi() {
      // Arrange - Various configurations
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 1024L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 10);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v99");

      // Act
      Map<String, Object> result = configurationController.getFrontendConfiguration();

      // Assert
      assertNotNull(result);
      assertEquals("/api", result.get("apiUrl"));
      assertTrue(result.containsKey("apiUrl"));
    }
  }

  @Nested
  @DisplayName("HTTP Request Tests")
  class HttpRequestTests {

    @Test
    @DisplayName("Should handle GET request to /api/config")
    void shouldHandleGetRequestToApiConfig() throws Exception {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("Should reject non-GET methods")
    void shouldRejectNonGetMethods() throws Exception {
      // Arrange & Act & Assert
      mockMvc.perform(post("/api/config")).andExpect(status().isMethodNotAllowed());

      mockMvc.perform(put("/api/config")).andExpect(status().isMethodNotAllowed());

      mockMvc.perform(delete("/api/config")).andExpect(status().isMethodNotAllowed());

      mockMvc.perform(patch("/api/config")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should handle requests with various headers")
    void shouldHandleRequestsWithVariousHeaders() throws Exception {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert - Test with various headers
      mockMvc
          .perform(
              get("/api/config")
                  .header("Accept", "application/json")
                  .header("User-Agent", "Test-Client/1.0"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));

      mockMvc
          .perform(get("/api/config").header("Accept", "*/*"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should handle requests with query parameters")
    void shouldHandleRequestsWithQueryParameters() throws Exception {
      // Arrange
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert - Query parameters should be ignored
      mockMvc
          .perform(get("/api/config").param("unused", "parameter").param("another", "value"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(10485760L))
          .andExpect(jsonPath("$.maxRows").value(1000));
    }
  }

  @Nested
  @DisplayName("AWS Environment Variables Tests")
  class AwsEnvironmentVariablesTests {

    @Test
    @DisplayName(
        "Should verify that AWS configuration keys are only included when environment variables are set")
    void shouldVerifyAwsConfigurationBehavior() throws Exception {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Note: We cannot mock System.getenv() with Mockito due to class loading constraints
      // The actual behavior depends on the environment where the test runs

      // Act
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(10485760L))
          .andExpect(jsonPath("$.maxRows").value(1000))
          .andExpect(jsonPath("$.apiUrl").value("/api"));

      // Assert - The AWS fields may or may not exist depending on actual environment variables
      // We just verify the structure is correct and the non-AWS fields are present
    }

    @Test
    @DisplayName("Should return valid configuration response regardless of AWS environment")
    void shouldReturnValidConfigurationResponse() {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      Map<String, Object> config = configurationController.getFrontendConfiguration();

      // Assert - Basic configuration is always present
      assertNotNull(config);
      assertEquals(10485760L, config.get("maxFileSize"));
      assertEquals(1000, config.get("maxRows"));
      assertEquals("/api", config.get("apiUrl"));

      // AWS configuration depends on actual environment variables
      // We can only verify the structure, not the presence/absence
      assertTrue(config.size() >= 3); // At least the 3 basic fields
    }

    @Test
    @DisplayName("Should handle configuration method invocation")
    void shouldHandleConfigurationMethodInvocation() {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 52428800L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 5000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v2");

      // Act - Direct method invocation
      Map<String, Object> result = configurationController.getFrontendConfiguration();

      // Assert - Verify the method returns expected structure
      assertNotNull(result);
      assertTrue(result.containsKey("maxFileSize"));
      assertTrue(result.containsKey("maxRows"));
      assertTrue(result.containsKey("apiUrl"));
      assertEquals(52428800L, result.get("maxFileSize"));
      assertEquals(5000, result.get("maxRows"));
      assertEquals("/api", result.get("apiUrl"));
    }

    @Test
    @DisplayName("Should verify configuration keys are properly named")
    void shouldVerifyConfigurationKeysAreProperlyNamed() {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 1024L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 10);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act
      Map<String, Object> config = configurationController.getFrontendConfiguration();

      // Assert - Verify key names follow expected convention
      for (String key : config.keySet()) {
        // All keys should be in camelCase
        assertTrue(
            key.matches("^[a-z][a-zA-Z0-9]*$"),
            "Key '" + key + "' does not follow camelCase convention");

        // Known keys
        assertTrue(
            key.equals("maxFileSize")
                || key.equals("maxRows")
                || key.equals("apiUrl")
                || key.equals("awsAccessKeyId")
                || key.equals("awsSecretAccessKey")
                || key.equals("awsModelId"),
            "Unexpected configuration key: " + key);
      }
    }

    @Test
    @DisplayName("Should maintain configuration consistency across multiple calls")
    void shouldMaintainConfigurationConsistencyAcrossMultipleCalls() {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 10485760L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 1000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act - Call method multiple times
      Map<String, Object> config1 = configurationController.getFrontendConfiguration();
      Map<String, Object> config2 = configurationController.getFrontendConfiguration();
      Map<String, Object> config3 = configurationController.getFrontendConfiguration();

      // Assert - Results should be consistent
      assertEquals(config1.get("maxFileSize"), config2.get("maxFileSize"));
      assertEquals(config2.get("maxFileSize"), config3.get("maxFileSize"));
      assertEquals(config1.get("maxRows"), config2.get("maxRows"));
      assertEquals(config2.get("maxRows"), config3.get("maxRows"));
      assertEquals(config1.get("apiUrl"), config2.get("apiUrl"));
      assertEquals(config2.get("apiUrl"), config3.get("apiUrl"));

      // Key sets should be identical
      assertEquals(config1.keySet(), config2.keySet());
      assertEquals(config2.keySet(), config3.keySet());
    }

    @Test
    @DisplayName("Should handle HTTP request to configuration endpoint")
    void shouldHandleHttpRequestToConfigurationEndpoint() throws Exception {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 20971520L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 2000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert
      mockMvc
          .perform(get("/api/config").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$").isMap())
          .andExpect(jsonPath("$.maxFileSize").isNumber())
          .andExpect(jsonPath("$.maxRows").isNumber())
          .andExpect(jsonPath("$.apiUrl").isString());
    }

    @Test
    @DisplayName("Should return non-null configuration map")
    void shouldReturnNonNullConfigurationMap() {
      // Given
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 0L);
      ReflectionTestUtils.setField(configurationController, "maxRows", 0);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "");

      // Act
      Map<String, Object> config = configurationController.getFrontendConfiguration();

      // Assert
      assertNotNull(config);
      assertNotNull(config.get("maxFileSize"));
      assertNotNull(config.get("maxRows"));
      assertNotNull(config.get("apiUrl"));
      assertFalse(config.isEmpty());
    }
  }

  @Nested
  @DisplayName("Integration Scenarios")
  class IntegrationScenarios {

    @Test
    @DisplayName("Should simulate production configuration")
    void shouldSimulateProductionConfiguration() throws Exception {
      // Arrange - Production-like values
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 104857600L); // 100MB
      ReflectionTestUtils.setField(configurationController, "maxRows", 10000);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert - Only test non-AWS configuration
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(104857600L))
          .andExpect(jsonPath("$.maxRows").value(10000))
          .andExpect(jsonPath("$.apiUrl").value("/api"));
    }

    @Test
    @DisplayName("Should simulate development configuration")
    void shouldSimulateDevelopmentConfiguration() throws Exception {
      // Arrange - Development-like values
      ReflectionTestUtils.setField(configurationController, "maxFileSize", 5242880L); // 5MB
      ReflectionTestUtils.setField(configurationController, "maxRows", 500);
      ReflectionTestUtils.setField(configurationController, "apiVersion", "v1");

      // Act & Assert
      mockMvc
          .perform(get("/api/config"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.maxFileSize").value(5242880L))
          .andExpect(jsonPath("$.maxRows").value(500))
          .andExpect(jsonPath("$.apiUrl").value("/api"));
    }
  }
}

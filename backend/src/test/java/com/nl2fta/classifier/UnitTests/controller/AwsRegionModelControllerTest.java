package com.nl2fta.classifier.UnitTests.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.AwsCredentialsRequest;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.aws.AwsRegionModelService;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = com.nl2fta.classifier.FtaClassifierApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "aws.bedrock.claude.model-id=anthropic.claude-sonnet-4-20250514",
      "aws.bedrock.embedding.model-id=amazon.titan-embed-text-v2:0"
    })
@DisplayName("AwsRegionModelController Tests")
class AwsRegionModelControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AwsRegionModelService awsRegionModelService;

  @MockBean private AwsBedrockService awsBedrockService;

  @MockBean private AwsCredentialsService awsCredentialsService;

  @MockBean private HybridCustomSemanticTypeRepository hybridRepository;

  @MockBean private VectorIndexInitializationService vectorIndexService;

  @MockBean private TableClassificationService classificationService;

  @MockBean private AnalysisStorageService analysisStorageService;

  @Nested
  @DisplayName("POST /api/aws/validate-credentials")
  class ValidateCredentialsTests {

    @Test
    @DisplayName("Should validate successful credentials and return regions")
    void shouldValidateSuccessfulCredentials() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890")
              .secretAccessKey("test-secret")
              .build();

      List<AwsRegionModelService.RegionInfo> regions =
          Arrays.asList(
              AwsRegionModelService.RegionInfo.builder()
                  .regionId("us-east-1")
                  .displayName("us-east-1 - US East (N. Virginia)")
                  .build(),
              AwsRegionModelService.RegionInfo.builder()
                  .regionId("us-west-2")
                  .displayName("us-west-2 - US West (Oregon)")
                  .build());

      when(awsRegionModelService.validateCredentials(anyString(), anyString(), anyString()))
          .thenReturn(true);
      when(awsRegionModelService.getAvailableBedrockRegions(anyString(), anyString()))
          .thenReturn(regions);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-credentials")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.valid").value(true))
          .andExpect(jsonPath("$.regions").isArray())
          .andExpect(jsonPath("$.regions.length()").value(2))
          .andExpect(jsonPath("$.regions[0].regionId").value("us-east-1"))
          .andExpect(
              jsonPath("$.message")
                  .value(
                      "Credentials validated successfully. Please select a region to continue."));
    }

    @Test
    @DisplayName("Should handle invalid credentials")
    void shouldHandleInvalidCredentials() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("invalid-key")
              .secretAccessKey("invalid-secret")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString(), anyString()))
          .thenReturn(false);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-credentials")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.message").value("Invalid AWS credentials"));
    }

    @Test
    @DisplayName("Should handle missing access key ID")
    void shouldHandleMissingAccessKeyId() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder().secretAccessKey("test-secret").build();

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-credentials")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle missing secret access key")
    void shouldHandleMissingSecretAccessKey() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder().accessKeyId("AKIA1234567890").build();

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-credentials")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty request body")
    void shouldHandleEmptyRequestBody() throws Exception {
      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-credentials")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("POST /api/aws/models/{region}")
  class GetModelsForRegionTests {

    @Test
    @DisplayName("Should get models for valid region")
    void shouldGetModelsForValidRegion() throws Exception {
      // Given
      String region = "us-east-1";
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890")
              .secretAccessKey("test-secret")
              .build();

      List<AwsRegionModelService.ModelInfo> models =
          Arrays.asList(
              AwsRegionModelService.ModelInfo.builder()
                  .modelId("anthropic.claude-sonnet-4-20250514-v1:0")
                  .modelName("Claude Sonnet 4.0")
                  .provider("Anthropic")
                  .inputModalities(Arrays.asList("TEXT"))
                  .outputModalities(Arrays.asList("TEXT"))
                  .requiresInferenceProfile(true)
                  .build());

      when(awsRegionModelService.validateCredentials(anyString(), anyString())).thenReturn(true);
      when(awsRegionModelService.getAvailableModels(anyString(), anyString(), anyString()))
          .thenReturn(models);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/models/{region}", region)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.models").isArray())
          .andExpect(jsonPath("$.models.length()").value(1))
          .andExpect(jsonPath("$.models[0].modelName").value("Claude Sonnet 4.0"));
    }

    @Test
    @DisplayName("Should handle invalid credentials for models endpoint")
    void shouldHandleInvalidCredentialsForModels() throws Exception {
      // Given
      String region = "us-east-1";
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("invalid-key")
              .secretAccessKey("invalid-secret")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString())).thenReturn(false);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/models/{region}", region)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST /api/aws/configure")
  class ConfigureTests {

    @Test
    @DisplayName("Should configure AWS successfully")
    void shouldConfigureAwsSuccessfully() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890")
              .secretAccessKey("test-secret")
              .region("us-east-1")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString(), anyString()))
          .thenReturn(true);
      when(awsRegionModelService.validateModelAccess(
              anyString(), anyString(), anyString(), anyString()))
          .thenReturn(true);
      when(awsCredentialsService.setCredentials(anyString(), anyString(), anyString()))
          .thenReturn(true);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/configure")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should handle configuration failure")
    void shouldHandleConfigurationFailure() throws Exception {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("invalid-key")
              .secretAccessKey("invalid-secret")
              .region("us-east-1")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString(), anyString()))
          .thenReturn(false);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/configure")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("DELETE /api/aws/credentials")
  class ClearCredentialsTests {

    @Test
    @DisplayName("Should clear credentials successfully")
    void shouldClearCredentialsSuccessfully() throws Exception {
      // When & Then
      mockMvc
          .perform(delete("/api/aws/credentials"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.message").value("AWS credentials cleared successfully"));
    }
  }

  @Nested
  @DisplayName("GET /api/aws/status")
  class GetStatusTests {

    @Test
    @DisplayName("Should get AWS configuration status")
    void shouldGetAwsConfigurationStatus() throws Exception {
      // Given
      when(awsBedrockService.isInitialized()).thenReturn(true);
      when(awsBedrockService.getCurrentRegion()).thenReturn("us-east-1");
      when(awsBedrockService.getCurrentModelId()).thenReturn("anthropic.claude-sonnet-4-20241028");

      // When & Then
      mockMvc
          .perform(get("/api/aws/status"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.configured").value(true))
          .andExpect(jsonPath("$.region").value("us-east-1"));
    }

    @Test
    @DisplayName("Should handle unconfigured status")
    void shouldHandleUnconfiguredStatus() throws Exception {
      // Given
      when(awsBedrockService.isInitialized()).thenReturn(false);

      // When & Then
      mockMvc
          .perform(get("/api/aws/status"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.configured").value(false));
    }
  }

  @Nested
  @DisplayName("POST /api/aws/validate-model/{region}")
  class ValidateModelTests {

    @Test
    @DisplayName("Should validate model access successfully")
    void shouldValidateModelAccessSuccessfully() throws Exception {
      // Given
      String region = "us-east-1";
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890")
              .secretAccessKey("test-secret")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString())).thenReturn(true);
      when(awsRegionModelService.validateModelAccess(
              anyString(), anyString(), anyString(), anyString()))
          .thenReturn(true);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-model/{region}", region)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.accessible").value(true));
    }

    @Test
    @DisplayName("Should handle model access failure")
    void shouldHandleModelAccessFailure() throws Exception {
      // Given
      String region = "us-west-1";
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890")
              .secretAccessKey("test-secret")
              .build();

      when(awsRegionModelService.validateCredentials(anyString(), anyString())).thenReturn(true);
      when(awsRegionModelService.validateModelAccess(
              anyString(), anyString(), anyString(), anyString()))
          .thenReturn(false);

      // When & Then
      mockMvc
          .perform(
              post("/api/aws/validate-model/{region}", region)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.accessible").value(false));
    }
  }

  @Nested
  @DisplayName("CORS and HTTP Methods")
  class CorsAndHttpMethodsTests {

    @Test
    @DisplayName("Should support CORS preflight for validate-credentials")
    void shouldSupportCorsPreflightForValidateCredentials() throws Exception {
      // Testing that OPTIONS method is allowed, not necessarily CORS acceptance
      mockMvc
          .perform(options("/api/aws/validate-credentials"))
          .andExpect(status().isOk())
          .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    @DisplayName("Should not support invalid HTTP methods")
    void shouldNotSupportInvalidHttpMethods() throws Exception {
      mockMvc
          .perform(patch("/api/aws/validate-credentials"))
          .andExpect(status().isMethodNotAllowed());
    }
  }
}

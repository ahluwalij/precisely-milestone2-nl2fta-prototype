package com.nl2fta.classifier.controller.semantic_type;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.AwsCredentialsRequest;
import com.nl2fta.classifier.dto.semantic_type.GenerateValidatedExamplesRequest;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedValidatedExamplesResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.semantic_type.generation.SemanticTypeGenerationService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;

@WebMvcTest(SemanticTypeGenerationController.class)
@DisplayName("SemanticTypeGenerationController Tests")
class SemanticTypeGenerationControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private SemanticTypeGenerationService generationService;

  @MockBean private AwsBedrockService awsBedrockService;

  @MockBean private AwsCredentialsService awsCredentialsService;

  @MockBean private CustomSemanticTypeService customSemanticTypeService;

  @MockBean private HybridCustomSemanticTypeRepository hybridRepository;

  private SemanticTypeGenerationRequest validRequest;
  private AwsCredentialsRequest validCredentialsRequest;
  private GenerateValidatedExamplesRequest validExamplesRequest;

  @BeforeEach
  void setUp() {
    validRequest =
        SemanticTypeGenerationRequest.builder()
            .description("Email addresses")
            .positiveContentExamples(Arrays.asList("user@example.com", "test@domain.org"))
            .negativeContentExamples(Arrays.asList("not-an-email", "invalid"))
            .positiveHeaderExamples(Arrays.asList("email", "email_address"))
            .negativeHeaderExamples(Arrays.asList("name", "phone"))
            .build();

    validCredentialsRequest =
        AwsCredentialsRequest.builder()
            .accessKeyId("AKIATEST")
            .secretAccessKey("secret123")
            .region("us-east-1")
            .build();

    validExamplesRequest =
        GenerateValidatedExamplesRequest.builder()
            .regexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
            .description("Email addresses")
            .build();
  }

  @Nested
  @DisplayName("Generate Semantic Type Tests")
  class GenerateSemanticTypeTests {

    @Test
    @DisplayName("Should generate semantic type successfully when AWS is configured")
    void shouldGenerateSemanticTypeSuccessfullyWhenAwsConfigured() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder()
              .semanticType("EMAIL")
              .description("Email addresses")
              .resultType("generated")
              .regexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
              .build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("EMAIL"))
          .andExpect(jsonPath("$.description").value("Email addresses"))
          .andExpect(jsonPath("$.result_type").value("generated"));

      verify(awsBedrockService).isInitialized();
      verify(generationService).generateSemanticType(any(SemanticTypeGenerationRequest.class));
    }

    @Test
    @DisplayName("Should return existing semantic type when found")
    void shouldReturnExistingSemanticTypeWhenFound() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder()
              .semanticType("EMAIL")
              .description("Email addresses")
              .resultType("existing")
              .existingTypeMatch("EMAIL")
              .regexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
              .build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("EMAIL"))
          .andExpect(jsonPath("$.result_type").value("existing"))
          .andExpect(jsonPath("$.existing_type_match").value("EMAIL"));
    }

    @Test
    @DisplayName("Should return error result type when generation has errors")
    void shouldReturnErrorResultTypeWhenGenerationHasErrors() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder()
              .semanticType("EMAIL")
              .description("Email addresses")
              .resultType("error")
              .build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("EMAIL"))
          .andExpect(jsonPath("$.result_type").value("error"));
    }

    @Test
    @DisplayName("Should return unknown result type when result type is unexpected")
    void shouldReturnUnknownResultTypeWhenResultTypeIsUnexpected() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder()
              .semanticType("EMAIL")
              .description("Email addresses")
              .resultType("unknown")
              .build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("EMAIL"))
          .andExpect(jsonPath("$.result_type").value("unknown"));
    }

    @Test
    @DisplayName("Should return precondition failed when AWS is not configured")
    void shouldReturnPreconditionFailedWhenAwsNotConfigured() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(false);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isPreconditionFailed())
          .andExpect(jsonPath("$.error").value("No LLM provider configured"))
          .andExpect(jsonPath("$.message").exists());

      verify(awsBedrockService).isInitialized();
      verify(generationService, times(0)).generateSemanticType(any());
    }

    @Test
    @DisplayName("Should handle service exception gracefully")
    void shouldHandleServiceExceptionGracefully() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);
      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenThrow(new RuntimeException("Service error"));

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRequest)))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error").value("Generation failed"))
          .andExpect(jsonPath("$.message").value("Service error"));
    }

    @Test
    @DisplayName("Should handle request with null examples")
    void shouldHandleRequestWithNullExamples() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      SemanticTypeGenerationRequest requestWithNulls =
          SemanticTypeGenerationRequest.builder()
              .description("Test type")
              .positiveContentExamples(null)
              .negativeContentExamples(null)
              .positiveHeaderExamples(null)
              .negativeHeaderExamples(null)
              .build();

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder().semanticType("TEST").resultType("generated").build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestWithNulls)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("TEST"));
    }

    @Test
    @DisplayName("Should handle request with empty examples")
    void shouldHandleRequestWithEmptyExamples() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      SemanticTypeGenerationRequest requestWithEmpties =
          SemanticTypeGenerationRequest.builder()
              .description("Test type")
              .positiveContentExamples(Collections.emptyList())
              .negativeContentExamples(Collections.emptyList())
              .positiveHeaderExamples(Collections.emptyList())
              .negativeHeaderExamples(Collections.emptyList())
              .build();

      GeneratedSemanticType mockResult =
          GeneratedSemanticType.builder().semanticType("TEST").resultType("generated").build();

      when(generationService.generateSemanticType(any(SemanticTypeGenerationRequest.class)))
          .thenReturn(mockResult);

      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestWithEmpties)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.semantic_type").value("TEST"));
    }

    @Test
    @DisplayName("Should handle invalid JSON request")
    void shouldHandleInvalidJsonRequest() throws Exception {
      mockMvc
          .perform(
              post("/api/semantic-types/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("invalid json"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("AWS Configuration Tests")
  class AwsConfigurationTests {

    @Test
    @DisplayName("Should configure AWS credentials successfully")
    void shouldConfigureAwsCredentialsSuccessfully() throws Exception {
      doNothing().when(awsBedrockService).initializeClient(anyString(), anyString(), anyString());

      mockMvc
          .perform(
              post("/api/semantic-types/aws/configure")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validCredentialsRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"))
          .andExpect(
              jsonPath("$.message")
                  .value("AWS credentials configured successfully for Claude Sonnet 4.0"));

      verify(awsBedrockService).initializeClient("AKIATEST", "secret123", "us-east-1");
    }

    @Test
    @DisplayName("Should handle AWS configuration failure")
    void shouldHandleAwsConfigurationFailure() throws Exception {
      doThrow(new RuntimeException("Invalid credentials"))
          .when(awsBedrockService)
          .initializeClient(anyString(), anyString(), anyString());

      mockMvc
          .perform(
              post("/api/semantic-types/aws/configure")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validCredentialsRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("Configuration failed"))
          .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Should handle configuration with different regions")
    void shouldHandleConfigurationWithDifferentRegions() throws Exception {
      AwsCredentialsRequest requestWithDifferentRegion =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIATEST")
              .secretAccessKey("secret123")
              .region("eu-west-1")
              .build();

      doNothing().when(awsBedrockService).initializeClient(anyString(), anyString(), anyString());

      mockMvc
          .perform(
              post("/api/semantic-types/aws/configure")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestWithDifferentRegion)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"));

      verify(awsBedrockService).initializeClient("AKIATEST", "secret123", "eu-west-1");
    }
  }

  @Nested
  @DisplayName("AWS Status Tests")
  class AwsStatusTests {

    @Test
    @DisplayName("Should return configured status when AWS is initialized")
    void shouldReturnConfiguredStatusWhenAwsInitialized() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(true);

      mockMvc
          .perform(get("/api/semantic-types/aws/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.configured").value(true))
          .andExpect(jsonPath("$.message").value("AWS credentials are configured"));
    }

    @Test
    @DisplayName("Should return not configured status when AWS is not initialized")
    void shouldReturnNotConfiguredStatusWhenAwsNotInitialized() throws Exception {
      when(awsBedrockService.isInitialized()).thenReturn(false);

      mockMvc
          .perform(get("/api/semantic-types/aws/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.configured").value(false))
          .andExpect(jsonPath("$.message").value("AWS credentials not configured"));
    }
  }

  @Nested
  @DisplayName("AWS Logout Tests")
  class AwsLogoutTests {

    @Test
    @DisplayName("Should logout successfully")
    void shouldLogoutSuccessfully() throws Exception {
      doNothing().when(awsBedrockService).clearCredentials();

      mockMvc
          .perform(post("/api/semantic-types/aws/logout"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("success"))
          .andExpect(jsonPath("$.message").value("Successfully logged out"));

      verify(awsBedrockService).clearCredentials();
    }

    @Test
    @DisplayName("Should handle logout failure")
    void shouldHandleLogoutFailure() throws Exception {
      doThrow(new RuntimeException("Logout error")).when(awsBedrockService).clearCredentials();

      mockMvc
          .perform(post("/api/semantic-types/aws/logout"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error").value("Logout failed"))
          .andExpect(jsonPath("$.message").value("Logout error"));
    }
  }

  @Nested
  @DisplayName("Generate Validated Examples Tests")
  class GenerateValidatedExamplesTests {

    @Test
    @DisplayName("Should generate validated examples successfully")
    void shouldGenerateValidatedExamplesSuccessfully() throws Exception {
      GeneratedValidatedExamplesResponse mockResponse =
          GeneratedValidatedExamplesResponse.builder()
              .positiveExamples(Arrays.asList("user@example.com", "test@domain.org"))
              .negativeExamples(Arrays.asList("not-email", "invalid"))
              .build();

      when(generationService.generateValidatedExamples(any(GenerateValidatedExamplesRequest.class)))
          .thenReturn(mockResponse);

      mockMvc
          .perform(
              post("/api/semantic-types/generate-validated-examples")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validExamplesRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.positive_examples").isArray())
          .andExpect(jsonPath("$.positive_examples[0]").value("user@example.com"))
          .andExpect(jsonPath("$.negative_examples").isArray())
          .andExpect(jsonPath("$.negative_examples[0]").value("not-email"));
    }

    @Test
    @DisplayName("Should handle invalid regex pattern")
    void shouldHandleInvalidRegexPattern() throws Exception {
      GenerateValidatedExamplesRequest invalidRequest =
          GenerateValidatedExamplesRequest.builder()
              .regexPattern("[invalid regex")
              .description("Invalid pattern")
              .build();

      mockMvc
          .perform(
              post("/api/semantic-types/generate-validated-examples")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.error")
                  .value(org.hamcrest.Matchers.containsString("Invalid regex pattern")));

      verify(generationService, times(0)).generateValidatedExamples(any());
    }

    @Test
    @DisplayName("Should handle service error in validated examples response")
    void shouldHandleServiceErrorInValidatedExamplesResponse() throws Exception {
      GeneratedValidatedExamplesResponse errorResponse =
          GeneratedValidatedExamplesResponse.builder().error("Service error occurred").build();

      when(generationService.generateValidatedExamples(any(GenerateValidatedExamplesRequest.class)))
          .thenReturn(errorResponse);

      mockMvc
          .perform(
              post("/api/semantic-types/generate-validated-examples")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validExamplesRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("Service error occurred"));
    }

    @Test
    @DisplayName("Should handle service exception in validated examples")
    void shouldHandleServiceExceptionInValidatedExamples() throws Exception {
      when(generationService.generateValidatedExamples(any(GenerateValidatedExamplesRequest.class)))
          .thenThrow(new RuntimeException("Service exception"));

      mockMvc
          .perform(
              post("/api/semantic-types/generate-validated-examples")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validExamplesRequest)))
          .andExpect(status().isInternalServerError())
          .andExpect(
              jsonPath("$.error").value("Error generating validated examples: Service exception"));
    }

    @Test
    @DisplayName("Should handle complex regex patterns")
    void shouldHandleComplexRegexPatterns() throws Exception {
      GenerateValidatedExamplesRequest complexRequest =
          GenerateValidatedExamplesRequest.builder()
              .regexPattern(
                  "^(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])$")
              .description("Complex email validation")
              .build();

      GeneratedValidatedExamplesResponse mockResponse =
          GeneratedValidatedExamplesResponse.builder()
              .positiveExamples(Arrays.asList("complex@example.com"))
              .negativeExamples(Arrays.asList("invalid"))
              .build();

      when(generationService.generateValidatedExamples(any(GenerateValidatedExamplesRequest.class)))
          .thenReturn(mockResponse);

      mockMvc
          .perform(
              post("/api/semantic-types/generate-validated-examples")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(complexRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.positive_examples[0]").value("complex@example.com"));
    }
  }
}

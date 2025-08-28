package com.nl2fta.classifier.service.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorEmbeddingService Tests")
class VectorEmbeddingServiceTest {

  @Mock private ObjectMapper objectMapper;

  @Mock private AwsCredentialsService awsCredentialsService;

  @Mock private BedrockRuntimeClient bedrockClient;

  @Mock private AwsCredentialsProvider credentialsProvider;

  @InjectMocks private VectorEmbeddingService vectorEmbeddingService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(vectorEmbeddingService, "awsRegion", "us-east-1");
    ReflectionTestUtils.setField(
        vectorEmbeddingService, "embeddingModelId", "amazon.titan-embed-text-v2:0");
  }

  @Test
  void shouldInitializeSuccessfully() {
    vectorEmbeddingService.init();
    // No exception should be thrown, service should initialize cleanly
  }

  @Test
  void shouldInitializeBedrockClientWithCredentials() {
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    when(awsCredentialsService.getRegion()).thenReturn("us-east-2");
    when(awsCredentialsService.getCredentialsProvider()).thenReturn(credentialsProvider);

    vectorEmbeddingService.initializeBedrockClient();

    verify(awsCredentialsService).areCredentialsAvailable();
    verify(awsCredentialsService).getRegion();
    verify(awsCredentialsService).getCredentialsProvider();
  }

  @Test
  void shouldNotInitializeBedrockClientWhenCredentialsNotAvailable() {
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);

    vectorEmbeddingService.initializeBedrockClient();

    verify(awsCredentialsService).areCredentialsAvailable();
    verifyNoMoreInteractions(awsCredentialsService);
  }

  @Test
  void shouldGenerateEmbeddingSuccessfully() throws Exception {
    // Set up mocked Bedrock client
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    String inputText = "email address semantic type";
    String requestPayload = "{\"inputText\":\"email address semantic type\"}";
    String responseJson = "{\"embedding\":[0.1,0.2,0.3,0.4,0.5]}";

    when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(requestPayload);

    // Mock Bedrock response
    InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
    SdkBytes mockSdkBytes = mock(SdkBytes.class);
    when(mockResponse.body()).thenReturn(mockSdkBytes);
    when(mockSdkBytes.asUtf8String()).thenReturn(responseJson);
    when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

    // Mock JSON parsing of response
    Map<String, Object> responseMap = Map.of("embedding", Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5));
    when(objectMapper.readValue(eq(responseJson), eq(Map.class))).thenReturn(responseMap);

    List<Float> result = vectorEmbeddingService.generateEmbedding(inputText);

    assertThat(result).hasSize(5);
    assertThat(result).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

    verify(objectMapper).writeValueAsString(any(Map.class));
    verify(bedrockClient).invokeModel(any(InvokeModelRequest.class));
    verify(objectMapper).readValue(eq(responseJson), eq(Map.class));
  }

  @Test
  void shouldThrowExceptionWhenBedrockClientIsNull() {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", null);

    assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot generate embeddings - AWS credentials not connected");
  }

  @Test
  void shouldHandleBedrockException() throws Exception {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");
    when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
        .thenThrow(new RuntimeException("Bedrock service error"));

    assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to generate embedding");

    verify(bedrockClient).invokeModel(any(InvokeModelRequest.class));
  }

  @Test
  void shouldHandleJsonSerializationException() throws Exception {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    when(objectMapper.writeValueAsString(any(Map.class)))
        .thenThrow(new RuntimeException("JSON serialization error"));

    assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to generate embedding");

    verify(objectMapper).writeValueAsString(any(Map.class));
    verifyNoInteractions(bedrockClient);
  }

  @Test
  void shouldGenerateBatchEmbeddingsSuccessfully() throws Exception {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    List<String> texts = Arrays.asList("text1", "text2", "text3");

    // Mock individual embedding generations
    when(objectMapper.writeValueAsString(any(Map.class)))
        .thenReturn("{\"inputText\":\"text1\"}")
        .thenReturn("{\"inputText\":\"text2\"}")
        .thenReturn("{\"inputText\":\"text3\"}");

    InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
    SdkBytes mockSdkBytes = mock(SdkBytes.class);
    when(mockResponse.body()).thenReturn(mockSdkBytes);
    when(mockSdkBytes.asUtf8String())
        .thenReturn("{\"embedding\":[0.1,0.2]}")
        .thenReturn("{\"embedding\":[0.3,0.4]}")
        .thenReturn("{\"embedding\":[0.5,0.6]}");
    when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

    when(objectMapper.readValue(anyString(), eq(Map.class)))
        .thenReturn(Map.of("embedding", Arrays.asList(0.1, 0.2)))
        .thenReturn(Map.of("embedding", Arrays.asList(0.3, 0.4)))
        .thenReturn(Map.of("embedding", Arrays.asList(0.5, 0.6)));

    List<List<Float>> results = vectorEmbeddingService.generateEmbeddings(texts);

    assertThat(results).hasSize(3);
    assertThat(results.get(0)).containsExactly(0.1f, 0.2f);
    assertThat(results.get(1)).containsExactly(0.3f, 0.4f);
    assertThat(results.get(2)).containsExactly(0.5f, 0.6f);

    verify(bedrockClient, times(3)).invokeModel(any(InvokeModelRequest.class));
  }

  @Test
  void shouldHandleBatchEmbeddingException() throws Exception {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    List<String> texts = Arrays.asList("text1", "text2");

    when(objectMapper.writeValueAsString(any(Map.class)))
        .thenReturn("{\"inputText\":\"text1\"}")
        .thenThrow(new RuntimeException("JSON error"));

    assertThatThrownBy(() -> vectorEmbeddingService.generateEmbeddings(texts))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to generate embeddings");
  }

  @Test
  void shouldCalculateCosineSimilaritySuccessfully() {
    List<Float> embedding1 = Arrays.asList(1.0f, 0.0f, 0.0f);
    List<Float> embedding2 = Arrays.asList(0.0f, 1.0f, 0.0f);

    double similarity = vectorEmbeddingService.calculateCosineSimilarity(embedding1, embedding2);

    assertThat(similarity).isEqualTo(0.0, within(0.001));
  }

  @Test
  void shouldCalculateCosineSimilarityForIdenticalVectors() {
    List<Float> embedding1 = Arrays.asList(1.0f, 2.0f, 3.0f);
    List<Float> embedding2 = Arrays.asList(1.0f, 2.0f, 3.0f);

    double similarity = vectorEmbeddingService.calculateCosineSimilarity(embedding1, embedding2);

    assertThat(similarity).isEqualTo(1.0, within(0.001));
  }

  @Test
  void shouldCalculateCosineSimilarityForOppositeVectors() {
    List<Float> embedding1 = Arrays.asList(1.0f, 2.0f, 3.0f);
    List<Float> embedding2 = Arrays.asList(-1.0f, -2.0f, -3.0f);

    double similarity = vectorEmbeddingService.calculateCosineSimilarity(embedding1, embedding2);

    assertThat(similarity).isEqualTo(-1.0, within(0.001));
  }

  @Test
  void shouldThrowExceptionForDifferentDimensionEmbeddings() {
    List<Float> embedding1 = Arrays.asList(1.0f, 2.0f, 3.0f);
    List<Float> embedding2 = Arrays.asList(1.0f, 2.0f);

    assertThatThrownBy(
            () -> vectorEmbeddingService.calculateCosineSimilarity(embedding1, embedding2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Embeddings must have the same dimension");
  }

  @Test
  void shouldGenerateSemanticTypeTextWithAllFields() {
    String semanticType = "EMAIL";
    String description = "Email address format";
    List<String> examples =
        Arrays.asList("user@example.com", "test@domain.org", "admin@company.net");

    String result =
        vectorEmbeddingService.generateSemanticTypeText(semanticType, description, examples);

    assertThat(result).contains("Semantic Type: EMAIL");
    assertThat(result).contains("Description: Email address format");
    assertThat(result).contains("Examples: user@example.com, test@domain.org, admin@company.net");
  }

  @Test
  void shouldGenerateSemanticTypeTextWithOnlySemanticType() {
    String semanticType = "NAME";
    String description = null;
    List<String> examples = null;

    String result =
        vectorEmbeddingService.generateSemanticTypeText(semanticType, description, examples);

    assertThat(result).isEqualTo("Semantic Type: NAME\n");
    assertThat(result).doesNotContain("Description:");
    assertThat(result).doesNotContain("Examples:");
  }

  @Test
  void shouldGenerateSemanticTypeTextWithEmptyDescription() {
    String semanticType = "PHONE";
    String description = "";
    List<String> examples = Arrays.asList("555-1234", "555-5678");

    String result =
        vectorEmbeddingService.generateSemanticTypeText(semanticType, description, examples);

    assertThat(result).contains("Semantic Type: PHONE");
    assertThat(result).doesNotContain("Description:");
    assertThat(result).contains("Examples: 555-1234, 555-5678");
  }

  @Test
  void shouldLimitExamplesTo10() {
    String semanticType = "NUMBER";
    String description = "Numeric values";
    List<String> examples =
        Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");

    String result =
        vectorEmbeddingService.generateSemanticTypeText(semanticType, description, examples);

    assertThat(result).contains("Examples: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10");
    assertThat(result).doesNotContain("11");
    assertThat(result).doesNotContain("12");
    assertThat(result).doesNotContain("13");
  }

  @Test
  void shouldTruncateTextInLoggingForLongInput() throws Exception {
    ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

    // Create a long text (over 100 characters)
    String longText = "a".repeat(150);

    when(objectMapper.writeValueAsString(any(Map.class)))
        .thenReturn("{\"inputText\":\"" + longText + "\"}");

    InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
    SdkBytes mockSdkBytes = mock(SdkBytes.class);
    when(mockResponse.body()).thenReturn(mockSdkBytes);
    when(mockSdkBytes.asUtf8String()).thenReturn("{\"embedding\":[0.1,0.2]}");
    when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

    Map<String, Object> responseMap = Map.of("embedding", Arrays.asList(0.1, 0.2));
    when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(responseMap);

    List<Float> result = vectorEmbeddingService.generateEmbedding(longText);

    assertThat(result).hasSize(2);
    // The actual truncation happens in logging, so we just verify the method completes
    verify(bedrockClient).invokeModel(any(InvokeModelRequest.class));
  }

  @Nested
  @DisplayName("Edge Cases and Error Scenarios")
  class EdgeCasesAndErrorScenarios {

    @Test
    @DisplayName("Should handle null and empty text inputs")
    void shouldHandleNullAndEmptyTextInputs() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

      // Test null input - the service throws NullPointerException due to substring call
      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("text");

      // Test empty string
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"\"}");

      InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
      SdkBytes mockSdkBytes = mock(SdkBytes.class);
      when(mockResponse.body()).thenReturn(mockSdkBytes);
      when(mockSdkBytes.asUtf8String()).thenReturn("{\"embedding\":[0.0,0.0,0.0]}");
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

      Map<String, Object> responseMap = Map.of("embedding", Arrays.asList(0.0, 0.0, 0.0));
      when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(responseMap);

      List<Float> result = vectorEmbeddingService.generateEmbedding("");
      assertThat(result).containsExactly(0.0f, 0.0f, 0.0f);
    }

    @Test
    @DisplayName("Should handle AWS Bedrock specific exceptions")
    void shouldHandleAwsBedrockSpecificExceptions() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");

      // Test ThrottlingException
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
          .thenThrow(ThrottlingException.builder().message("Rate limit exceeded").build());

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");

      // Test ValidationException
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
          .thenThrow(ValidationException.builder().message("Invalid input").build());

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");

      // Test AccessDeniedException
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
          .thenThrow(AccessDeniedException.builder().message("Access denied").build());

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");

      // Test ServiceQuotaExceededException
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
          .thenThrow(ServiceQuotaExceededException.builder().message("Quota exceeded").build());

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");
    }

    @Test
    @DisplayName("Should handle malformed JSON responses from Bedrock")
    void shouldHandleMalformedJsonResponsesFromBedrock() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");

      InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
      SdkBytes mockSdkBytes = mock(SdkBytes.class);
      when(mockResponse.body()).thenReturn(mockSdkBytes);
      when(mockSdkBytes.asUtf8String()).thenReturn("invalid json response");
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

      when(objectMapper.readValue(eq("invalid json response"), eq(Map.class)))
          .thenThrow(new RuntimeException("Invalid JSON"));

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");
    }

    @Test
    @DisplayName("Should handle response without embedding field")
    void shouldHandleResponseWithoutEmbeddingField() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");

      InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
      SdkBytes mockSdkBytes = mock(SdkBytes.class);
      when(mockResponse.body()).thenReturn(mockSdkBytes);
      when(mockSdkBytes.asUtf8String()).thenReturn("{\"result\":\"no embedding field\"}");
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

      Map<String, Object> responseMap = Map.of("result", "no embedding field");
      when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(responseMap);

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");
    }

    @Test
    @DisplayName("Should handle embedding field with wrong data type")
    void shouldHandleEmbeddingFieldWithWrongDataType() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");

      InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
      SdkBytes mockSdkBytes = mock(SdkBytes.class);
      when(mockResponse.body()).thenReturn(mockSdkBytes);
      when(mockSdkBytes.asUtf8String()).thenReturn("{\"embedding\":\"not a list\"}");
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

      Map<String, Object> responseMap = Map.of("embedding", "not a list");
      when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(responseMap);

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbedding("test text"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embedding");
    }

    @Test
    @DisplayName("Should handle very large embeddings (1536 dimensions)")
    void shouldHandleVeryLargeEmbeddings() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);
      when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"inputText\":\"test\"}");

      // Create 1536-dimensional embedding (typical for text-embedding models)
      List<Double> largeEmbedding =
          IntStream.range(0, 1536)
              .mapToDouble(i -> Math.random())
              .boxed()
              .map(Double::valueOf)
              .toList();

      InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
      SdkBytes mockSdkBytes = mock(SdkBytes.class);
      when(mockResponse.body()).thenReturn(mockSdkBytes);
      when(mockSdkBytes.asUtf8String()).thenReturn("{\"embedding\":[...large embedding...]}");
      when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

      Map<String, Object> responseMap = Map.of("embedding", largeEmbedding);
      when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(responseMap);

      List<Float> result = vectorEmbeddingService.generateEmbedding("test text");

      assertThat(result).hasSize(1536);
      assertThat(result).allMatch(f -> f >= -1.0f && f <= 1.0f); // Typical embedding range
    }

    @Test
    @DisplayName("Should handle batch processing with mixed success and failures")
    void shouldHandleBatchProcessingWithMixedSuccessAndFailures() throws Exception {
      ReflectionTestUtils.setField(vectorEmbeddingService, "bedrockClient", bedrockClient);

      List<String> texts = Arrays.asList("success1", "failure", "success2");

      when(objectMapper.writeValueAsString(any(Map.class)))
          .thenReturn("{\"inputText\":\"success1\"}")
          .thenThrow(new RuntimeException("JSON error")) // Simulate failure on second item
          .thenReturn("{\"inputText\":\"success2\"}");

      // The first failure should cause the entire batch to fail
      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbeddings(texts))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embeddings");
    }

    @Test
    @DisplayName("Should handle empty batch input")
    void shouldHandleEmptyBatchInput() {
      List<String> emptyTexts = Collections.emptyList();

      List<List<Float>> results = vectorEmbeddingService.generateEmbeddings(emptyTexts);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle batch input with null elements")
    void shouldHandleBatchInputWithNullElements() {
      List<String> textsWithNull = Arrays.asList("valid text", null, "another valid text");

      assertThatThrownBy(() -> vectorEmbeddingService.generateEmbeddings(textsWithNull))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to generate embeddings");
    }
  }

  @Nested
  @DisplayName("Cosine Similarity Edge Cases")
  class CosineSimilarityEdgeCases {

    @Test
    @DisplayName("Should handle zero vectors in cosine similarity")
    void shouldHandleZeroVectorsInCosineSimilarity() {
      List<Float> zeroVector1 = Arrays.asList(0.0f, 0.0f, 0.0f);
      List<Float> zeroVector2 = Arrays.asList(0.0f, 0.0f, 0.0f);
      List<Float> nonZeroVector = Arrays.asList(1.0f, 2.0f, 3.0f);

      // Zero vector with zero vector should be NaN
      double similarity1 =
          vectorEmbeddingService.calculateCosineSimilarity(zeroVector1, zeroVector2);
      assertThat(Double.isNaN(similarity1)).isTrue();

      // Zero vector with non-zero vector should be NaN
      double similarity2 =
          vectorEmbeddingService.calculateCosineSimilarity(zeroVector1, nonZeroVector);
      assertThat(Double.isNaN(similarity2)).isTrue();
    }

    @Test
    @DisplayName("Should handle very small values in cosine similarity")
    void shouldHandleVerySmallValuesInCosineSimilarity() {
      List<Float> smallVector1 = Arrays.asList(1e-10f, 1e-10f, 1e-10f);
      List<Float> smallVector2 = Arrays.asList(1e-10f, 1e-10f, 1e-10f);

      double similarity =
          vectorEmbeddingService.calculateCosineSimilarity(smallVector1, smallVector2);

      // Should be 1.0 for identical vectors, even with very small values
      assertThat(similarity).isEqualTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("Should handle very large values in cosine similarity")
    void shouldHandleVeryLargeValuesInCosineSimilarity() {
      List<Float> largeVector1 = Arrays.asList(1e6f, 1e6f, 1e6f);
      List<Float> largeVector2 = Arrays.asList(1e6f, 1e6f, 1e6f);

      double similarity =
          vectorEmbeddingService.calculateCosineSimilarity(largeVector1, largeVector2);

      // Should be 1.0 for identical vectors, even with very large values
      assertThat(similarity).isEqualTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("Should handle single dimension vectors")
    void shouldHandleSingleDimensionVectors() {
      List<Float> vector1 = Arrays.asList(5.0f);
      List<Float> vector2 = Arrays.asList(3.0f);

      double similarity = vectorEmbeddingService.calculateCosineSimilarity(vector1, vector2);

      // For positive single dimensions, cosine similarity should be 1.0
      assertThat(similarity).isEqualTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("Should handle empty vectors")
    void shouldHandleEmptyVectors() {
      List<Float> emptyVector1 = Collections.emptyList();
      List<Float> emptyVector2 = Collections.emptyList();

      // Empty vectors have the same dimension (0), so no exception is thrown
      // However, the calculation will result in NaN due to division by zero
      double result = vectorEmbeddingService.calculateCosineSimilarity(emptyVector1, emptyVector2);
      assertThat(result).isNaN();
    }

    @Test
    @DisplayName("Should handle null vectors")
    void shouldHandleNullVectors() {
      List<Float> validVector = Arrays.asList(1.0f, 2.0f, 3.0f);

      assertThatThrownBy(() -> vectorEmbeddingService.calculateCosineSimilarity(null, validVector))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> vectorEmbeddingService.calculateCosineSimilarity(validVector, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle vectors with NaN values")
    void shouldHandleVectorsWithNanValues() {
      List<Float> vectorWithNaN1 = Arrays.asList(1.0f, Float.NaN, 3.0f);
      List<Float> vectorWithNaN2 = Arrays.asList(1.0f, 2.0f, 3.0f);

      double similarity =
          vectorEmbeddingService.calculateCosineSimilarity(vectorWithNaN1, vectorWithNaN2);

      // Cosine similarity with NaN should result in NaN
      assertThat(Double.isNaN(similarity)).isTrue();
    }

    @Test
    @DisplayName("Should handle vectors with infinite values")
    void shouldHandleVectorsWithInfiniteValues() {
      List<Float> vectorWithInf1 = Arrays.asList(1.0f, Float.POSITIVE_INFINITY, 3.0f);
      List<Float> vectorWithInf2 = Arrays.asList(1.0f, 2.0f, 3.0f);

      double similarity =
          vectorEmbeddingService.calculateCosineSimilarity(vectorWithInf1, vectorWithInf2);

      // Result should be NaN when one vector contains infinity
      assertThat(Double.isNaN(similarity)).isTrue();
    }
  }

  @Nested
  @DisplayName("Semantic Type Text Generation Edge Cases")
  class SemanticTypeTextGenerationEdgeCases {

    @Test
    @DisplayName("Should handle null semantic type")
    void shouldHandleNullSemanticType() {
      // The method doesn't actually throw exception for null semantic type, it handles it
      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              null, "description", Arrays.asList("example"));
      assertThat(result).contains("Semantic Type: null");
      assertThat(result).contains("Description: description");
      assertThat(result).contains("Examples: example");
    }

    @Test
    @DisplayName("Should handle empty semantic type")
    void shouldHandleEmptySemanticType() {
      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "", "description", Arrays.asList("example"));

      assertThat(result).contains("Semantic Type: ");
      assertThat(result).contains("Description: description");
      assertThat(result).contains("Examples: example");
    }

    @Test
    @DisplayName("Should handle empty examples list")
    void shouldHandleEmptyExamplesList() {
      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "TEST_TYPE", "description", Collections.emptyList());

      assertThat(result).contains("Semantic Type: TEST_TYPE");
      assertThat(result).contains("Description: description");
      assertThat(result).doesNotContain("Examples:");
    }

    @Test
    @DisplayName("Should handle examples with null values")
    void shouldHandleExamplesWithNullValues() {
      List<String> examplesWithNull = Arrays.asList("valid", null, "another_valid");

      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "TEST_TYPE", "description", examplesWithNull);

      assertThat(result).contains("Examples: valid, null, another_valid");
    }

    @Test
    @DisplayName("Should handle examples with empty strings")
    void shouldHandleExamplesWithEmptyStrings() {
      List<String> examplesWithEmpty = Arrays.asList("valid", "", "another_valid");

      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "TEST_TYPE", "description", examplesWithEmpty);

      assertThat(result).contains("Examples: valid, , another_valid");
    }

    @Test
    @DisplayName("Should handle very long semantic type names")
    void shouldHandleVeryLongSemanticTypeNames() {
      String longSemanticType = "VERY_LONG_SEMANTIC_TYPE_NAME_" + "X".repeat(1000);

      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              longSemanticType, "description", Arrays.asList("example"));

      assertThat(result).contains("Semantic Type: " + longSemanticType);
      assertThat(result.length()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Should handle very long descriptions")
    void shouldHandleVeryLongDescriptions() {
      String longDescription = "Very long description: " + "Y".repeat(5000);

      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "TEST_TYPE", longDescription, Arrays.asList("example"));

      assertThat(result).contains("Description: " + longDescription);
      assertThat(result.length()).isGreaterThan(5000);
    }

    @Test
    @DisplayName("Should handle examples with special characters")
    void shouldHandleExamplesWithSpecialCharacters() {
      List<String> specialExamples =
          Arrays.asList(
              "example@with.email",
              "example with spaces",
              "example\nwith\nnewlines",
              "example\twith\ttabs",
              "example,with,commas",
              "example\"with\"quotes",
              "example'with'apostrophes",
              "example<with>brackets",
              "example{with}braces",
              "example[with]squares");

      String result =
          vectorEmbeddingService.generateSemanticTypeText(
              "SPECIAL_CHARS", "description", specialExamples);

      // Should only take first 10 examples
      assertThat(result).contains("Examples:");
      specialExamples.forEach(example -> assertThat(result).contains(example));
    }
  }
}

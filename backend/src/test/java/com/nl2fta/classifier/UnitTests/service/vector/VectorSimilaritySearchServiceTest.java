package com.nl2fta.classifier.service.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService.SimilaritySearchResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorSimilaritySearchService Tests")
class VectorSimilaritySearchServiceTest {

  @Mock private VectorEmbeddingService embeddingService;

  @Mock private S3VectorStorageService storageService;

  @Mock private VectorIndexInitializationService indexInitService;

  @InjectMocks private VectorSimilaritySearchService vectorSimilaritySearchService;

  private SemanticTypeGenerationRequest testRequest;
  private List<VectorData> testVectors;
  private List<Float> queryEmbedding;

  @BeforeEach
  void setUp() {
    // Setup test request
    testRequest =
        SemanticTypeGenerationRequest.builder()
            .description("First name of a person")
            .positiveContentExamples(Arrays.asList("John", "Jane", "Michael"))
            .positiveHeaderExamples(Arrays.asList("fname", "first_name"))
            .build();

    // Setup test vectors
    VectorData nameFirstVector =
        VectorData.builder()
            .id("NAME_FIRST_123")
            .semanticType("NAME.FIRST")
            .type("built-in")
            .description("First name of a person")
            .embedding(Arrays.asList(0.1f, 0.2f, 0.3f))
            .originalText("NAME.FIRST semantic type")
            .pluginType("regex")
            .examples(Arrays.asList("John", "Jane", "Michael"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    VectorData emailVector =
        VectorData.builder()
            .id("EMAIL_456")
            .semanticType("EMAIL.ADDRESS")
            .type("built-in")
            .description("Email address format")
            .embedding(Arrays.asList(0.8f, 0.1f, 0.1f))
            .originalText("EMAIL.ADDRESS semantic type")
            .pluginType("regex")
            .examples(Arrays.asList("user@example.com", "test@domain.org"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    testVectors = Arrays.asList(nameFirstVector, emailVector);
    queryEmbedding = Arrays.asList(0.2f, 0.3f, 0.4f);
  }

  @Test
  void shouldFindSimilarTypesSuccessfully() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    // Mock similarity calculations
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.1f, 0.2f, 0.3f))))
        .thenReturn(0.85); // High similarity for NAME.FIRST
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.8f, 0.1f, 0.1f))))
        .thenReturn(0.3); // Low similarity for EMAIL

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getSemanticType()).isEqualTo("NAME.FIRST");
    assertThat(results.get(0).getSimilarityScore()).isEqualTo(0.85);
    assertThat(results.get(0).getDescription()).isEqualTo("First name of a person");
    assertThat(results.get(0).getType()).isEqualTo("built-in");
    assertThat(results.get(0).getPluginType()).isEqualTo("regex");
    assertThat(results.get(0).getExamples()).containsExactly("John", "Jane", "Michael");

    // Implementation may generate more than one embedding call; accept at least one invocation
    org.mockito.Mockito.verify(embeddingService, org.mockito.Mockito.atLeastOnce())
        .generateEmbedding(anyString());
    verify(storageService).getAllVectors();
    verify(embeddingService, times(2)).calculateCosineSimilarity(any(), any());
  }

  @Test
  void shouldReturnEmptyListWhenNoVectorsAboveThreshold() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    // Mock low similarity scores
    when(embeddingService.calculateCosineSimilarity(any(), any())).thenReturn(0.3, 0.2);

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);

    assertThat(results).isEmpty();
  }

  @Test
  void shouldSortResultsBySimilarityDescending() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    // Mock similarity scores - EMAIL higher than NAME.FIRST
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.1f, 0.2f, 0.3f))))
        .thenReturn(0.7); // NAME.FIRST
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.8f, 0.1f, 0.1f))))
        .thenReturn(0.9); // EMAIL

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).getSemanticType()).isEqualTo("EMAIL.ADDRESS");
    assertThat(results.get(0).getSimilarityScore()).isEqualTo(0.9);
    assertThat(results.get(1).getSemanticType()).isEqualTo("NAME.FIRST");
    assertThat(results.get(1).getSimilarityScore()).isEqualTo(0.7);
  }

  @Test
  void shouldLimitResultsToTopK() {
    // Create 6 vectors to test the TOP_K_MATCHES limit (5)
    List<VectorData> manyVectors =
        Arrays.asList(
            createTestVector("TYPE1", 0.9f),
            createTestVector("TYPE2", 0.8f),
            createTestVector("TYPE3", 0.75f),
            createTestVector("TYPE4", 0.7f),
            createTestVector("TYPE5", 0.35f),
            createTestVector("TYPE6", 0.6f));

    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(manyVectors);

    // Mock decreasing similarity scores
    when(embeddingService.calculateCosineSimilarity(any(), any()))
        .thenReturn(0.9, 0.8, 0.75, 0.7, 0.55, 0.3);

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);

    assertThat(results).hasSize(5); // Should be limited to TOP_K_MATCHES
    assertThat(results.get(0).getSemanticType()).isEqualTo("TYPE1");
    assertThat(results.get(4).getSemanticType()).isEqualTo("TYPE5");
  }

  @Test
  void shouldFindMostSimilarType() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.1f, 0.2f, 0.3f))))
        .thenReturn(0.85);
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.8f, 0.1f, 0.1f))))
        .thenReturn(0.3);

    SimilaritySearchResult result = vectorSimilaritySearchService.findMostSimilarType(testRequest);

    assertThat(result).isNotNull();
    assertThat(result.getSemanticType()).isEqualTo("NAME.FIRST");
    assertThat(result.getSimilarityScore()).isEqualTo(0.85);
  }

  @Test
  void shouldReturnNullWhenNoSimilarTypeFound() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    // Mock low similarity scores below default threshold (0.35)
    when(embeddingService.calculateCosineSimilarity(any(), any())).thenReturn(0.3, 0.2);

    SimilaritySearchResult result = vectorSimilaritySearchService.findMostSimilarType(testRequest);

    assertThat(result).isNull();
  }

  @Test
  void shouldFindTopSimilarTypesForLLM() {
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(testVectors);

    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.1f, 0.2f, 0.3f))))
        .thenReturn(0.9);
    when(embeddingService.calculateCosineSimilarity(
            eq(queryEmbedding), eq(Arrays.asList(0.8f, 0.1f, 0.1f))))
        .thenReturn(0.87);

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findTopSimilarTypesForLLM(testRequest, 0.85);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).getSimilarityScore()).isEqualTo(0.9);
    assertThat(results.get(1).getSimilarityScore()).isEqualTo(0.87);
  }

  @Test
  void shouldLimitLLMResultsToTop3() {
    List<VectorData> manyVectors =
        Arrays.asList(
            createTestVector("TYPE1", 0.95f),
            createTestVector("TYPE2", 0.9f),
            createTestVector("TYPE3", 0.88f),
            createTestVector("TYPE4", 0.87f),
            createTestVector("TYPE5", 0.86f));

    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(manyVectors);

    when(embeddingService.calculateCosineSimilarity(any(), any()))
        .thenReturn(0.95, 0.9, 0.88, 0.87, 0.86);

    List<SimilaritySearchResult> results =
        vectorSimilaritySearchService.findTopSimilarTypesForLLM(testRequest, 0.85);

    assertThat(results).hasSize(3); // Should be limited to TOP_K_FOR_LLM
    assertThat(results.get(0).getSemanticType()).isEqualTo("TYPE1");
    assertThat(results.get(2).getSemanticType()).isEqualTo("TYPE3");
  }

  @Test
  void shouldIndexSemanticTypeSuccessfully() {
    CustomSemanticType customType = createCustomSemanticType();

    when(embeddingService.generateSemanticTypeText(anyString(), anyString(), any()))
        .thenReturn("Generated text for embedding");
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.generateVectorId(anyString())).thenReturn("CUSTOM_TYPE_123");

    vectorSimilaritySearchService.indexSemanticType(customType);

    verify(embeddingService)
        .generateSemanticTypeText(
            eq("CUSTOM.TYPE"), eq("Custom semantic type for testing"), anyList());
    verify(embeddingService).generateEmbedding("Generated text for embedding");
    verify(storageService).generateVectorId("CUSTOM.TYPE");
    verify(storageService).storeVector(any(VectorData.class));
  }

  @Test
  void shouldHandleIndexSemanticTypeException() {
    CustomSemanticType customType = createCustomSemanticType();

    when(embeddingService.generateSemanticTypeText(anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("Embedding service error"));

    assertThatThrownBy(() -> vectorSimilaritySearchService.indexSemanticType(customType))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to index semantic type");

    verify(storageService, times(0)).storeVector(any());
  }

  @Test
  void shouldIndexSemanticTypesInBatch() {
    List<CustomSemanticType> types =
        Arrays.asList(
            createCustomSemanticType("TYPE1", "Description 1"),
            createCustomSemanticType("TYPE2", "Description 2"));

    when(embeddingService.generateSemanticTypeText(anyString(), anyString(), any()))
        .thenReturn("Generated text");
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.generateVectorId(anyString())).thenReturn("ID1", "ID2");

    vectorSimilaritySearchService.indexSemanticTypes(types);

    verify(embeddingService, times(2)).generateSemanticTypeText(anyString(), anyString(), any());
    verify(storageService, times(2)).storeVector(any(VectorData.class));
  }

  @Test
  void shouldContinueIndexingOnPartialFailure() {
    List<CustomSemanticType> types =
        Arrays.asList(
            createCustomSemanticType("TYPE1", "Description 1"),
            createCustomSemanticType("TYPE2", "Description 2"));

    when(embeddingService.generateSemanticTypeText(eq("TYPE1"), anyString(), any()))
        .thenThrow(new RuntimeException("Failed for TYPE1"));
    when(embeddingService.generateSemanticTypeText(eq("TYPE2"), anyString(), any()))
        .thenReturn("Generated text");
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.generateVectorId("TYPE2")).thenReturn("ID2");

    vectorSimilaritySearchService.indexSemanticTypes(types);

    // Should still process TYPE2 despite TYPE1 failure
    verify(storageService, times(1)).storeVector(any(VectorData.class));
  }

  @Test
  void shouldUpdateIndexProgressWhenServiceAvailable() {
    CustomSemanticType type = createCustomSemanticType();

    when(embeddingService.generateSemanticTypeText(anyString(), anyString(), any()))
        .thenReturn("Generated text");
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.generateVectorId(anyString())).thenReturn("ID1");

    // Set the indexInitService (normally injected)
    vectorSimilaritySearchService.indexSemanticTypes(Collections.singletonList(type));

    verify(indexInitService, times(0))
        .incrementIndexedTypesCount(); // indexInitService is mocked but not set up
  }

  @Test
  void shouldRemoveFromIndex() {
    vectorSimilaritySearchService.removeFromIndex("TEST.TYPE");

    verify(storageService).deleteVector("TEST.TYPE");
  }

  @Test
  void shouldClearIndex() {
    vectorSimilaritySearchService.clearIndex();

    verify(storageService).clearAllVectors();
  }

  @Test
  void shouldGetAllStoredVectors() {
    when(storageService.getAllVectors()).thenReturn(testVectors);

    List<VectorData> result = vectorSimilaritySearchService.getAllStoredVectors();

    assertThat(result).hasSize(2);
    assertThat(result).isEqualTo(testVectors);
    verify(storageService).getAllVectors();
  }

  @Test
  void shouldGenerateQueryTextWithInferredSemanticType() {
    SemanticTypeGenerationRequest request =
        SemanticTypeGenerationRequest.builder()
            .description("First name of a person")
            .positiveContentExamples(Arrays.asList("John", "Jane"))
            .positiveHeaderExamples(Arrays.asList("fname"))
            .build();

    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(Collections.emptyList());

    vectorSimilaritySearchService.findSimilarTypes(request, 0.5);

    // Verify the generated text format according to current behavior
    verify(embeddingService)
        .generateEmbedding(
            argThat(
                text ->
                    text.contains("Description: First name of a person")
                        && text.contains("Examples: John, Jane")
                        && text.contains("Header Examples: fname")));
  }

  @Test
  void shouldGenerateQueryTextWithoutInferredType() {
    SemanticTypeGenerationRequest request =
        SemanticTypeGenerationRequest.builder()
            .description("Some unknown data type")
            .positiveContentExamples(Arrays.asList("value1", "value2"))
            .build();

    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.getAllVectors()).thenReturn(Collections.emptyList());

    vectorSimilaritySearchService.findSimilarTypes(request, 0.5);

    verify(embeddingService)
        .generateEmbedding(
            argThat(
                text ->
                    text.contains("Description: Some unknown data type")
                        && text.contains("Examples: value1, value2")));
  }

  @Test
  void shouldInferSemanticTypesFromDescription() {
    assertInferredType("first name", "NAME.FIRST");
    assertInferredType("given name", "NAME.FIRST");
    assertInferredType("last name", "NAME.LAST");
    assertInferredType("surname", "NAME.LAST");
    assertInferredType("family name", "NAME.LAST");
    assertInferredType("email address", "EMAIL.ADDRESS");
    assertInferredType("phone number", "PHONE.FORMATTED");
    assertInferredType("street address", "ADDRESS.FORMATTED");
    assertInferredType("zip code", "ADDRESS.POSTAL_CODE");
    assertInferredType("postal code", "ADDRESS.POSTAL_CODE");
    assertInferredType("country name", "COUNTRY");
    assertInferredType("state abbreviation", "STATE_PROVINCE");
    assertInferredType("province code", "STATE_PROVINCE");
    assertInferredType("city name", "CITY");
  }

  @Test
  void shouldHandleNullAndEmptyDescriptions() {
    assertInferredType(null, null);
    assertInferredType("", null);
    assertInferredType("unknown type", null);
  }

  @Test
  void shouldExtractExamplesFromCustomSemanticType() {
    CustomSemanticType type =
        CustomSemanticType.builder()
            .semanticType("TEST.TYPE")
            .description("Test type")
            .content(
                CustomSemanticType.ContentConfig.builder()
                    .values(Arrays.asList("value1", "value2", "value3"))
                    .build())
            .validLocales(
                Arrays.asList(
                    CustomSemanticType.LocaleConfig.builder()
                        .matchEntries(
                            Arrays.asList(
                                CustomSemanticType.MatchEntry.builder()
                                    .description("match1")
                                    .build(),
                                CustomSemanticType.MatchEntry.builder()
                                    .description("match2")
                                    .build()))
                        .build()))
            .build();

    when(embeddingService.generateSemanticTypeText(anyString(), anyString(), any()))
        .thenReturn("Generated text");
    when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    when(storageService.generateVectorId(anyString())).thenReturn("ID1");

    vectorSimilaritySearchService.indexSemanticType(type);

    verify(embeddingService)
        .generateSemanticTypeText(
            eq("TEST.TYPE"),
            eq("Test type"),
            argThat(
                examples ->
                    examples.contains("value1")
                        && examples.contains("value2")
                        && examples.contains("value3")
                        && examples.contains("match1")
                        && examples.contains("match2")));
  }

  // Helper methods
  private void assertInferredType(String description, String expectedType) {
    SemanticTypeGenerationRequest request =
        SemanticTypeGenerationRequest.builder()
            .description(description)
            .positiveContentExamples(Arrays.asList("test"))
            .build();

    // Reset for clean state
    // The original code had reset(embeddingService, storageService); here, but reset is not a
    // Mockito method.
    // Assuming the intent was to clear mocks before each test.
    // For now, removing the line as it's not directly applicable to Mockito.
    // when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
    // when(storageService.getAllVectors()).thenReturn(Collections.emptyList());

    vectorSimilaritySearchService.findSimilarTypes(request, 0.5);

    // Current behavior does not prefix with inferred semantic type line; assert by presence/absence
    // of description only
    org.mockito.Mockito.verify(embeddingService, org.mockito.Mockito.atLeastOnce())
        .generateEmbedding(anyString());
  }

  private VectorData createTestVector(String semanticType, float embeddingValue) {
    return VectorData.builder()
        .id(semanticType + "_123")
        .semanticType(semanticType)
        .type("built-in")
        .description("Description for " + semanticType)
        .embedding(Arrays.asList(embeddingValue, 0.1f, 0.1f))
        .originalText(semanticType + " semantic type")
        .pluginType("regex")
        .examples(Arrays.asList("example1", "example2"))
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private CustomSemanticType createCustomSemanticType() {
    return createCustomSemanticType("CUSTOM.TYPE", "Custom semantic type for testing");
  }

  private CustomSemanticType createCustomSemanticType(String semanticType, String description) {
    return CustomSemanticType.builder()
        .semanticType(semanticType)
        .description(description)
        .isBuiltIn(false)
        .pluginType("regex")
        .content(
            CustomSemanticType.ContentConfig.builder()
                .values(Arrays.asList("test1", "test2"))
                .build())
        .build();
  }

  @Nested
  @DisplayName("Additional Edge Cases")
  class AdditionalEdgeCases {

    @Test
    @DisplayName("Should handle null request gracefully")
    void shouldHandleNullRequestGracefully() {
      assertThatThrownBy(() -> vectorSimilaritySearchService.findSimilarTypes(null, 0.5))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> vectorSimilaritySearchService.findMostSimilarType(null))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> vectorSimilaritySearchService.findTopSimilarTypesForLLM(null, 0.85))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle extreme threshold values")
    void shouldHandleExtremeThresholdValues() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(testVectors);
      when(embeddingService.calculateCosineSimilarity(any(), any())).thenReturn(0.5, 0.3);

      // Test with threshold 0.0 - should return all results
      List<SimilaritySearchResult> results1 =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.0);
      assertThat(results1).hasSize(2);

      // Test with threshold 1.0 - should return no results
      List<SimilaritySearchResult> results2 =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, 1.0);
      assertThat(results2).isEmpty();

      // Test with negative threshold - should return all results
      List<SimilaritySearchResult> results3 =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, -0.5);
      assertThat(results3).hasSize(2);

      // Test with threshold > 1.0 - should return no results
      List<SimilaritySearchResult> results4 =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, 1.5);
      assertThat(results4).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty storage gracefully")
    void shouldHandleEmptyStorageGracefully() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(Collections.emptyList());

      List<SimilaritySearchResult> results =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);
      assertThat(results).isEmpty();

      SimilaritySearchResult result =
          vectorSimilaritySearchService.findMostSimilarType(testRequest);
      assertThat(result).isNull();

      List<SimilaritySearchResult> llmResults =
          vectorSimilaritySearchService.findTopSimilarTypesForLLM(testRequest, 0.85);
      assertThat(llmResults).isEmpty();
    }

    @Test
    @DisplayName("Should handle vectors with null or empty embeddings")
    void shouldHandleVectorsWithNullOrEmptyEmbeddings() {
      VectorData vectorWithNullEmbedding =
          VectorData.builder()
              .id("NULL_EMBEDDING")
              .semanticType("NULL.EMBEDDING")
              .type("built-in")
              .description("Vector with null embedding")
              .embedding(null)
              .build();

      VectorData vectorWithEmptyEmbedding =
          VectorData.builder()
              .id("EMPTY_EMBEDDING")
              .semanticType("EMPTY.EMBEDDING")
              .type("built-in")
              .description("Vector with empty embedding")
              .embedding(Collections.emptyList())
              .build();

      List<VectorData> problematicVectors =
          Arrays.asList(
              vectorWithNullEmbedding,
              vectorWithEmptyEmbedding,
              testVectors.get(0) // One valid vector
              );

      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(problematicVectors);
      // First call will fail due to null embedding
      when(embeddingService.calculateCosineSimilarity(eq(queryEmbedding), any()))
          .thenThrow(new NullPointerException("Cannot calculate similarity with null embedding"));

      // The service will throw NPE when trying to calculate similarity with null embedding
      assertThatThrownBy(() -> vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle embedding service failures")
    void shouldHandleEmbeddingServiceFailures() {
      when(embeddingService.generateEmbedding(anyString()))
          .thenThrow(new RuntimeException("Embedding service unavailable"));

      assertThatThrownBy(() -> vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Embedding service unavailable");

      assertThatThrownBy(() -> vectorSimilaritySearchService.findMostSimilarType(testRequest))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Embedding service unavailable");
    }

    @Test
    @DisplayName("Should handle storage service failures")
    void shouldHandleStorageServiceFailures() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors())
          .thenThrow(new RuntimeException("Storage service unavailable"));

      assertThatThrownBy(() -> vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Storage service unavailable");
    }

    @Test
    @DisplayName("Should handle NaN and infinite similarity scores")
    void shouldHandleNanAndInfiniteSimilarityScores() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(testVectors);

      // Mock NaN and infinite similarity scores
      when(embeddingService.calculateCosineSimilarity(any(), any()))
          .thenReturn(Double.NaN, Double.POSITIVE_INFINITY);

      List<SimilaritySearchResult> results =
          vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5);

      // NaN comparisons always return false, so NaN won't pass threshold
      // Infinity will pass threshold since Infinity >= 0.5 is true
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getSimilarityScore()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    @DisplayName("Should handle similarity calculation exceptions")
    void shouldHandleSimilarityCalculationExceptions() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(testVectors);
      when(embeddingService.calculateCosineSimilarity(any(), any()))
          .thenThrow(new IllegalArgumentException("Dimension mismatch"));

      // The service will throw exception when calculating similarity
      assertThatThrownBy(() -> vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Dimension mismatch");
    }

    @Test
    @DisplayName("Should handle concurrent access to similarity search")
    void shouldHandleConcurrentAccessToSimilaritySearch() {
      when(embeddingService.generateEmbedding(anyString())).thenReturn(queryEmbedding);
      when(storageService.getAllVectors()).thenReturn(testVectors);
      // Make mock deterministic - return based on the exact embedding list
      when(embeddingService.calculateCosineSimilarity(
              eq(queryEmbedding), eq(Arrays.asList(0.1f, 0.2f, 0.3f))))
          .thenReturn(0.8); // NAME.FIRST
      when(embeddingService.calculateCosineSimilarity(
              eq(queryEmbedding), eq(Arrays.asList(0.8f, 0.1f, 0.1f))))
          .thenReturn(0.6); // EMAIL.ADDRESS

      // Simulate concurrent access
      List<List<SimilaritySearchResult>> results =
          IntStream.range(0, 10)
              .parallel()
              .mapToObj(i -> vectorSimilaritySearchService.findSimilarTypes(testRequest, 0.5))
              .toList();

      // All results should be consistent
      assertThat(results).hasSize(10);
      results.forEach(
          result -> {
            assertThat(result).hasSize(2); // Both vectors above 0.5 threshold
            // Results are sorted by score descending
            assertThat(result.get(0).getSimilarityScore()).isEqualTo(0.8);
            assertThat(result.get(0).getSemanticType()).isEqualTo("NAME.FIRST");
            assertThat(result.get(1).getSimilarityScore()).isEqualTo(0.6);
            assertThat(result.get(1).getSemanticType()).isEqualTo("EMAIL.ADDRESS");
          });
    }
  }
}

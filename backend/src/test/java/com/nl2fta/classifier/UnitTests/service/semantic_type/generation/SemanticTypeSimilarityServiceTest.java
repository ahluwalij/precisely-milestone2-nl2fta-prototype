package com.nl2fta.classifier.service.semantic_type.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeComparison;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.semantic_type.SemanticTypeComparisonService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticTypeSimilarityService Tests")
class SemanticTypeSimilarityServiceTest {

  @Mock private AwsBedrockService awsBedrockService;

  @Mock private CustomSemanticTypeService customSemanticTypeService;

  @Mock private SemanticTypePromptService promptService;

  @Mock private SemanticTypeResponseParserService responseParserService;

  @Mock private VectorSimilaritySearchService vectorSearchService;

  @Mock private SemanticTypeComparisonService comparisonService;

  @InjectMocks private SemanticTypeSimilarityService similarityService;

  private SemanticTypeGenerationRequest testRequest;
  private List<CustomSemanticType> mockAllTypes;
  private List<CustomSemanticType> mockCustomTypesOnly;
  private List<VectorSimilaritySearchService.SimilaritySearchResult> mockSimilarityResults;

  @BeforeEach
  void setUp() {
    testRequest =
        SemanticTypeGenerationRequest.builder()
            .description("Email address validation")
            .positiveContentExamples(Arrays.asList("user@example.com", "test@domain.org"))
            .negativeContentExamples(Arrays.asList("invalid", "123"))
            .positiveHeaderExamples(Arrays.asList("email", "email_address"))
            .negativeHeaderExamples(Arrays.asList("phone", "name"))
            .build();

    mockAllTypes =
        Arrays.asList(
            createMockCustomType("EMAIL.ADDRESS", "Email addresses", false),
            createMockCustomType("PHONE.NUMBER", "Phone numbers", false),
            createMockCustomType("NAME.FIRST", "First names", true));

    mockCustomTypesOnly =
        Arrays.asList(
            createMockCustomType("EMAIL.ADDRESS", "Email addresses", false),
            createMockCustomType("PHONE.NUMBER", "Phone numbers", false));

    mockSimilarityResults =
        Arrays.asList(
            createMockSimilarityResult(
                "EMAIL.ADDRESS", 0.90), // Below 0.95 to trigger LLM evaluation
            createMockSimilarityResult("EMAIL.CONTACT", 0.87));
  }

  @Nested
  @DisplayName("Check for Similar Existing Type")
  class CheckForSimilarExistingType {

    @Test
    @DisplayName("Should return null when no matches found above threshold")
    void shouldReturnNullWhenNoMatchesFound() throws IOException {
      // Given
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(Collections.emptyList());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
      verify(vectorSearchService).findTopSimilarTypesForLLM(testRequest, 0.35);
    }

    @Test
    @DisplayName("Should return single high-confidence match directly")
    void shouldReturnSingleHighConfidenceMatchDirectly() throws IOException {
      // Given
      List<VectorSimilaritySearchService.SimilaritySearchResult> singleHighMatch =
          Arrays.asList(createMockSimilarityResult("EMAIL.ADDRESS", 0.95));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleHighMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getResultType()).isEqualTo("existing");
      assertThat(result.getExistingTypeMatch()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.isExistingTypeIsBuiltIn()).isFalse();
      assertThat(result.getSuggestedAction()).isEqualTo("Use the existing semantic type");
      verify(comparisonService).compareSemanticTypes(eq(testRequest), any());
    }

    @Test
    @DisplayName("Should use LLM evaluation for multiple matches")
    void shouldUseLlmEvaluationForMultipleMatches() throws Exception {
      // Given - Use scores below 0.95 to ensure LLM evaluation path
      List<VectorSimilaritySearchService.SimilaritySearchResult> multipleMatches =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.90),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(multipleMatches);
      when(promptService.buildMultipleMatchEvaluationPrompt(
              eq(testRequest), any(), eq(multipleMatches)))
          .thenReturn("LLM evaluation prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("LLM evaluation prompt"))
          .thenReturn("LLM response");

      Map<String, Object> llmResponse = new HashMap<>();
      llmResponse.put("foundMatch", true);
      llmResponse.put("matchedType", "EMAIL.ADDRESS");
      llmResponse.put("explanation", "Best match for email validation");
      llmResponse.put("suggestedAction", "use_existing");

      when(responseParserService.parseSimilarityCheckXmlResponse("LLM response"))
          .thenReturn(llmResponse);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getResultType()).isEqualTo("existing");
      assertThat(result.getExistingTypeMatch()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.getExplanation()).isEqualTo("Best match for email validation");
      assertThat(result.getSuggestedAction()).isEqualTo("use_existing");

      verify(promptService)
          .buildMultipleMatchEvaluationPrompt(eq(testRequest), any(), eq(multipleMatches));
      verify(awsBedrockService).invokeClaudeForSemanticTypeGeneration("LLM evaluation prompt");
      verify(responseParserService).parseSimilarityCheckXmlResponse("LLM response");
    }

    @Test
    @DisplayName("Should handle LLM selecting non-existent type")
    void shouldHandleLlmSelectingNonExistentType() throws Exception {
      // Given - Use scores below 0.95 to ensure LLM evaluation path
      List<VectorSimilaritySearchService.SimilaritySearchResult> multipleMatches =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.90),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(multipleMatches);
      when(promptService.buildMultipleMatchEvaluationPrompt(
              eq(testRequest), any(), eq(multipleMatches)))
          .thenReturn("LLM evaluation prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("LLM evaluation prompt"))
          .thenReturn("LLM response");

      Map<String, Object> llmResponse = new HashMap<>();
      llmResponse.put("foundMatch", true);
      llmResponse.put("matchedType", "NON_EXISTENT_TYPE"); // Type not in candidates
      llmResponse.put("explanation", "Selected non-existent type");

      when(responseParserService.parseSimilarityCheckXmlResponse("LLM response"))
          .thenReturn(llmResponse);

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle LLM response indicating no match")
    void shouldHandleLlmResponseIndicatingNoMatch() throws Exception {
      // Given - Use scores below 0.95 to ensure LLM evaluation path
      List<VectorSimilaritySearchService.SimilaritySearchResult> multipleMatches =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.90),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(multipleMatches);
      when(promptService.buildMultipleMatchEvaluationPrompt(
              eq(testRequest), any(), eq(multipleMatches)))
          .thenReturn("LLM evaluation prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("LLM evaluation prompt"))
          .thenReturn("LLM response");

      Map<String, Object> llmResponse = new HashMap<>();
      llmResponse.put("foundMatch", false);
      llmResponse.put("explanation", "No suitable match found");

      when(responseParserService.parseSimilarityCheckXmlResponse("LLM response"))
          .thenReturn(llmResponse);

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle vector search exception gracefully")
    void shouldHandleVectorSearchExceptionGracefully() throws IOException {
      // Given
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenThrow(new RuntimeException("Vector search failed"));

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle single match with very high confidence")
    void shouldHandleSingleMatchWithVeryHighConfidence() throws IOException {
      // Given
      List<VectorSimilaritySearchService.SimilaritySearchResult> veryHighMatch =
          Arrays.asList(createMockSimilarityResult("EMAIL.ADDRESS", 0.98));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(veryHighMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypeMatch()).isEqualTo("EMAIL.ADDRESS");
      // Should not use LLM for very high confidence single match
      try {
        verify(awsBedrockService, times(0)).invokeClaudeForSemanticTypeGeneration(anyString());
      } catch (Exception e) {
        // Expected - method throws exception
      }
    }

    @Test
    @DisplayName("Should identify built-in types correctly")
    void shouldIdentifyBuiltInTypesCorrectly() throws IOException {
      // Given
      List<VectorSimilaritySearchService.SimilaritySearchResult> builtInMatch =
          Arrays.asList(createMockSimilarityResult("NAME.FIRST", 0.95));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(builtInMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypeMatch()).isEqualTo("NAME.FIRST");
      assertThat(result.isExistingTypeIsBuiltIn()).isTrue(); // NAME.FIRST is built-in
    }

    @Test
    @DisplayName("Should handle IOException from custom type service")
    void shouldHandleIOExceptionFromCustomTypeService() throws IOException {
      // Given
      when(customSemanticTypeService.getAllCustomTypes())
          .thenThrow(new IOException("Service unavailable"));

      // When & Then
      assertThatThrownBy(() -> similarityService.checkForSimilarExistingType(testRequest))
          .isInstanceOf(IOException.class)
          .hasMessage("Service unavailable");
    }
  }

  @Nested
  @DisplayName("Generate Examples for Existing Type")
  class GenerateExamplesForExistingType {

    @Test
    @DisplayName("Should generate examples for existing type successfully")
    void shouldGenerateExamplesForExistingTypeSuccessfully() throws Exception {
      // Given
      String existingTypeName = "EMAIL.ADDRESS";
      CustomSemanticType existingType =
          createMockCustomType("EMAIL.ADDRESS", "Email addresses", false);

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);

      // When
      GeneratedSemanticType result =
          similarityService.generateExamplesForExistingType(existingTypeName, testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getResultType()).isEqualTo("existing");
      assertThat(result.getSemanticType()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.getDescription()).isEqualTo("Email addresses");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getPositiveContentExamples())
          .isEqualTo(testRequest.getPositiveContentExamples());
      assertThat(result.getNegativeContentExamples())
          .isEqualTo(testRequest.getNegativeContentExamples());
      assertThat(result.getPositiveHeaderExamples())
          .isEqualTo(testRequest.getPositiveHeaderExamples());
      assertThat(result.getNegativeHeaderExamples())
          .isEqualTo(testRequest.getNegativeHeaderExamples());
      assertThat(result.getExplanation()).isEqualTo("Using existing semantic type: EMAIL.ADDRESS");
    }

    @Test
    @DisplayName("Should throw exception when existing type not found")
    void shouldThrowExceptionWhenExistingTypeNotFound() throws Exception {
      // Given
      String nonExistentType = "NON_EXISTENT_TYPE";
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);

      // When & Then
      assertThatThrownBy(
              () -> similarityService.generateExamplesForExistingType(nonExistentType, testRequest))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Existing type not found: NON_EXISTENT_TYPE");
    }

    @Test
    @DisplayName("Should handle IOException from custom type service")
    void shouldHandleIOExceptionFromCustomTypeService() throws Exception {
      // Given
      when(customSemanticTypeService.getAllCustomTypes())
          .thenThrow(new IOException("Service error"));

      // When & Then
      assertThatThrownBy(
              () -> similarityService.generateExamplesForExistingType("EMAIL.ADDRESS", testRequest))
          .isInstanceOf(IOException.class)
          .hasMessage("Service error");
    }
  }

  @Nested
  @DisplayName("Helper Methods - Pattern and Header Extraction")
  class HelperMethodsPatternAndHeaderExtraction {

    @Test
    @DisplayName("Should build match response with regex pattern extraction")
    void shouldBuildMatchResponseWithRegexPatternExtraction() throws IOException {
      // Given
      CustomSemanticType regexType =
          createMockCustomType("EMAIL.ADDRESS", "Email addresses", false);
      regexType.setPluginType("regex");

      // Set up locale with match entries
      CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
      CustomSemanticType.MatchEntry matchEntry =
          CustomSemanticType.MatchEntry.builder()
              .regExpReturned("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
              .build();
      locale.setMatchEntries(Arrays.asList(matchEntry));
      regexType.setValidLocales(Arrays.asList(locale));

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("EMAIL.ADDRESS", 0.95));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Arrays.asList(regexType));
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(Arrays.asList(regexType));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypePattern())
          .isEqualTo("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    @Test
    @DisplayName("Should build match response with list pattern extraction")
    void shouldBuildMatchResponseWithListPatternExtraction() throws IOException {
      // Given
      CustomSemanticType listType = createMockCustomType("COUNTRY.CODE", "Country codes", false);
      listType.setPluginType("list");

      CustomSemanticType.ContentConfig content =
          CustomSemanticType.ContentConfig.builder()
              .values(Arrays.asList("US", "CA", "GB", "DE", "FR"))
              .build();
      listType.setContent(content);

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("COUNTRY.CODE", 0.95));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Arrays.asList(listType));
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(Arrays.asList(listType));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      // Backend truncates list patterns when many values; for <=5 values it joins as
      // comma-separated
      assertThat(result.getExistingTypePattern()).isEqualTo("US, CA, GB, DE, FR");
    }

    @Test
    @DisplayName("Should build match response with truncated list pattern for many values")
    void shouldBuildMatchResponseWithTruncatedListPatternForManyValues() throws IOException {
      // Given
      CustomSemanticType listType = createMockCustomType("STATE.CODE", "US State codes", false);
      listType.setPluginType("list");

      List<String> manyValues =
          Arrays.asList("AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA");
      CustomSemanticType.ContentConfig content =
          CustomSemanticType.ContentConfig.builder().values(manyValues).build();
      listType.setContent(content);

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("STATE.CODE", 0.95));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Arrays.asList(listType));
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(Arrays.asList(listType));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      // Expect first 5 values then ellipsis per current backend
      assertThat(result.getExistingTypePattern())
          .isEqualTo("AL, AK, AZ, AR, CA ... (10 values total)");
    }

    @Test
    @DisplayName("Should extract header patterns from semantic type")
    void shouldExtractHeaderPatternsFromSemanticType() throws IOException {
      // Given
      CustomSemanticType typeWithHeaders =
          createMockCustomType("EMAIL.ADDRESS", "Email addresses", false);

      CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
      CustomSemanticType.HeaderRegExp headerRegExp1 =
          CustomSemanticType.HeaderRegExp.builder().regExp("(?i).*email.*").build();
      CustomSemanticType.HeaderRegExp headerRegExp2 =
          CustomSemanticType.HeaderRegExp.builder().regExp("(?i).*mail.*").build();
      locale.setHeaderRegExps(Arrays.asList(headerRegExp1, headerRegExp2));
      typeWithHeaders.setValidLocales(Arrays.asList(locale));

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("EMAIL.ADDRESS", 0.95));

      when(customSemanticTypeService.getAllCustomTypes())
          .thenReturn(Arrays.asList(typeWithHeaders));
      when(customSemanticTypeService.getCustomTypesOnly())
          .thenReturn(Arrays.asList(typeWithHeaders));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypeHeaderPatterns())
          .containsExactly("(?i).*email.*", "(?i).*mail.*");
    }

    @Test
    @DisplayName("Should handle semantic type with no header patterns")
    void shouldHandleSemanticTypeWithNoHeaderPatterns() throws IOException {
      // Given
      CustomSemanticType typeWithoutHeaders =
          createMockCustomType("SIMPLE.TYPE", "Simple type", false);
      // No header patterns set

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("SIMPLE.TYPE", 0.95));

      when(customSemanticTypeService.getAllCustomTypes())
          .thenReturn(Arrays.asList(typeWithoutHeaders));
      when(customSemanticTypeService.getCustomTypesOnly())
          .thenReturn(Arrays.asList(typeWithoutHeaders));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypeHeaderPatterns()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandling {

    @Test
    @DisplayName("Should handle null similarity results gracefully")
    void shouldHandleNullSimilarityResultsGracefully() throws IOException {
      // Given
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(null);

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle null LLM response gracefully")
    void shouldHandleNullLlmResponseGracefully() throws Exception {
      // Given - Use scores below 0.95 to ensure LLM evaluation path
      List<VectorSimilaritySearchService.SimilaritySearchResult> multipleMatches =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.90),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(multipleMatches);
      when(promptService.buildMultipleMatchEvaluationPrompt(
              eq(testRequest), any(), eq(multipleMatches)))
          .thenReturn("LLM evaluation prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("LLM evaluation prompt"))
          .thenReturn("LLM response");
      when(responseParserService.parseSimilarityCheckXmlResponse("LLM response")).thenReturn(null);

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle exception during LLM evaluation")
    void shouldHandleExceptionDuringLlmEvaluation() throws Exception {
      // Given - Use scores below 0.95 to ensure LLM evaluation path
      List<VectorSimilaritySearchService.SimilaritySearchResult> multipleMatches =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.90),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockAllTypes);
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(mockCustomTypesOnly);
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(multipleMatches);
      when(promptService.buildMultipleMatchEvaluationPrompt(
              eq(testRequest), any(), eq(multipleMatches)))
          .thenThrow(new IOException("Prompt generation failed"));

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle empty all types list")
    void shouldHandleEmptyAllTypesList() throws IOException {
      // Given
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Collections.emptyList());
      when(customSemanticTypeService.getCustomTypesOnly()).thenReturn(Collections.emptyList());
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(Collections.emptyList());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle semantic type with null plugin type")
    void shouldHandleSemanticTypeWithNullPluginType() throws IOException {
      // Given
      CustomSemanticType typeWithNullPlugin =
          createMockCustomType("NULL.PLUGIN", "Null plugin type", false);
      typeWithNullPlugin.setPluginType(null);

      List<VectorSimilaritySearchService.SimilaritySearchResult> singleMatch =
          Arrays.asList(createMockSimilarityResult("NULL.PLUGIN", 0.95));

      when(customSemanticTypeService.getAllCustomTypes())
          .thenReturn(Arrays.asList(typeWithNullPlugin));
      when(customSemanticTypeService.getCustomTypesOnly())
          .thenReturn(Arrays.asList(typeWithNullPlugin));
      when(vectorSearchService.findTopSimilarTypesForLLM(eq(testRequest), eq(0.35)))
          .thenReturn(singleMatch);
      when(comparisonService.compareSemanticTypes(eq(testRequest), any()))
          .thenReturn(new SemanticTypeComparison());

      // When
      GeneratedSemanticType result = similarityService.checkForSimilarExistingType(testRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getExistingTypeMatch()).isEqualTo("NULL.PLUGIN");
      assertThat(result.getExistingTypePattern()).isNull();
    }
  }

  // Helper methods
  private CustomSemanticType createMockCustomType(
      String semanticType, String description, boolean isBuiltIn) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType(semanticType);
    type.setDescription(description);
    type.setIsBuiltIn(isBuiltIn);
    type.setPluginType("regex");
    return type;
  }

  private VectorSimilaritySearchService.SimilaritySearchResult createMockSimilarityResult(
      String semanticType, double score) {
    return VectorSimilaritySearchService.SimilaritySearchResult.builder()
        .semanticType(semanticType)
        .description("Mock description for " + semanticType)
        .similarityScore(score)
        .type("built-in")
        .pluginType("regex")
        .examples(Arrays.asList("example1", "example2"))
        .build();
  }
}

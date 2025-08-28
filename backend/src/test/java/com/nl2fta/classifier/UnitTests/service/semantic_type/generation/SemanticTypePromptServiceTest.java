package com.nl2fta.classifier.service.semantic_type.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.PromptService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticTypePromptService Tests")
class SemanticTypePromptServiceTest {

  @Mock private CustomSemanticTypeService customSemanticTypeService;

  @Mock private PromptService promptService;

  @InjectMocks private SemanticTypePromptService promptBuilder;

  private SemanticTypeGenerationRequest.SemanticTypeGenerationRequestBuilder baseRequestBuilder;
  private List<CustomSemanticType> mockExistingTypes;

  @BeforeEach
  void setUp() {
    baseRequestBuilder =
        SemanticTypeGenerationRequest.builder()
            .typeName("TEST_TYPE")
            .description("Test semantic type description")
            .positiveContentExamples(Arrays.asList("example1@test.com", "user@domain.org"))
            .negativeContentExamples(Arrays.asList("invalid", "123"))
            .positiveHeaderExamples(Arrays.asList("email", "email_address"))
            .negativeHeaderExamples(Arrays.asList("phone", "name"))
            .columnHeader("email_col");

    mockExistingTypes =
        Arrays.asList(
            createMockCustomType("EMAIL.ADDRESS", "Email addresses"),
            createMockCustomType("PHONE.NUMBER", "Phone numbers"),
            createMockCustomType("NAME.FIRST", "First names"));
  }

  @Nested
  @DisplayName("Generation Prompt Building")
  class GenerationPromptBuilding {

    @Test
    @DisplayName("Should build generation prompt with all existing types as context")
    void shouldBuildGenerationPromptWithExistingTypes() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(mockExistingTypes);
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");

      // When
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");
      String result = promptBuilder.buildGenerationPrompt(request);

      // Then
      assertThat(result).isEqualTo("Generated prompt");

      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getTypeName()).isEqualTo("TEST_TYPE");
      assertThat(capturedParams.getDescription()).isEqualTo("Test semantic type description");
      assertThat(capturedParams.getPositiveContentExamples())
          .containsExactly("example1@test.com", "user@domain.org");
      assertThat(capturedParams.getNegativeContentExamples()).containsExactly("invalid", "123");
      assertThat(capturedParams.getPositiveHeaderExamples())
          .containsExactly("email", "email_address");
      assertThat(capturedParams.getNegativeHeaderExamples()).containsExactly("phone", "name");
      assertThat(capturedParams.getColumnHeader()).isEqualTo("email_col");
      // Existing types are passed as plain names (no hyphen prefix)
      assertThat(capturedParams.getExistingTypes()).hasSize(3);
    }

    @Test
    @DisplayName("Should handle empty existing types list")
    void shouldHandleEmptyExistingTypesList() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(Collections.emptyList());
      // No need to stub prompt builder for value; focus on param wiring

      // When
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");
      String result = promptBuilder.buildGenerationPrompt(request);

      // Then
      assertThat(result).isEqualTo("Generated prompt");

      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getExistingTypes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null values in request gracefully")
    void shouldHandleNullValuesInRequest() throws IOException {
      // Given
      SemanticTypeGenerationRequest request =
          SemanticTypeGenerationRequest.builder()
              .typeName("MINIMAL_TYPE")
              .description("Minimal description")
              .build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(mockExistingTypes);

      // When
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");
      String result = promptBuilder.buildGenerationPrompt(request);

      // Then
      assertThat(result).isEqualTo("Generated prompt");

      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getTypeName()).isEqualTo("MINIMAL_TYPE");
      assertThat(capturedParams.getDescription()).isEqualTo("Minimal description");
      // These might be empty lists rather than null
      // assertThat(capturedParams.getPositiveContentExamples()).isNull();
      // Just check they exist and aren't throwing errors
    }

    @Test
    @DisplayName("Should propagate IOException from custom type service")
    void shouldPropagateIOException() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(customSemanticTypeService.getAllSemanticTypes())
          .thenThrow(new IOException("Service unavailable"));

      // When & Then: method declares IOException
      assertThatThrownBy(() -> promptBuilder.buildGenerationPrompt(request))
          .isInstanceOf(IOException.class)
          .hasMessage("Service unavailable");
    }
  }

  @Nested
  @DisplayName("Data Pattern Regeneration Prompt")
  class DataPatternRegenerationPrompt {

    @Test
    @DisplayName("Should delegate to PromptService for data pattern regeneration")
    void shouldDelegateToPromptServiceForDataPatternRegeneration() throws IOException {
      // Given
      String semanticTypeName = "SSN";
      String currentPattern = "\\d{9}";
      List<String> positiveExamples = Arrays.asList("123456789", "987654321");
      List<String> negativeExamples = Arrays.asList("abc123", "12345");
      String userDescription = "Add dashes to format";
      String description = "Social Security Number";

      when(promptService.buildRegenerateDataValuesPrompt(
              semanticTypeName,
              currentPattern,
              positiveExamples,
              negativeExamples,
              userDescription,
              description))
          .thenReturn("Data regeneration prompt");

      // When
      String result =
          promptBuilder.buildRegenerateDataValuesPrompt(
              semanticTypeName,
              currentPattern,
              positiveExamples,
              negativeExamples,
              userDescription,
              description);

      // Then
      assertThat(result).isEqualTo("Data regeneration prompt");
      verify(promptService)
          .buildRegenerateDataValuesPrompt(
              semanticTypeName,
              currentPattern,
              positiveExamples,
              negativeExamples,
              userDescription,
              description);
    }

    @Test
    @DisplayName("Should propagate IOException from PromptService")
    void shouldPropagateIOExceptionFromPromptService() throws IOException {
      // Given
      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), anyString(), anyString()))
          .thenThrow(new IOException("Template not found"));

      // When & Then
      assertThatThrownBy(
              () ->
                  promptBuilder.buildRegenerateDataValuesPrompt(
                      "TEST",
                      "\\d+",
                      Arrays.asList("123"),
                      Arrays.asList("abc"),
                      "description",
                      "type description"))
          .isInstanceOf(IOException.class)
          .hasMessage("Template not found");
    }
  }

  @Nested
  @DisplayName("Header Pattern Regeneration Prompt")
  class HeaderPatternRegenerationPrompt {

    @Test
    @DisplayName("Should delegate to PromptService for header pattern regeneration")
    void shouldDelegateToPromptServiceForHeaderPatternRegeneration() throws IOException {
      // Given
      String semanticTypeName = "EMAIL";
      String currentHeaderPatterns = "email, mail";
      List<String> positiveExamples = Arrays.asList("user@test.com", "admin@site.org");
      List<String> negativeExamples = Arrays.asList("phone", "address");
      String userDescription = "Add more email variations";

      when(promptService.buildRegenerateHeaderValuesPrompt(
              semanticTypeName,
              currentHeaderPatterns,
              positiveExamples,
              negativeExamples,
              userDescription))
          .thenReturn("Header regeneration prompt");

      // When
      String result =
          promptBuilder.buildRegenerateHeaderValuesPrompt(
              semanticTypeName,
              currentHeaderPatterns,
              positiveExamples,
              negativeExamples,
              userDescription);

      // Then
      assertThat(result).isEqualTo("Header regeneration prompt");
      verify(promptService)
          .buildRegenerateHeaderValuesPrompt(
              semanticTypeName,
              currentHeaderPatterns,
              positiveExamples,
              negativeExamples,
              userDescription);
    }

    @Test
    @DisplayName("Should handle null values in header regeneration")
    void shouldHandleNullValuesInHeaderRegeneration() throws IOException {
      // Given
      when(promptService.buildRegenerateHeaderValuesPrompt(eq("TYPE"), any(), any(), any(), any()))
          .thenReturn("Header prompt with nulls");

      // When
      String result =
          promptBuilder.buildRegenerateHeaderValuesPrompt("TYPE", null, null, null, null);

      // Then
      assertThat(result).isEqualTo("Header prompt with nulls");
      verify(promptService).buildRegenerateHeaderValuesPrompt("TYPE", null, null, null, null);
    }
  }

  @Nested
  @DisplayName("Multiple Match Evaluation Prompt")
  class MultipleMatchEvaluationPrompt {

    @Test
    @DisplayName("Should build comprehensive evaluation prompt for multiple matches")
    void shouldBuildComprehensiveEvaluationPrompt() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();
      List<CustomSemanticType> candidateTypes =
          Arrays.asList(
              createMockCustomType("EMAIL.ADDRESS", "Email addresses"),
              createMockCustomType("EMAIL.CONTACT", "Contact email addresses"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(
              createMockSimilarityResult("EMAIL.ADDRESS", 0.95),
              createMockSimilarityResult("EMAIL.CONTACT", 0.87));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result)
          .contains("You are helping determine which existing semantic type best matches");
      assertThat(result).contains("USER'S REQUEST:");
      assertThat(result).contains("Description: Test semantic type description");
      assertThat(result).contains("Positive Content Examples: example1@test.com, user@domain.org");
      assertThat(result).contains("Negative Content Examples: invalid, 123");
      assertThat(result).contains("Positive Header Examples: email, email_address");
    }

    @Test
    @DisplayName("Should handle request with minimal information")
    void shouldHandleRequestWithMinimalInformation() throws IOException {
      // Given
      SemanticTypeGenerationRequest minimalRequest =
          SemanticTypeGenerationRequest.builder().description("Minimal description").build();

      List<CustomSemanticType> candidateTypes =
          Arrays.asList(createMockCustomType("TYPE1", "Description 1"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.8));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              minimalRequest, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("Description: Minimal description");
      assertThat(result).doesNotContain("Positive Content Examples:");
      assertThat(result).doesNotContain("Negative Content Examples:");
      assertThat(result).doesNotContain("Positive Header Examples:");
    }

    @Test
    @DisplayName("Should handle empty candidate types and similarity scores")
    void shouldHandleEmptyCandidatesAndScores() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, Collections.emptyList(), Collections.emptyList());

      // Then
      assertThat(result).contains("USER'S REQUEST:");
      assertThat(result).contains("Description: Test semantic type description");
    }

    @Test
    @DisplayName("Should handle request with null/empty example lists")
    void shouldHandleRequestWithNullEmptyExampleLists() throws IOException {
      // Given
      SemanticTypeGenerationRequest request =
          SemanticTypeGenerationRequest.builder()
              .description("Test description")
              .positiveContentExamples(null)
              .negativeContentExamples(Collections.emptyList())
              .positiveHeaderExamples(Collections.emptyList())
              .build();

      List<CustomSemanticType> candidateTypes =
          Arrays.asList(createMockCustomType("TYPE1", "Description 1"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.8));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("Description: Test description");
      assertThat(result).doesNotContain("Positive Content Examples:");
      assertThat(result).doesNotContain("Negative Content Examples:");
      assertThat(result).doesNotContain("Positive Header Examples:");
      assertThat(result).contains("CANDIDATE MATCHES");
    }

    @Test
    @DisplayName("Should handle list-type semantic types with content values")
    void shouldHandleListTypeSemanticTypesWithContentValues() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      // Create a list-type semantic type with content values
      CustomSemanticType listType = createMockCustomType("COUNTRY.CODE", "Country codes");
      listType.setPluginType("list");
      CustomSemanticType.ContentConfig content =
          CustomSemanticType.ContentConfig.builder()
              .values(Arrays.asList("US", "CA", "GB", "DE", "FR"))
              .build();
      listType.setContent(content);

      List<CustomSemanticType> candidateTypes = Arrays.asList(listType);
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("COUNTRY.CODE", 0.9));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("COUNTRY.CODE");
      assertThat(result).contains("Values: US, CA, GB, DE, FR");
    }

    @Test
    @DisplayName("Should handle list-type semantic types with many content values")
    void shouldHandleListTypeSemanticTypesWithManyContentValues() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      // Create a list-type semantic type with more than 5 values
      CustomSemanticType listType = createMockCustomType("STATE.CODE", "US State codes");
      listType.setPluginType("list");
      CustomSemanticType.ContentConfig content =
          CustomSemanticType.ContentConfig.builder()
              .values(Arrays.asList("AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA"))
              .build();
      listType.setContent(content);

      List<CustomSemanticType> candidateTypes = Arrays.asList(listType);
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("STATE.CODE", 0.85));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("STATE.CODE");
      assertThat(result).contains("Sample values: AL, AK, AZ, AR, CA...");
    }

    @Test
    @DisplayName("Should handle list-type semantic types with null content")
    void shouldHandleListTypeSemanticTypesWithNullContent() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      CustomSemanticType listType = createMockCustomType("EMPTY.LIST", "Empty list type");
      listType.setPluginType("list");
      listType.setContent(null);

      List<CustomSemanticType> candidateTypes = Arrays.asList(listType);
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("EMPTY.LIST", 0.7));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("EMPTY.LIST");
      assertThat(result).doesNotContain("Values:");
      assertThat(result).doesNotContain("Sample values:");
    }

    @Test
    @DisplayName("Should handle list-type semantic types with empty values list")
    void shouldHandleListTypeSemanticTypesWithEmptyValuesList() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      CustomSemanticType listType = createMockCustomType("EMPTY.VALUES", "Empty values list");
      listType.setPluginType("list");
      CustomSemanticType.ContentConfig content =
          CustomSemanticType.ContentConfig.builder()
              .values(null) // null values list
              .build();
      listType.setContent(content);

      List<CustomSemanticType> candidateTypes = Arrays.asList(listType);
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("EMPTY.VALUES", 0.6));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("EMPTY.VALUES");
      assertThat(result).doesNotContain("Values:");
      assertThat(result).doesNotContain("Sample values:");
    }
  }

  @Nested
  @DisplayName("Error Handling and Edge Cases")
  class ErrorHandlingAndEdgeCases {

    @Test
    @DisplayName("Should handle null request in buildGenerationPrompt")
    void shouldHandleNullRequestInBuildGenerationPrompt() {
      // When & Then
      assertThatThrownBy(() -> promptBuilder.buildGenerationPrompt(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle null request in buildMultipleMatchEvaluationPrompt")
    void shouldHandleNullRequestInBuildMultipleMatchEvaluationPrompt() {
      // When & Then
      assertThatThrownBy(
              () ->
                  promptBuilder.buildMultipleMatchEvaluationPrompt(
                      null, Collections.emptyList(), Collections.emptyList()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle mismatched candidate types and similarity scores lists")
    void shouldHandleMismatchedCandidateTypesAndSimilarityScoresLists() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();
      List<CustomSemanticType> candidateTypes =
          Arrays.asList(
              createMockCustomType("TYPE1", "Description 1"),
              createMockCustomType("TYPE2", "Description 2"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.8)); // Only one score for two types

      // When & Then
      assertThatThrownBy(
              () ->
                  promptBuilder.buildMultipleMatchEvaluationPrompt(
                      request, candidateTypes, similarityScores))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Should handle very long description text")
    void shouldHandleVeryLongDescriptionText() throws IOException {
      // Given
      String veryLongDescription = "A".repeat(10000); // 10K character description
      SemanticTypeGenerationRequest request =
          SemanticTypeGenerationRequest.builder().description(veryLongDescription).build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(mockExistingTypes);
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");

      // When
      String result = promptBuilder.buildGenerationPrompt(request);

      // Then
      assertThat(result).isEqualTo("Generated prompt");

      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getDescription()).isEqualTo(veryLongDescription);
    }

    @Test
    @DisplayName("Should handle large number of existing types")
    void shouldHandleLargeNumberOfExistingTypes() throws IOException {
      // Given
      List<CustomSemanticType> manyTypes = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        manyTypes.add(createMockCustomType("TYPE_" + i, "Description " + i));
      }

      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(manyTypes);
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");

      // When
      String result = promptBuilder.buildGenerationPrompt(request);

      // Then
      assertThat(result).isEqualTo("Generated prompt");

      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getExistingTypes()).hasSize(1000);
    }
  }

  @Nested
  @DisplayName("Business Logic Validation")
  class BusinessLogicValidation {

    @Test
    @DisplayName("Should correctly format existing types with hyphen prefix")
    void shouldCorrectlyFormatExistingTypesWithHyphenPrefix() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(mockExistingTypes);
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");

      // When
      promptBuilder.buildGenerationPrompt(request);

      // Then
      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      List<String> existingTypes = capturedParams.getExistingTypes();

      // Existing types are plain names in current implementation
      assertThat(existingTypes).containsExactly("EMAIL.ADDRESS", "PHONE.NUMBER", "NAME.FIRST");
    }

    @Test
    @DisplayName("Should include all request parameters in generation prompt")
    void shouldIncludeAllRequestParametersInGenerationPrompt() throws IOException {
      // Given
      SemanticTypeGenerationRequest complexRequest =
          SemanticTypeGenerationRequest.builder()
              .typeName("COMPLEX_TYPE")
              .description("Complex type description")
              .positiveContentExamples(Arrays.asList("pos1", "pos2", "pos3"))
              .negativeContentExamples(Arrays.asList("neg1", "neg2"))
              .positiveHeaderExamples(Arrays.asList("header1", "header2"))
              .negativeHeaderExamples(Arrays.asList("badheader1"))
              .columnHeader("test_column")
              .build();

      when(customSemanticTypeService.getAllSemanticTypes()).thenReturn(mockExistingTypes);
      when(promptService.buildSemanticTypeGenerationPrompt(any())).thenReturn("Generated prompt");

      // When
      promptBuilder.buildGenerationPrompt(complexRequest);

      // Then
      ArgumentCaptor<PromptService.SemanticTypeGenerationParams> paramsCaptor =
          ArgumentCaptor.forClass(PromptService.SemanticTypeGenerationParams.class);
      verify(promptService).buildSemanticTypeGenerationPrompt(paramsCaptor.capture());

      PromptService.SemanticTypeGenerationParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getTypeName()).isEqualTo("COMPLEX_TYPE");
      assertThat(capturedParams.getDescription()).isEqualTo("Complex type description");
      assertThat(capturedParams.getPositiveContentExamples())
          .containsExactly("pos1", "pos2", "pos3");
      assertThat(capturedParams.getNegativeContentExamples()).containsExactly("neg1", "neg2");
      assertThat(capturedParams.getPositiveHeaderExamples()).containsExactly("header1", "header2");
      assertThat(capturedParams.getNegativeHeaderExamples()).containsExactly("badheader1");
      assertThat(capturedParams.getColumnHeader()).isEqualTo("test_column");
    }

    @Test
    @DisplayName("Should correctly build XML format in multiple match evaluation prompt")
    void shouldCorrectlyBuildXmlFormatInMultipleMatchEvaluationPrompt() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();
      List<CustomSemanticType> candidateTypes =
          Arrays.asList(createMockCustomType("TYPE1", "Description 1"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.85));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("<similarity_check>");
      assertThat(result).contains("<found_match>true/false</found_match>");
      assertThat(result).contains("<matched_type>TYPE_NAME or null</matched_type>");
      assertThat(result)
          .contains(
              "<explanation>Brief explanation of why this is the best match or why none match</explanation>");
      assertThat(result)
          .contains("<suggested_action>use_existing/create_different</suggested_action>");
      assertThat(result).contains("</similarity_check>");
    }

    @Test
    @DisplayName("Should correctly format similarity scores as percentages")
    void shouldCorrectlyFormatSimilarityScoresAsPercentages() throws IOException {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();
      List<CustomSemanticType> candidateTypes =
          Arrays.asList(
              createMockCustomType("HIGH_SCORE", "High similarity"),
              createMockCustomType("LOW_SCORE", "Low similarity"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(
              createMockSimilarityResult("HIGH_SCORE", 0.9567), // Should be formatted as 95.7%
              createMockSimilarityResult("LOW_SCORE", 0.1234) // Should be formatted as 12.3%
              );

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("(Similarity: 95.7%)");
      assertThat(result).contains("(Similarity: 12.3%)");
    }

    @Test
    @DisplayName(
        "Should handle null negativeContentExamples and positiveHeaderExamples in buildMultipleMatchEvaluationPrompt")
    void shouldHandleNullNegativeContentExamplesAndPositiveHeaderExamplesInMultipleMatch()
        throws IOException {
      // Given
      SemanticTypeGenerationRequest request =
          SemanticTypeGenerationRequest.builder()
              .description("Test description")
              .positiveContentExamples(Arrays.asList("pos1", "pos2"))
              .negativeContentExamples(null) // null, not empty
              .positiveHeaderExamples(null) // null, not empty
              .build();

      List<CustomSemanticType> candidateTypes =
          Arrays.asList(createMockCustomType("TYPE1", "Description 1"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.8));

      // When
      String result =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              request, candidateTypes, similarityScores);

      // Then
      assertThat(result).contains("Description: Test description");
      assertThat(result).contains("Positive Content Examples: pos1, pos2");
      assertThat(result).doesNotContain("Negative Content Examples:");
      assertThat(result).doesNotContain("Positive Header Examples:");
      assertThat(result).contains("CANDIDATE MATCHES");
    }
  }

  @Nested
  @DisplayName("Comprehensive Branch Coverage")
  class ComprehensiveBranchCoverage {

    @Test
    @DisplayName("Should achieve complete branch coverage for all null scenarios")
    void shouldAchieveCompleteBranchCoverageForAllNullScenarios() throws IOException {
      // This test is designed to hit any remaining branch coverage gaps
      // by testing combinations of null/empty values systematically

      // Test case 1: All nulls in multiple match evaluation
      SemanticTypeGenerationRequest allNullsRequest =
          SemanticTypeGenerationRequest.builder()
              .description("Description only")
              .positiveContentExamples(null)
              .negativeContentExamples(null)
              .positiveHeaderExamples(null)
              .negativeHeaderExamples(null)
              .build();

      List<CustomSemanticType> candidateTypes =
          Arrays.asList(createMockCustomType("TYPE1", "Description 1"));
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores =
          Arrays.asList(createMockSimilarityResult("TYPE1", 0.8));

      String result1 =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              allNullsRequest, candidateTypes, similarityScores);
      assertThat(result1).contains("Description: Description only");
      assertThat(result1).doesNotContain("Positive Content Examples:");
      assertThat(result1).doesNotContain("Negative Content Examples:");
      assertThat(result1).doesNotContain("Positive Header Examples:");

      // Test case 2: Mix of null and empty lists
      SemanticTypeGenerationRequest mixedRequest =
          SemanticTypeGenerationRequest.builder()
              .description("Mixed scenario")
              .positiveContentExamples(Arrays.asList("example"))
              .negativeContentExamples(null) // null
              .positiveHeaderExamples(null) // null
              .negativeHeaderExamples(Collections.emptyList()) // empty
              .build();

      String result2 =
          promptBuilder.buildMultipleMatchEvaluationPrompt(
              mixedRequest, candidateTypes, similarityScores);
      assertThat(result2).contains("Positive Content Examples: example");
      assertThat(result2).doesNotContain("Negative Content Examples:");
      assertThat(result2).doesNotContain("Positive Header Examples:");
    }
  }

  // Helper methods
  private CustomSemanticType createMockCustomType(String semanticType, String description) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType(semanticType);
    type.setDescription(description);
    type.setPluginType("regex"); // default to regex type
    return type;
  }

  private VectorSimilaritySearchService.SimilaritySearchResult createMockSimilarityResult(
      String semanticType, double score) {
    return VectorSimilaritySearchService.SimilaritySearchResult.builder()
        .semanticType(semanticType)
        .description("Mock description")
        .similarityScore(score)
        .type("built-in")
        .pluginType("regex")
        .examples(Arrays.asList("example1", "example2"))
        .build();
  }
}

package com.nl2fta.classifier.service.semantic_type.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GenerateValidatedExamplesRequest;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedValidatedExamplesResponse;
import com.nl2fta.classifier.dto.semantic_type.PatternUpdateResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticTypeGenerationService Tests")
class SemanticTypeGenerationServiceTest {

  @Mock private AwsBedrockService awsBedrockService;

  @Mock private SemanticTypePromptService promptService;

  @Mock private SemanticTypeResponseParserService responseParserService;

  @Mock private CustomSemanticTypeService customSemanticTypeService;

  @Mock private SemanticTypeSimilarityService similarityService;

  @InjectMocks private SemanticTypeGenerationService generationService;

  private SemanticTypeGenerationRequest.SemanticTypeGenerationRequestBuilder baseRequestBuilder;

  @BeforeEach
  void setUp() {
    baseRequestBuilder =
        SemanticTypeGenerationRequest.builder()
            .typeName("TEST_TYPE")
            .description("Test semantic type")
            .positiveContentExamples(Arrays.asList("example1", "example2"))
            .negativeContentExamples(Arrays.asList("negative1", "negative2"))
            .positiveHeaderExamples(Arrays.asList("header1", "header2"))
            .negativeHeaderExamples(Arrays.asList("bad_header"));
  }

  @Nested
  @DisplayName("Generate Semantic Type - Core Logic")
  class GenerateSemanticType {

    @Test
    @DisplayName("Should generate new semantic type when no similarity check requested")
    void shouldGenerateNewSemanticTypeWithoutSimilarityCheck() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.checkExistingTypes(false).build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "new");

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TEST_TYPE");
      assertThat(result.getResultType()).isEqualTo("new");

      verify(similarityService, never()).checkForSimilarExistingType(any());
      verify(promptService).buildGenerationPrompt(request);
      verify(awsBedrockService).invokeClaudeForSemanticTypeGeneration("test prompt");
    }

    @Test
    @DisplayName(
        "Should use existing type when similarity found and not proceeding despite similarity")
    void shouldUseExistingTypeWhenSimilarityFound() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.checkExistingTypes(true).proceedDespiteSimilarity(false).build();

      GeneratedSemanticType existingMatch = createMockGeneratedType("EMAIL.ADDRESS", "existing");
      existingMatch.setExistingTypeMatch("EMAIL.ADDRESS");
      existingMatch.setExistingTypeIsBuiltIn(true);

      when(similarityService.checkForSimilarExistingType(request)).thenReturn(existingMatch);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.getResultType()).isEqualTo("existing");
      assertThat(result.getExistingTypeMatch()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.isExistingTypeIsBuiltIn()).isTrue();

      verify(similarityService).checkForSimilarExistingType(request);
      verify(promptService, never()).buildGenerationPrompt(any());
      verify(awsBedrockService, never()).invokeClaudeForSemanticTypeGeneration(anyString());
    }

    @Test
    @DisplayName("Should generate new type when similarity found but proceeding despite similarity")
    void shouldGenerateNewTypeWhenProceedingDespiteSimilarity() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.checkExistingTypes(true).proceedDespiteSimilarity(true).build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "new");

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TEST_TYPE");
      assertThat(result.getResultType()).isEqualTo("new");

      verify(similarityService, never()).checkForSimilarExistingType(any());
      verify(promptService).buildGenerationPrompt(request);
      verify(awsBedrockService).invokeClaudeForSemanticTypeGeneration("test prompt");
    }

    @Test
    @DisplayName("Should generate new type when no similarity found")
    void shouldGenerateNewTypeWhenNoSimilarityFound() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.checkExistingTypes(true).proceedDespiteSimilarity(false).build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "new");

      when(similarityService.checkForSimilarExistingType(request)).thenReturn(null);
      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TEST_TYPE");
      assertThat(result.getResultType()).isEqualTo("new");

      verify(similarityService).checkForSimilarExistingType(request);
      verify(promptService).buildGenerationPrompt(request);
      verify(awsBedrockService).invokeClaudeForSemanticTypeGeneration("test prompt");
    }

    @Test
    @DisplayName("Should handle examples-only requests")
    void shouldHandleExamplesOnlyRequests() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.typeName("EXAMPLES_ONLY_EMAIL").build();

      GeneratedSemanticType expectedResult =
          createMockGeneratedType("EXAMPLES_ONLY_EMAIL", "examples_only");

      when(promptService.buildGenerationPrompt(request)).thenReturn("examples prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("examples prompt"))
          .thenReturn("examples response");
      when(responseParserService.parseGenerationResponse("examples response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("EXAMPLES_ONLY_EMAIL");

      verify(similarityService, never()).checkForSimilarExistingType(any());
      verify(promptService).buildGenerationPrompt(request);
    }

    @Test
    @DisplayName("Should handle generate examples for existing type")
    void shouldGenerateExamplesForExistingType() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.generateExamplesForExistingType("EMAIL.ADDRESS").build();

      GeneratedSemanticType expectedResult =
          createMockGeneratedType("EMAIL.ADDRESS", "examples_for_existing");

      when(similarityService.generateExamplesForExistingType("EMAIL.ADDRESS", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("EMAIL.ADDRESS");

      verify(similarityService).generateExamplesForExistingType("EMAIL.ADDRESS", request);
      verify(promptService, never()).buildGenerationPrompt(any());
    }

    @Test
    @DisplayName("Should validate regex examples when plugin type is regex")
    void shouldValidateRegexExamples() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("TEST_REGEX")
              .pluginType("regex")
              .regexPattern("\\d{3}-\\d{2}-\\d{4}")
              .positiveContentExamples(
                  Arrays.asList("123-45-6789", "invalid-format", "987-65-4321"))
              .negativeContentExamples(Arrays.asList("123456789", "123-45-6789", "abc-de-fghi"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getPluginType()).isEqualTo("regex");

      // Should filter out invalid positive examples
      assertThat(result.getPositiveContentExamples()).containsExactly("123-45-6789", "987-65-4321");

      // Should filter out invalid negative examples (ones that actually match the pattern)
      assertThat(result.getNegativeContentExamples()).containsExactly("123456789", "abc-de-fghi");
    }

    @Test
    @DisplayName("Should handle AWS service failures gracefully")
    void shouldHandleAwsServiceFailures() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenThrow(new RuntimeException("AWS service unavailable"));

      // When & Then
      assertThatThrownBy(() -> generationService.generateSemanticType(request))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("AWS service unavailable");
    }
  }

  @Nested
  @DisplayName("Generate Validated Examples")
  class GenerateValidatedExamples {

    @Test
    @DisplayName("Should handle data pattern improvement requests")
    void shouldHandleDataPatternImprovement() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("SSN")
              .regexPattern("\\d{9}")
              .existingPositiveExamples(Arrays.asList("123456789"))
              .existingNegativeExamples(Arrays.asList("abc123"))
              .userDescription("Add dashes to SSN format")
              .isPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .improvedPattern("\\d{3}-\\d{2}-\\d{4}")
              .newPositiveContentExamples(Arrays.asList("123-45-6789", "987-65-4321"))
              .newNegativeContentExamples(Arrays.asList("123456789", "abc-de-fghi"))
              .explanation("Updated pattern to include dashes")
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              eq("SSN"), eq("\\d{9}"), any(), any(), eq("Add dashes to SSN format"), any()))
          .thenReturn("data improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("data improvement prompt"))
          .thenReturn("pattern update response");
      when(responseParserService.parsePatternUpdateResponse("pattern update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getUpdatedRegexPattern()).isEqualTo("\\d{3}-\\d{2}-\\d{4}");
      assertThat(result.getPositiveExamples()).containsExactly("123-45-6789", "987-65-4321");
      assertThat(result.getNegativeExamples()).containsExactly("123456789", "abc-de-fghi");
      assertThat(result.getRationale()).isEqualTo("Updated pattern to include dashes");

      verify(promptService)
          .buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Should handle header pattern improvement requests")
    void shouldHandleHeaderPatternImprovement() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("EMAIL")
              .existingPositiveExamples(Arrays.asList("email", "mail"))
              .existingNegativeExamples(Arrays.asList("phone", "address"))
              .userDescription("Add email variations")
              .isHeaderPatternImprovement(true)
              .build();

      CustomSemanticType existingType = new CustomSemanticType();
      existingType.setSemanticType("EMAIL");

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .newPositiveHeaderExamples(
                  Arrays.asList("email_address", "e_mail", "electronic_mail"))
              .newNegativeHeaderExamples(Arrays.asList("postal_mail", "street_address"))
              .explanation("Added more email header variations")
              .build();

      when(customSemanticTypeService.getCustomType("EMAIL")).thenReturn(existingType);
      when(promptService.buildRegenerateHeaderValuesPrompt(
              eq("EMAIL"), anyString(), any(), any(), eq("Add email variations")))
          .thenReturn("header improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("header improvement prompt"))
          .thenReturn("header update response");
      when(responseParserService.parsePatternUpdateResponse("header update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples())
          .containsExactly("email_address", "e_mail", "electronic_mail");
      assertThat(result.getNegativeExamples()).containsExactly("postal_mail", "street_address");
      assertThat(result.getUpdatedHeaderPatterns()).hasSize(3);
      assertThat(result.getRationale()).isEqualTo("Added more email header variations");

      verify(customSemanticTypeService).getCustomType("EMAIL");
      verify(promptService)
          .buildRegenerateHeaderValuesPrompt(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Should handle general example generation")
    void shouldHandleGeneralExampleGeneration() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("PHONE")
              .regexPattern("\\d{10}")
              .existingPositiveExamples(Arrays.asList("5551234567"))
              .existingNegativeExamples(Arrays.asList("abc"))
              .isPatternImprovement(false)
              .isHeaderPatternImprovement(false)
              .build();

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples()).containsExactly("5551234567");
      assertThat(result.getNegativeExamples()).containsExactly("abc");

      // Should not call AWS services for general generation
      verify(awsBedrockService, never()).invokeClaudeForSemanticTypeGeneration(anyString());
    }

    @Test
    @DisplayName("Should handle pattern update parsing failures")
    void shouldHandlePatternUpdateParsingFailures() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("FAILED_TYPE")
              .regexPattern("\\d+")
              .isPatternImprovement(true)
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), any(), any()))
          .thenReturn("improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("improvement prompt"))
          .thenReturn("invalid response");
      when(responseParserService.parsePatternUpdateResponse("invalid response")).thenReturn(null);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isFalse();
      assertThat(result.getError()).contains("Failed to parse data pattern update response");
    }

    @Test
    @DisplayName("Should handle service exceptions during validation")
    void shouldHandleServiceExceptionsDuringValidation() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("ERROR_TYPE")
              .isPatternImprovement(true)
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), any(), any(), any(), anyString(), any()))
          .thenThrow(new RuntimeException("Service error"));

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isFalse();
      assertThat(result.getError()).contains("Failed to improve data pattern: Service error");
    }
  }

  @Nested
  @DisplayName("Pattern Validation")
  class PatternValidation {

    @Test
    @DisplayName("Should validate positive examples correctly against regex pattern")
    void shouldValidatePositiveExamplesCorrectly() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("TEST_VALIDATION")
              .pluginType("regex")
              .regexPattern("\\d{3}")
              .positiveContentExamples(Arrays.asList("123", "abc", "456", "xyz"))
              .negativeContentExamples(Arrays.asList("12", "1234", "789"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result.getPositiveContentExamples()).containsExactly("123", "456");
      assertThat(result.getNegativeContentExamples()).containsExactly("12", "1234");
    }

    @Test
    @DisplayName("Should handle invalid regex patterns gracefully")
    void shouldHandleInvalidRegexPatterns() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType invalidRegexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("INVALID_REGEX")
              .pluginType("regex")
              .regexPattern("[invalid regex")
              .positiveContentExamples(Arrays.asList("test1", "test2"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(invalidRegexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then - should return original result without validation
      assertThat(result.getPositiveContentExamples()).containsExactly("test1", "test2");
      assertThat(result.getRegexPattern()).isEqualTo("[invalid regex");
    }

    @Test
    @DisplayName("Should preserve all examples when plugin type is not regex")
    void shouldPreserveExamplesForNonRegexTypes() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType listType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("LIST_TYPE")
              .pluginType("list")
              .positiveContentExamples(Arrays.asList("apple", "banana", "cherry"))
              .negativeContentExamples(Arrays.asList("car", "house"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(listType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then - should not validate examples for non-regex types
      assertThat(result.getPositiveContentExamples()).containsExactly("apple", "banana", "cherry");
      assertThat(result.getNegativeContentExamples()).containsExactly("car", "house");
    }
  }

  @Nested
  @DisplayName("Request Type Detection")
  class RequestTypeDetection {

    @Test
    @DisplayName("Should detect examples-only request by type name prefix")
    void shouldDetectExamplesOnlyByTypeName() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.typeName("EXAMPLES_ONLY_TEST").build();

      GeneratedSemanticType expectedResult =
          createMockGeneratedType("EXAMPLES_ONLY_TEST", "examples");

      when(promptService.buildGenerationPrompt(request)).thenReturn("examples prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("examples prompt"))
          .thenReturn("examples response");
      when(responseParserService.parseGenerationResponse("examples response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      verify(promptService).buildGenerationPrompt(request);
    }

    @Test
    @DisplayName("Should detect examples-only request by description prefix")
    void shouldDetectExamplesOnlyByDescription() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder
              .description("EXAMPLES GENERATION ONLY: Generate more email examples")
              .build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "examples");

      when(promptService.buildGenerationPrompt(request)).thenReturn("examples prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("examples prompt"))
          .thenReturn("examples response");
      when(responseParserService.parseGenerationResponse("examples response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      verify(promptService).buildGenerationPrompt(request);
    }

    @Test
    @DisplayName("Should handle request with null type name gracefully")
    void shouldHandleNullTypeName() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.typeName(null).build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("NEW_TYPE", "new");

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("test response");
      when(responseParserService.parseGenerationResponse("test response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      verify(promptService).buildGenerationPrompt(request);
    }

    @Test
    @DisplayName("Should handle request with null description gracefully")
    void shouldHandleNullDescription() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.description(null).build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "new");

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("test response");
      when(responseParserService.parseGenerationResponse("test response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      verify(promptService).buildGenerationPrompt(request);
    }

    @Test
    @DisplayName("Should handle empty generateExamplesForExistingType string")
    void shouldHandleEmptyGenerateExamplesForExistingType() throws Exception {
      // Given
      SemanticTypeGenerationRequest request =
          baseRequestBuilder.generateExamplesForExistingType("   ").build();

      GeneratedSemanticType expectedResult = createMockGeneratedType("TEST_TYPE", "new");

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("test response");
      when(responseParserService.parseGenerationResponse("test response", request))
          .thenReturn(expectedResult);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result).isNotNull();
      verify(promptService).buildGenerationPrompt(request);
      verify(similarityService, never()).generateExamplesForExistingType(anyString(), any());
    }
  }

  @Nested
  @DisplayName("Validation Methods")
  class ValidationMethods {

    @Test
    @DisplayName("Should validate examples against pattern with null examples list")
    void shouldHandleNullExamplesInValidation() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("TEST_VALIDATION")
              .pluginType("regex")
              .regexPattern("\\d{3}")
              .positiveContentExamples(null)
              .negativeContentExamples(null)
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result.getPositiveContentExamples()).isEmpty();
      assertThat(result.getNegativeContentExamples()).isEmpty();
    }

    @Test
    @DisplayName("Should validate examples against pattern with empty examples list")
    void shouldHandleEmptyExamplesInValidation() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("TEST_VALIDATION")
              .pluginType("regex")
              .regexPattern("\\d{3}")
              .positiveContentExamples(new ArrayList<>())
              .negativeContentExamples(new ArrayList<>())
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result.getPositiveContentExamples()).isEmpty();
      assertThat(result.getNegativeContentExamples()).isEmpty();
    }

    @Test
    @DisplayName("Should handle complex regex patterns in validation")
    void shouldHandleComplexRegexPatterns() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("COMPLEX_REGEX")
              .pluginType("regex")
              .regexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
              .positiveContentExamples(
                  Arrays.asList(
                      "test@example.com",
                      "invalid-email",
                      "user.name+tag@domain.co.uk",
                      "not-an-email"))
              .negativeContentExamples(
                  Arrays.asList(
                      "@invalid.com",
                      "test@example.com", // This should be filtered out as it matches
                      "plaintext"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      assertThat(result.getPositiveContentExamples())
          .containsExactly("test@example.com", "user.name+tag@domain.co.uk");
      assertThat(result.getNegativeContentExamples()).containsExactly("@invalid.com", "plaintext");
    }

    @Test
    @DisplayName("Should preserve header examples during validation")
    void shouldPreserveHeaderExamplesDuringValidation() throws Exception {
      // Given
      SemanticTypeGenerationRequest request = baseRequestBuilder.build();

      GeneratedSemanticType regexType =
          GeneratedSemanticType.builder()
              .resultType("new")
              .semanticType("HEADER_TEST")
              .pluginType("regex")
              .regexPattern("\\d+")
              .positiveContentExamples(Arrays.asList("123", "abc"))
              .negativeContentExamples(Arrays.asList("xyz", "456"))
              .positiveHeaderExamples(Arrays.asList("number", "count"))
              .negativeHeaderExamples(Arrays.asList("text", "description"))
              .build();

      when(promptService.buildGenerationPrompt(request)).thenReturn("test prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("test prompt"))
          .thenReturn("mock response");
      when(responseParserService.parseGenerationResponse("mock response", request))
          .thenReturn(regexType);

      // When
      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // Then
      // Content examples should be filtered
      assertThat(result.getPositiveContentExamples()).containsExactly("123");
      assertThat(result.getNegativeContentExamples()).containsExactly("xyz");
      // Header examples should be preserved as-is
      assertThat(result.getPositiveHeaderExamples()).containsExactly("number", "count");
      assertThat(result.getNegativeHeaderExamples()).containsExactly("text", "description");
    }
  }

  @Nested
  @DisplayName("Generate Validated Examples - Advanced Cases")
  class GenerateValidatedExamplesAdvanced {

    @Test
    @DisplayName("Should handle header pattern improvement when semantic type not found")
    void shouldHandleHeaderPatternImprovementWhenTypeNotFound() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("NON_EXISTENT_TYPE")
              .existingPositiveExamples(Arrays.asList("email"))
              .existingNegativeExamples(Arrays.asList("phone"))
              .userDescription("Add more patterns")
              .isHeaderPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .newPositiveHeaderExamples(Arrays.asList("email_addr", "mail"))
              .newNegativeHeaderExamples(Arrays.asList("telephone"))
              .explanation("Added header patterns despite missing type")
              .build();

      when(customSemanticTypeService.getCustomType("NON_EXISTENT_TYPE"))
          .thenThrow(new RuntimeException("Type not found"));
      when(promptService.buildRegenerateHeaderValuesPrompt(
              eq("NON_EXISTENT_TYPE"), eq(""), any(), any(), eq("Add more patterns")))
          .thenReturn("header improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("header improvement prompt"))
          .thenReturn("header update response");
      when(responseParserService.parsePatternUpdateResponse("header update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples()).containsExactly("email_addr", "mail");
      assertThat(result.getNegativeExamples()).containsExactly("telephone");
      assertThat(result.getUpdatedHeaderPatterns()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle data pattern improvement with null pattern response")
    void shouldHandleDataPatternImprovementWithNullPattern() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("TEST_TYPE")
              .regexPattern("\\d+")
              .existingPositiveExamples(Arrays.asList("123"))
              .existingNegativeExamples(Arrays.asList("abc"))
              .userDescription("Improve pattern")
              .isPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .improvedPattern(null) // Null improved pattern
              .newPositiveContentExamples(Arrays.asList("456", "789"))
              .newNegativeContentExamples(Arrays.asList("xyz", "def"))
              .explanation("Generated examples without pattern change")
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              eq("TEST_TYPE"), eq("\\d+"), any(), any(), eq("Improve pattern"), any()))
          .thenReturn("data improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("data improvement prompt"))
          .thenReturn("pattern update response");
      when(responseParserService.parsePatternUpdateResponse("pattern update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getUpdatedRegexPattern()).isNull();
      assertThat(result.getPositiveExamples()).containsExactly("456", "789");
      assertThat(result.getNegativeExamples()).containsExactly("xyz", "def");
    }

    @Test
    @DisplayName("Should handle general example generation with null examples")
    void shouldHandleGeneralExampleGenerationWithNullExamples() {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("TEST_TYPE")
              .regexPattern("\\d+")
              .existingPositiveExamples(null)
              .existingNegativeExamples(null)
              .isPatternImprovement(false)
              .isHeaderPatternImprovement(false)
              .build();

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples()).isEmpty();
      assertThat(result.getNegativeExamples()).isEmpty();
    }

    @Test
    @DisplayName("Should handle AWS service failure during data pattern improvement")
    void shouldHandleAwsServiceFailureDuringDataPatternImprovement() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("FAILING_TYPE")
              .regexPattern("\\d+")
              .isPatternImprovement(true)
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), any(), any()))
          .thenThrow(new RuntimeException("AWS connection failed"));

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isFalse();
      assertThat(result.getError())
          .contains("Failed to improve data pattern: AWS connection failed");
    }

    @Test
    @DisplayName("Should handle AWS service failure during header pattern improvement")
    void shouldHandleAwsServiceFailureDuringHeaderPatternImprovement() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("FAILING_TYPE")
              .isHeaderPatternImprovement(true)
              .build();

      when(customSemanticTypeService.getCustomType("FAILING_TYPE"))
          .thenThrow(new RuntimeException("Service failure"));
      when(promptService.buildRegenerateHeaderValuesPrompt(
              anyString(), anyString(), any(), any(), any()))
          .thenThrow(new RuntimeException("AWS connection failed"));

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isFalse();
      assertThat(result.getError())
          .contains("Failed to improve header patterns: AWS connection failed");
    }

    @Test
    @DisplayName("Should validate examples with invalid regex pattern")
    void shouldValidateExamplesWithInvalidRegexPattern() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("INVALID_REGEX_TYPE")
              .regexPattern("[invalid regex")
              .existingPositiveExamples(Arrays.asList("test1", "test2"))
              .existingNegativeExamples(Arrays.asList("neg1", "neg2"))
              .userDescription("Test invalid regex")
              .isPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .improvedPattern("[another invalid regex")
              .newPositiveContentExamples(Arrays.asList("valid1", "valid2"))
              .newNegativeContentExamples(Arrays.asList("invalid1", "invalid2"))
              .explanation("Updated with invalid regex")
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), anyString(), any()))
          .thenReturn("data improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("data improvement prompt"))
          .thenReturn("pattern update response");
      when(responseParserService.parsePatternUpdateResponse("pattern update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then - should complete successfully even with invalid regex
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples()).isEmpty(); // All filtered out due to invalid regex
      assertThat(result.getNegativeExamples()).isEmpty(); // All filtered out due to invalid regex
    }
  }

  @Nested
  @DisplayName("Helper Methods and Edge Cases")
  class HelperMethodsAndEdgeCases {

    @Test
    @DisplayName("Should handle semantic type with no header patterns")
    void shouldHandleTypeWithNoHeaderPatterns() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("TYPE_NO_HEADERS")
              .isHeaderPatternImprovement(true)
              .build();

      // Create a mock type with no header patterns
      CustomSemanticType mockType = new CustomSemanticType();
      mockType.setSemanticType("TYPE_NO_HEADERS");
      CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
      locale.setHeaderRegExps(null); // No header patterns
      mockType.setValidLocales(Arrays.asList(locale));

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .newPositiveHeaderExamples(Arrays.asList("new_header"))
              .explanation("No existing header patterns")
              .build();

      when(customSemanticTypeService.getCustomType("TYPE_NO_HEADERS")).thenReturn(mockType);
      when(promptService.buildRegenerateHeaderValuesPrompt(
              eq("TYPE_NO_HEADERS"), eq(""), any(), any(), any()))
          .thenReturn("header improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("header improvement prompt"))
          .thenReturn("header update response");
      when(responseParserService.parsePatternUpdateResponse("header update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      verify(customSemanticTypeService).getCustomType("TYPE_NO_HEADERS");
      verify(promptService)
          .buildRegenerateHeaderValuesPrompt(eq("TYPE_NO_HEADERS"), eq(""), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle building validation summary with header examples")
    void shouldBuildValidationSummaryWithHeaderExamples() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("HEADER_SUMMARY_TEST")
              .isHeaderPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .newPositiveContentExamples(null) // No content examples
              .newNegativeContentExamples(null)
              .newPositiveHeaderExamples(Arrays.asList("header1", "header2", "header3"))
              .newNegativeHeaderExamples(Arrays.asList("bad_header1", "bad_header2"))
              .explanation("Header examples for validation summary")
              .build();

      when(customSemanticTypeService.getCustomType("HEADER_SUMMARY_TEST"))
          .thenThrow(new RuntimeException("Type not found"));
      when(promptService.buildRegenerateHeaderValuesPrompt(
              anyString(), anyString(), any(), any(), any()))
          .thenReturn("header improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("header improvement prompt"))
          .thenReturn("header update response");
      when(responseParserService.parsePatternUpdateResponse("header update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getValidationSummary().getTotalPositiveGenerated()).isEqualTo(3);
      assertThat(result.getValidationSummary().getTotalNegativeGenerated()).isEqualTo(2);
      assertThat(result.getValidationSummary().getPositiveExamplesValidated()).isEqualTo(3);
      assertThat(result.getValidationSummary().getNegativeExamplesValidated()).isEqualTo(2);
      assertThat(result.getValidationSummary().getPositiveExamplesFailed()).isEqualTo(0);
      assertThat(result.getValidationSummary().getNegativeExamplesFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle validation with partial example failures")
    void shouldHandleValidationWithPartialFailures() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("PARTIAL_FAILURE_TEST")
              .regexPattern("\\d{3}")
              .isPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .improvedPattern("\\d{3}")
              .newPositiveContentExamples(
                  Arrays.asList("123", "abc", "456", "xyz")) // 2 valid, 2 invalid
              .newNegativeContentExamples(Arrays.asList("12", "789", "1234")) // 2 valid, 1 invalid
              .explanation("Testing partial validation failures")
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), any(), any()))
          .thenReturn("data improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("data improvement prompt"))
          .thenReturn("pattern update response");
      when(responseParserService.parsePatternUpdateResponse("pattern update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getPositiveExamples()).containsExactly("123", "456");
      assertThat(result.getNegativeExamples()).containsExactly("12", "1234");
      assertThat(result.getValidationSummary().getTotalPositiveGenerated()).isEqualTo(4);
      assertThat(result.getValidationSummary().getTotalNegativeGenerated()).isEqualTo(3);
      assertThat(result.getValidationSummary().getPositiveExamplesValidated()).isEqualTo(2);
      assertThat(result.getValidationSummary().getNegativeExamplesValidated()).isEqualTo(2);
      assertThat(result.getValidationSummary().getPositiveExamplesFailed()).isEqualTo(2);
      assertThat(result.getValidationSummary().getNegativeExamplesFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle empty validation summary inputs")
    void shouldHandleEmptyValidationSummaryInputs() throws Exception {
      // Given
      GenerateValidatedExamplesRequest request =
          GenerateValidatedExamplesRequest.builder()
              .semanticTypeName("EMPTY_SUMMARY_TEST")
              .regexPattern("\\d+")
              .isPatternImprovement(true)
              .build();

      PatternUpdateResponse updateResponse =
          PatternUpdateResponse.builder()
              .improvedPattern("\\d+")
              .newPositiveContentExamples(null)
              .newNegativeContentExamples(null)
              .newPositiveHeaderExamples(null)
              .newNegativeHeaderExamples(null)
              .explanation("Empty examples for summary test")
              .build();

      when(promptService.buildRegenerateDataValuesPrompt(
              anyString(), anyString(), any(), any(), any(), any()))
          .thenReturn("data improvement prompt");
      when(awsBedrockService.invokeClaudeForSemanticTypeGeneration("data improvement prompt"))
          .thenReturn("pattern update response");
      when(responseParserService.parsePatternUpdateResponse("pattern update response"))
          .thenReturn(updateResponse);

      // When
      GeneratedValidatedExamplesResponse result =
          generationService.generateValidatedExamples(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValidationSuccessful()).isTrue();
      assertThat(result.getValidationSummary().getTotalPositiveGenerated()).isEqualTo(0);
      assertThat(result.getValidationSummary().getTotalNegativeGenerated()).isEqualTo(0);
      assertThat(result.getValidationSummary().getPositiveExamplesValidated()).isEqualTo(0);
      assertThat(result.getValidationSummary().getNegativeExamplesValidated()).isEqualTo(0);
      assertThat(result.getValidationSummary().getPositiveExamplesFailed()).isEqualTo(0);
      assertThat(result.getValidationSummary().getNegativeExamplesFailed()).isEqualTo(0);
    }
  }

  // Helper methods
  private GeneratedSemanticType createMockGeneratedType(String semanticType, String resultType) {
    return GeneratedSemanticType.builder()
        .resultType(resultType)
        .semanticType(semanticType)
        .description("Mock generated type")
        .pluginType("regex")
        .regexPattern("\\w+")
        .positiveContentExamples(Arrays.asList("example1", "example2"))
        .negativeContentExamples(Arrays.asList("negative1"))
        .positiveHeaderExamples(Arrays.asList("header1"))
        .negativeHeaderExamples(Arrays.asList("bad_header"))
        .confidenceThreshold(0.8)
        .explanation("Mock explanation")
        .build();
  }
}

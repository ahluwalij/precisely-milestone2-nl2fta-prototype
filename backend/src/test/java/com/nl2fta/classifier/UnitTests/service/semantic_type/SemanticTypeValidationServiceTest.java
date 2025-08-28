package com.nl2fta.classifier.service.semantic_type.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobber.fta.PluginDefinition;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

/**
 * Comprehensive unit tests for SemanticTypeValidationService. Tests all validation methods,
 * regex/list validation, error handling, and edge cases following Google Java Testing Documentation
 * principles.
 *
 * <p>Coverage target: 95%+ for this high-priority service (845 missed instructions).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Semantic Type Validation Service Tests")
class SemanticTypeValidationServiceTest {

  @Mock private SemanticTypePluginService mockPluginService;

  @InjectMocks private SemanticTypeValidationService validationService;

  private CustomSemanticType regexCustomType;
  private CustomSemanticType listCustomType;
  private List<String> validPositiveExamples;
  private List<String> validNegativeExamples;
  private List<PluginDefinition> testBuiltInPlugins;

  @BeforeEach
  void setUp() {
    regexCustomType = createRegexCustomSemanticType();
    listCustomType = createListCustomSemanticType();
    validPositiveExamples = Arrays.asList("E12345P", "E67890F", "E11111P");
    validNegativeExamples = Arrays.asList("A12345P", "E1234P", "E12345X", "invalid");
    testBuiltInPlugins = createTestBuiltInPlugins();
  }

  @Nested
  @DisplayName("Validate Semantic Type Tests")
  class ValidateSemanticTypeTests {

    @Test
    @DisplayName("Should validate regex type successfully with all positive examples matching")
    void shouldValidateRegexTypeSuccessfullyWithAllPositiveExamplesMatching() {
      // Given
      List<String> positiveExamples = Arrays.asList("E12345P", "E67890F");
      List<String> negativeExamples = Arrays.asList("A12345P", "E1234P");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isTrue();
      assertThat(result.getError()).isNull();
      assertThat(result.getPositiveExampleResults()).hasSize(2);
      assertThat(result.getNegativeExampleResults()).hasSize(2);

      // Verify all positive examples match
      result
          .getPositiveExampleResults()
          .forEach(
              validation -> {
                assertThat(validation.isMatches()).isTrue();
                assertThat(validation.getReason()).isEqualTo("Correctly matched by pattern");
              });

      // Verify all negative examples don't match
      result
          .getNegativeExampleResults()
          .forEach(
              validation -> {
                assertThat(validation.isMatches()).isFalse();
                assertThat(validation.getReason()).isEqualTo("Correctly rejected by pattern");
              });
    }

    @Test
    @DisplayName("Should fail validation when positive examples don't match regex")
    void shouldFailValidationWhenPositiveExamplesDontMatchRegex() {
      // Given
      List<String> positiveExamples = Arrays.asList("E12345P", "INVALID");
      List<String> negativeExamples = Arrays.asList("A12345P");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError())
          .contains("Pattern validation failed: 1/2 positive examples matched");

      // First positive should match, second should not
      assertThat(result.getPositiveExampleResults().get(0).isMatches()).isTrue();
      assertThat(result.getPositiveExampleResults().get(1).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(1).getReason())
          .contains("Failed to match pattern");
    }

    @Test
    @DisplayName("Should fail validation when negative examples match regex")
    void shouldFailValidationWhenNegativeExamplesMatchRegex() {
      // Given
      List<String> positiveExamples = Arrays.asList("E12345P");
      List<String> negativeExamples = Arrays.asList("A12345P", "E67890F"); // Second one matches!

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).contains("Pattern validation failed");
      assertThat(result.getError()).contains("1/2 negative examples correctly rejected");

      // First negative should not match, second should match (incorrectly)
      assertThat(result.getNegativeExampleResults().get(0).isMatches()).isFalse();
      assertThat(result.getNegativeExampleResults().get(1).isMatches()).isTrue();
      assertThat(result.getNegativeExampleResults().get(1).getReason())
          .contains("Incorrectly matched by pattern");
    }

    @Test
    @DisplayName("Should validate list type successfully")
    void shouldValidateListTypeSuccessfully() {
      // Given
      List<String> positiveExamples = Arrays.asList("admin", "user");
      List<String> negativeExamples = Arrays.asList("invalid", "unknown");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              listCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isTrue();
      assertThat(result.getError()).isNull();

      // Verify positive examples are found (exact or case-insensitive per backend logic)
      result
          .getPositiveExampleResults()
          .forEach(
              validation -> {
                assertThat(validation.isMatches()).isTrue();
              });

      // Verify negative examples are not found in list
      result
          .getNegativeExampleResults()
          .forEach(
              validation -> {
                assertThat(validation.isMatches()).isFalse();
                assertThat(validation.getReason()).isEqualTo("Correctly not in list");
              });
    }

    @Test
    @DisplayName("Should fail list validation when positive examples not in list")
    void shouldFailListValidationWhenPositiveExamplesNotInList() {
      // Given
      List<String> positiveExamples = Arrays.asList("admin", "invalid");
      List<String> negativeExamples = Arrays.asList("unknown");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              listCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError())
          .contains("List validation failed: 1/2 positive examples matched");
    }

    @Test
    @DisplayName("Should fail list validation when negative examples found in list")
    void shouldFailListValidationWhenNegativeExamplesFoundInList() {
      // Given
      List<String> positiveExamples = Arrays.asList("admin");
      List<String> negativeExamples = Arrays.asList("invalid", "user"); // "user" is in list!

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              listCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).contains("List validation failed");
      assertThat(result.getError()).contains("1/2 negative examples correctly rejected");
    }

    @Test
    @DisplayName("Should handle unsupported plugin type")
    void shouldHandleUnsupportedPluginType() {
      // Given
      CustomSemanticType unsupportedType = createCustomSemanticType("unsupported");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              unsupportedType, Collections.emptyList(), Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).isEqualTo("Unsupported plugin type: unsupported");
    }

    @Test
    @DisplayName("Should handle null or empty examples gracefully")
    void shouldHandleNullOrEmptyExamplesGracefully() {
      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, Collections.emptyList(), Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isTrue();
      assertThat(result.getPositiveExampleResults()).isEmpty();
      assertThat(result.getNegativeExampleResults()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Regex Type Validation Tests")
  class RegexTypeValidationTests {

    @Test
    @DisplayName("Should handle regex with no pattern")
    void shouldHandleRegexWithNoPattern() {
      // Given
      CustomSemanticType typeWithoutPattern = createRegexCustomSemanticTypeWithoutPattern();

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              typeWithoutPattern, validPositiveExamples, validNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).isEqualTo("No regex pattern found in semantic type");
    }

    @Test
    @DisplayName("Should handle invalid regex pattern syntax")
    void shouldHandleInvalidRegexPatternSyntax() {
      // Given
      CustomSemanticType typeWithInvalidRegex = createRegexCustomSemanticTypeWithInvalidPattern();

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              typeWithInvalidRegex, validPositiveExamples, validNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).startsWith("Invalid regex pattern:");
    }

    @Test
    @DisplayName("Should handle null and empty examples in regex validation")
    void shouldHandleNullAndEmptyExamplesInRegexValidation() {
      // Given
      List<String> examplesWithNullAndEmpty = Arrays.asList("E12345P", null, "", "  ");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, examplesWithNullAndEmpty, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse(); // Should fail because of null/empty examples
      assertThat(result.getPositiveExampleResults()).hasSize(4);

      // First should match
      assertThat(result.getPositiveExampleResults().get(0).isMatches()).isTrue();

      // Others should not match with appropriate reasons
      assertThat(result.getPositiveExampleResults().get(1).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(1).getReason())
          .isEqualTo("Empty or null example");

      assertThat(result.getPositiveExampleResults().get(2).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(2).getReason())
          .isEqualTo("Empty or null example");

      assertThat(result.getPositiveExampleResults().get(3).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(3).getReason())
          .isEqualTo("Empty or null example");
    }

    @Test
    @DisplayName("Should generate suggested examples for failed positive examples")
    void shouldGenerateSuggestedExamplesForFailedPositiveExamples() {
      // Given
      List<String> failingPositiveExamples = Arrays.asList("INVALID1", "INVALID2");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, failingPositiveExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getSuggestedPositiveExamples()).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate suggested examples for failed negative examples")
    void shouldGenerateSuggestedExamplesForFailedNegativeExamples() {
      // Given
      List<String> failingNegativeExamples = Arrays.asList("E12345P", "E67890F"); // These match!

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, Collections.emptyList(), failingNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getSuggestedNegativeExamples()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle regex runtime exception during matching")
    void shouldHandleRegexRuntimeExceptionDuringMatching() {
      // Given - Create a pattern that might cause issues with certain inputs
      CustomSemanticType problematicRegexType =
          createRegexCustomSemanticTypeWithPattern(".*\\p{IsLatin}.*");
      List<String> problematicExamples = Arrays.asList("test\uD800"); // Invalid surrogate

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              problematicRegexType, problematicExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      // Should handle gracefully even if regex throws exception
      assertThat(result.getPositiveExampleResults()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("List Type Validation Tests")
  class ListTypeValidationTests {

    @Test
    @DisplayName("Should handle list type with no content")
    void shouldHandleListTypeWithNoContent() {
      // Given
      CustomSemanticType typeWithoutContent = createListCustomSemanticTypeWithoutContent();

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              typeWithoutContent, validPositiveExamples, validNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).isEqualTo("No list values found in semantic type");
    }

    @Test
    @DisplayName("Should handle list type with empty values")
    void shouldHandleListTypeWithEmptyValues() {
      // Given
      CustomSemanticType typeWithEmptyValues = createListCustomSemanticTypeWithEmptyValues();

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              typeWithEmptyValues, validPositiveExamples, validNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isValid()).isFalse();
      assertThat(result.getError()).isEqualTo("No list values found in semantic type");
    }

    @Test
    @DisplayName("Should validate with case-sensitive list matching")
    void shouldValidateWithCaseSensitiveListMatching() {
      // Given
      List<String> positiveExamples = Arrays.asList("admin", "ADMIN"); // Different cases
      List<String> negativeExamples = Arrays.asList("invalid");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              listCustomType, positiveExamples, negativeExamples);

      // Then
      assertThat(result).isNotNull();
      // Backend treats list matching case-insensitively (FTA uppercases literals)
      assertThat(result.isValid()).isTrue();
      assertThat(result.getPositiveExampleResults().get(0).isMatches()).isTrue();
      assertThat(result.getPositiveExampleResults().get(1).isMatches()).isTrue();
      assertThat(result.getPositiveExampleResults().get(1).getReason())
          .contains("case-insensitive");
    }
  }

  @Nested
  @DisplayName("Validate Custom Type Tests")
  class ValidateCustomTypeTests {

    @Test
    @DisplayName("Should validate valid regex custom type")
    void shouldValidateValidRegexCustomType() {
      // When & Then - Should not throw exception
      validationService.validateCustomType(regexCustomType);
    }

    @Test
    @DisplayName("Should validate valid list custom type")
    void shouldValidateValidListCustomType() {
      // When & Then - Should not throw exception
      validationService.validateCustomType(listCustomType);
    }

    @Test
    @DisplayName("Should throw exception for null custom type")
    void shouldThrowExceptionForNullCustomType() {
      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Custom semantic type cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null semantic type name")
    void shouldThrowExceptionForNullSemanticTypeName() {
      // Given
      CustomSemanticType typeWithNullName = createCustomSemanticType("regex");
      typeWithNullName.setSemanticType(null);

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(typeWithNullName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type name cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty semantic type name")
    void shouldThrowExceptionForEmptySemanticTypeName() {
      // Given
      CustomSemanticType typeWithEmptyName = createCustomSemanticType("regex");
      typeWithEmptyName.setSemanticType("  ");

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(typeWithEmptyName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type name cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null plugin type")
    void shouldThrowExceptionForNullPluginType() {
      // Given
      CustomSemanticType typeWithNullPluginType = createCustomSemanticType("regex");
      typeWithNullPluginType.setPluginType(null);

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(typeWithNullPluginType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Plugin type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty plugin type")
    void shouldThrowExceptionForEmptyPluginType() {
      // Given
      CustomSemanticType typeWithEmptyPluginType = createCustomSemanticType("regex");
      typeWithEmptyPluginType.setPluginType("  ");

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(typeWithEmptyPluginType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Plugin type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for unsupported plugin type")
    void shouldThrowExceptionForUnsupportedPluginType() {
      // Given
      CustomSemanticType typeWithUnsupportedPluginType = createCustomSemanticType("unsupported");

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(typeWithUnsupportedPluginType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported plugin type: unsupported");
    }

    @Test
    @DisplayName("Should throw exception for regex type without valid locales")
    void shouldThrowExceptionForRegexTypeWithoutValidLocales() {
      // Given
      CustomSemanticType regexTypeWithoutLocales = createCustomSemanticType("regex");
      regexTypeWithoutLocales.setValidLocales(null);

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithoutLocales))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Regex semantic type must have at least one valid locale");
    }

    @Test
    @DisplayName("Should throw exception for regex type with empty valid locales")
    void shouldThrowExceptionForRegexTypeWithEmptyValidLocales() {
      // Given
      CustomSemanticType regexTypeWithEmptyLocales = createCustomSemanticType("regex");
      regexTypeWithEmptyLocales.setValidLocales(Collections.emptyList());

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithEmptyLocales))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Regex semantic type must have at least one valid locale");
    }

    @Test
    @DisplayName("Should throw exception for regex type without match entries")
    void shouldThrowExceptionForRegexTypeWithoutMatchEntries() {
      // Given
      CustomSemanticType regexTypeWithoutMatchEntries =
          createRegexCustomSemanticTypeWithoutMatchEntries();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithoutMatchEntries))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Regex semantic type must have at least one match entry");
    }

    @Test
    @DisplayName("Should throw exception for regex type with null regex pattern")
    void shouldThrowExceptionForRegexTypeWithNullRegexPattern() {
      // Given
      CustomSemanticType regexTypeWithNullPattern = createRegexCustomSemanticTypeWithNullPattern();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithNullPattern))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Regex pattern cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for regex type with empty regex pattern")
    void shouldThrowExceptionForRegexTypeWithEmptyRegexPattern() {
      // Given
      CustomSemanticType regexTypeWithEmptyPattern =
          createRegexCustomSemanticTypeWithEmptyPattern();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithEmptyPattern))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Regex pattern cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for regex type with invalid regex pattern syntax")
    void shouldThrowExceptionForRegexTypeWithInvalidRegexPatternSyntax() {
      // Given
      CustomSemanticType regexTypeWithInvalidPattern =
          createRegexCustomSemanticTypeWithInvalidPattern();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(regexTypeWithInvalidPattern))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("Invalid regex pattern: Unclosed character class near index 7");
    }

    @Test
    @DisplayName("Should throw exception for list type without content")
    void shouldThrowExceptionForListTypeWithoutContent() {
      // Given
      CustomSemanticType listTypeWithoutContent = createListCustomSemanticTypeWithoutContent();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(listTypeWithoutContent))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("List semantic type must have content values");
    }

    @Test
    @DisplayName("Should throw exception for list type with null content values")
    void shouldThrowExceptionForListTypeWithNullContentValues() {
      // Given
      CustomSemanticType listTypeWithNullValues = createListCustomSemanticTypeWithNullValues();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(listTypeWithNullValues))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("List semantic type must have content values");
    }

    @Test
    @DisplayName("Should throw exception for list type with empty content values")
    void shouldThrowExceptionForListTypeWithEmptyContentValues() {
      // Given
      CustomSemanticType listTypeWithEmptyValues = createListCustomSemanticTypeWithEmptyValues();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(listTypeWithEmptyValues))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("List semantic type must have content values");
    }

    @Test
    @DisplayName("Should throw exception for list type with null value in list")
    void shouldThrowExceptionForListTypeWithNullValueInList() {
      // Given
      CustomSemanticType listTypeWithNullValue = createListCustomSemanticTypeWithNullValueInList();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(listTypeWithNullValue))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("List values cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for list type with empty value in list")
    void shouldThrowExceptionForListTypeWithEmptyValueInList() {
      // Given
      CustomSemanticType listTypeWithEmptyValue =
          createListCustomSemanticTypeWithEmptyValueInList();

      // When & Then
      assertThatThrownBy(() -> validationService.validateCustomType(listTypeWithEmptyValue))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("List values cannot be null or empty");
    }
  }

  @Nested
  @DisplayName("Is Built-in Type Tests")
  class IsBuiltInTypeTests {

    @Test
    @DisplayName("Should return true for built-in type")
    void shouldReturnTrueForBuiltInType() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);

      // When
      boolean result = validationService.isBuiltInType("BuiltInType1");

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-built-in type")
    void shouldReturnFalseForNonBuiltInType() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);

      // When
      boolean result = validationService.isBuiltInType("CustomType");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when plugin service throws exception")
    void shouldReturnFalseWhenPluginServiceThrowsException() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins())
          .thenThrow(new RuntimeException("Plugin load error"));

      // When
      boolean result = validationService.isBuiltInType("AnyType");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null type name gracefully")
    void shouldHandleNullTypeNameGracefully() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);

      // When
      boolean result = validationService.isBuiltInType(null);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle empty type name gracefully")
    void shouldHandleEmptyTypeNameGracefully() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);

      // When
      boolean result = validationService.isBuiltInType("");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle empty built-in plugins list")
    void shouldHandleEmptyBuiltInPluginsList() throws Exception {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(Collections.emptyList());

      // When
      boolean result = validationService.isBuiltInType("AnyType");

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("Generate Suggested Examples Tests")
  class GenerateSuggestedExamplesTests {

    @Test
    @DisplayName("Should generate positive suggestions for digit patterns")
    void shouldGeneratePositiveSuggestionsForDigitPatterns() {
      // Given
      List<String> failingPositiveExamples = Arrays.asList("INVALID");
      CustomSemanticType digitRegexType = createRegexCustomSemanticTypeWithPattern("\\d+");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              digitRegexType, failingPositiveExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSuggestedPositiveExamples()).isNotEmpty();
      assertThat(result.getSuggestedPositiveExamples())
          .anyMatch(suggestion -> suggestion.contains("digits"));
    }

    @Test
    @DisplayName("Should generate positive suggestions for uppercase patterns")
    void shouldGeneratePositiveSuggestionsForUppercasePatterns() {
      // Given
      List<String> failingPositiveExamples = Arrays.asList("lowercase");
      CustomSemanticType uppercaseRegexType = createRegexCustomSemanticTypeWithPattern("[A-Z]+");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              uppercaseRegexType, failingPositiveExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSuggestedPositiveExamples()).isNotEmpty();
      assertThat(result.getSuggestedPositiveExamples())
          .anyMatch(suggestion -> suggestion.contains("uppercase"));
    }

    @Test
    @DisplayName("Should generate positive suggestions for email patterns")
    void shouldGeneratePositiveSuggestionsForEmailPatterns() {
      // Given
      List<String> failingPositiveExamples = Arrays.asList("notanemail");
      CustomSemanticType emailRegexType = createRegexCustomSemanticTypeWithPattern(".*@.*");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              emailRegexType, failingPositiveExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSuggestedPositiveExamples()).isNotEmpty();
      assertThat(result.getSuggestedPositiveExamples())
          .anyMatch(suggestion -> suggestion.contains("@"));
    }

    @Test
    @DisplayName("Should generate negative suggestions")
    void shouldGenerateNegativeSuggestions() {
      // Given
      List<String> failingNegativeExamples = Arrays.asList("E12345P"); // This matches!

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, Collections.emptyList(), failingNegativeExamples);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSuggestedNegativeExamples()).isNotEmpty();
      assertThat(result.getSuggestedNegativeExamples())
          .contains("Random text that shouldn't match");
      assertThat(result.getSuggestedNegativeExamples()).contains("12345");
      assertThat(result.getSuggestedNegativeExamples()).contains("!@#$%");
    }

    @Test
    @DisplayName("Should limit suggested examples to 3")
    void shouldLimitSuggestedExamplesTo3() {
      // Given
      List<String> failingPositiveExamples = Arrays.asList("fail");
      CustomSemanticType complexRegexType =
          createRegexCustomSemanticTypeWithPattern("\\d+[A-Z]+@.*");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              complexRegexType, failingPositiveExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSuggestedPositiveExamples()).hasSizeLessThanOrEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling Tests")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle very large example lists")
    void shouldHandleVeryLargeExampleLists() {
      // Given
      List<String> largePositiveList = new ArrayList<>();
      List<String> largeNegativeList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largePositiveList.add("E" + String.format("%05d", i) + "P");
        largeNegativeList.add("A" + String.format("%05d", i) + "X");
      }

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, largePositiveList, largeNegativeList);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getPositiveExampleResults()).hasSize(1000);
      assertThat(result.getNegativeExampleResults()).hasSize(1000);
    }

    @Test
    @DisplayName("Should handle unicode characters in examples")
    void shouldHandleUnicodeCharactersInExamples() {
      // Given
      List<String> unicodeExamples = Arrays.asList("E12345P", "É12345Ñ", "测试", "🚀123");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, unicodeExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getPositiveExampleResults()).hasSize(4);
      // Only the first should match the pattern
      assertThat(result.getPositiveExampleResults().get(0).isMatches()).isTrue();
      assertThat(result.getPositiveExampleResults().get(1).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(2).isMatches()).isFalse();
      assertThat(result.getPositiveExampleResults().get(3).isMatches()).isFalse();
    }

    @Test
    @DisplayName("Should handle very long example strings")
    void shouldHandleVeryLongExampleStrings() {
      // Given
      StringBuilder longString = new StringBuilder();
      for (int i = 0; i < 10000; i++) {
        longString.append("a");
      }
      List<String> longExamples = Arrays.asList(longString.toString());

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, longExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getPositiveExampleResults()).hasSize(1);
      assertThat(result.getPositiveExampleResults().get(0).isMatches()).isFalse();
    }

    @Test
    @DisplayName("Should handle whitespace-only examples")
    void shouldHandleWhitespaceOnlyExamples() {
      // Given
      List<String> whitespaceExamples = Arrays.asList(" ", "\t", "\n", "\r\n", "   ");

      // When
      SemanticTypeValidationService.ValidationResult result =
          validationService.validateSemanticType(
              regexCustomType, whitespaceExamples, Collections.emptyList());

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getPositiveExampleResults()).hasSize(5);
      result
          .getPositiveExampleResults()
          .forEach(
              validation -> {
                assertThat(validation.isMatches()).isFalse();
                assertThat(validation.getReason()).isEqualTo("Empty or null example");
              });
    }
  }

  // Helper methods for creating test data

  private CustomSemanticType createRegexCustomSemanticType() {
    return createRegexCustomSemanticTypeWithPattern("E\\d{5}[PF]");
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithPattern(String pattern) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestRegexType");
    type.setDescription("Test regex semantic type");
    type.setPluginType("regex");

    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");

    CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
    matchEntry.setRegExpReturned(pattern);
    matchEntry.setIsRegExpComplete(true);

    locale.setMatchEntries(Arrays.asList(matchEntry));
    type.setValidLocales(Arrays.asList(locale));

    return type;
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithoutPattern() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestRegexType");
    type.setDescription("Test regex semantic type");
    type.setPluginType("regex");

    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");

    // No match entries
    locale.setMatchEntries(Collections.emptyList());
    type.setValidLocales(Arrays.asList(locale));

    return type;
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithInvalidPattern() {
    return createRegexCustomSemanticTypeWithPattern("[invalid"); // Unclosed bracket
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithoutMatchEntries() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestRegexType");
    type.setDescription("Test regex semantic type");
    type.setPluginType("regex");

    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");
    locale.setMatchEntries(null);

    type.setValidLocales(Arrays.asList(locale));

    return type;
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithNullPattern() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestRegexType");
    type.setDescription("Test regex semantic type");
    type.setPluginType("regex");

    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");

    CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
    matchEntry.setRegExpReturned(null);

    locale.setMatchEntries(Arrays.asList(matchEntry));
    type.setValidLocales(Arrays.asList(locale));

    return type;
  }

  private CustomSemanticType createRegexCustomSemanticTypeWithEmptyPattern() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestRegexType");
    type.setDescription("Test regex semantic type");
    type.setPluginType("regex");

    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");

    CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
    matchEntry.setRegExpReturned("  ");

    locale.setMatchEntries(Arrays.asList(matchEntry));
    type.setValidLocales(Arrays.asList(locale));

    return type;
  }

  private CustomSemanticType createListCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setValues(Arrays.asList("admin", "user", "guest"));
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createListCustomSemanticTypeWithoutContent() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");
    type.setContent(null);

    return type;
  }

  private CustomSemanticType createListCustomSemanticTypeWithNullValues() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setValues(null);
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createListCustomSemanticTypeWithEmptyValues() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setValues(Collections.emptyList());
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createListCustomSemanticTypeWithNullValueInList() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setValues(Arrays.asList("admin", null, "guest"));
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createListCustomSemanticTypeWithEmptyValueInList() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestListType");
    type.setDescription("Test list semantic type");
    type.setPluginType("list");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setValues(Arrays.asList("admin", "  ", "guest"));
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createCustomSemanticType(String pluginType) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestType");
    type.setDescription("Test semantic type");
    type.setPluginType(pluginType);
    return type;
  }

  private List<PluginDefinition> createTestBuiltInPlugins() {
    PluginDefinition plugin1 = new PluginDefinition();
    plugin1.semanticType = "BuiltInType1";

    PluginDefinition plugin2 = new PluginDefinition();
    plugin2.semanticType = "BuiltInType2";

    return Arrays.asList(plugin1, plugin2);
  }
}

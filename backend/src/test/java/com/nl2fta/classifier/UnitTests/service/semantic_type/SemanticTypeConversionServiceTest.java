package com.nl2fta.classifier.service.semantic_type.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.HeaderPattern;
import com.nl2fta.classifier.service.semantic_type.SemanticTypeConversionService;

@DisplayName("Semantic Type Conversion Service Tests")
class SemanticTypeConversionServiceTest {

  private SemanticTypeConversionService conversionService;

  @BeforeEach
  void setUp() {
    conversionService = new SemanticTypeConversionService();
  }

  @Nested
  @DisplayName("Convert To Custom Type Tests")
  class ConvertToCustomTypeTests {

    @Test
    @DisplayName("Should convert regex type with header patterns")
    void shouldConvertRegexTypeWithHeaderPatterns() {
      // Arrange
      GeneratedSemanticType generated = new GeneratedSemanticType();
      generated.setSemanticType("USER.ID");
      generated.setDescription("User identifier");
      generated.setPluginType("regex");
      generated.setRegexPattern("^USR-\\d{6}$");
      generated.setConfidenceThreshold(0.95);
      generated.setPriority(1500);

      List<HeaderPattern> headerPatterns =
          Arrays.asList(
              createHeaderPattern("(?i)user.*id", 95), createHeaderPattern("(?i)userid", 90));
      generated.setHeaderPatterns(headerPatterns);

      // Act
      CustomSemanticType result = conversionService.convertToCustomType(generated);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("USER.ID");
      assertThat(result.getDescription()).isEqualTo("User identifier");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getThreshold()).isEqualTo(95);
      // Priority mirrors the generated value in current implementation
      assertThat(result.getPriority()).isEqualTo(1500);
      assertThat(result.getContent()).isNull(); // Regex types don't have content

      assertThat(result.getValidLocales()).hasSize(1);
      assertThat(result.getValidLocales().get(0).getLocaleTag()).isEqualTo("*");
      assertThat(result.getValidLocales().get(0).getHeaderRegExps()).hasSize(2);
      assertThat(result.getValidLocales().get(0).getHeaderRegExps().get(0).getRegExp())
          .isEqualTo("(?i)user.*id");
      assertThat(result.getValidLocales().get(0).getHeaderRegExps().get(0).getConfidence())
          .isEqualTo(95);
      assertThat(result.getValidLocales().get(0).getHeaderRegExps().get(0).getMandatory()).isTrue();
    }

    @Test
    @DisplayName("Should convert list type with values")
    void shouldConvertListTypeWithValues() {
      // Arrange
      GeneratedSemanticType generated = new GeneratedSemanticType();
      generated.setSemanticType("STATUS.ACTIVE");
      generated.setDescription("Active status values");
      generated.setPluginType("list");
      generated.setListValues(Arrays.asList("ACTIVE", "ENABLED", "ON"));
      generated.setConfidenceThreshold(0.90);

      // Act
      CustomSemanticType result = conversionService.convertToCustomType(generated);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("STATUS.ACTIVE");
      assertThat(result.getPluginType()).isEqualTo("list");
      assertThat(result.getThreshold()).isEqualTo(90);
      // Priority may be null if not provided by generated result
      assertThat(result.getPriority() == null || result.getPriority() >= 0).isTrue();

      assertThat(result.getContent()).isNotNull();
      assertThat(result.getContent().getType()).isEqualTo("inline");
      assertThat(result.getContent().getValues()).containsExactly("ACTIVE", "ENABLED", "ON");
    }

    @Test
    @DisplayName("Should convert type without header patterns")
    void shouldConvertTypeWithoutHeaderPatterns() {
      // Arrange
      GeneratedSemanticType generated = new GeneratedSemanticType();
      generated.setSemanticType("SIMPLE.TYPE");
      generated.setDescription("Simple type");
      generated.setPluginType("regex");
      generated.setConfidenceThreshold(0.85);

      // Act
      CustomSemanticType result = conversionService.convertToCustomType(generated);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getValidLocales()).isNull();
    }
  }

  @Nested
  @DisplayName("Create Combined Header Pattern Tests")
  class CreateCombinedHeaderPatternTests {

    @Test
    @DisplayName("Should create combined pattern from multiple header patterns")
    void shouldCreateCombinedPatternFromMultiplePatterns() {
      // Arrange
      List<HeaderPattern> headerPatterns =
          Arrays.asList(
              createHeaderPattern("(?i)email", 95),
              createHeaderPattern("(?i)e-mail", 90),
              createHeaderPattern("(?i)mail", 85));

      // Act
      CustomSemanticType.HeaderRegExp result =
          conversionService.createCombinedHeaderPattern(headerPatterns);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getRegExp()).isEqualTo("(?i)email|(?i)e-mail|(?i)mail");
      assertThat(result.getConfidence()).isEqualTo(95);
      assertThat(result.getMandatory()).isTrue();
    }

    @Test
    @DisplayName("Should handle single header pattern")
    void shouldHandleSingleHeaderPattern() {
      // Arrange
      List<HeaderPattern> headerPatterns =
          Collections.singletonList(createHeaderPattern("(?i)single", 90));

      // Act
      CustomSemanticType.HeaderRegExp result =
          conversionService.createCombinedHeaderPattern(headerPatterns);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getRegExp()).isEqualTo("(?i)single");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should convert complex type with all fields")
    void shouldConvertComplexTypeWithAllFields() {
      // Arrange
      GeneratedSemanticType generated = new GeneratedSemanticType();
      generated.setSemanticType("COMPLEX.TYPE");
      generated.setDescription("A complex semantic type with all features");
      generated.setPluginType("regex");
      generated.setRegexPattern("^[A-Z]{3}-\\d{4}-[A-Z]{2}$");
      generated.setConfidenceThreshold(0.925);
      generated.setPriority(2000);

      List<HeaderPattern> headerPatterns =
          Arrays.asList(
              createHeaderPattern("(?i)complex.*type", 95),
              createHeaderPattern("(?i)cplx.*typ", 90),
              createHeaderPattern("(?i)ct", 85));
      generated.setHeaderPatterns(headerPatterns);

      // Act
      CustomSemanticType result = conversionService.convertToCustomType(generated);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("COMPLEX.TYPE");
      assertThat(result.getDescription()).isEqualTo("A complex semantic type with all features");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getThreshold()).isEqualTo(93); // 0.925 rounded
      assertThat(result.getPriority()).isGreaterThanOrEqualTo(2000);

      assertThat(result.getValidLocales()).hasSize(1);
      assertThat(result.getValidLocales().get(0).getHeaderRegExps()).hasSize(3);

      // Verify each header pattern is preserved separately
      assertThat(result.getValidLocales().get(0).getHeaderRegExps())
          .extracting(CustomSemanticType.HeaderRegExp::getRegExp)
          .containsExactly("(?i)complex.*type", "(?i)cplx.*typ", "(?i)ct");

      assertThat(result.getValidLocales().get(0).getHeaderRegExps())
          .extracting(CustomSemanticType.HeaderRegExp::getConfidence)
          .containsExactly(95, 90, 85);
    }

    @Test
    @DisplayName("Should handle list type with special characters in values")
    void shouldHandleListTypeWithSpecialCharacters() {
      // Arrange
      GeneratedSemanticType generated = new GeneratedSemanticType();
      generated.setSemanticType("SPECIAL.CHARS");
      generated.setDescription("List with special characters");
      generated.setPluginType("list");
      generated.setListValues(Arrays.asList("A,B", "C;D", "E|F"));
      generated.setConfidenceThreshold(0.85);

      // Act
      CustomSemanticType result = conversionService.convertToCustomType(generated);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getContent().getType()).isEqualTo("inline");
      assertThat(result.getContent().getValues()).containsExactly("A,B", "C;D", "E|F");
      // Note: Comma is used as delimiter, so values with commas might need escaping
    }
  }

  private HeaderPattern createHeaderPattern(String regExp, int confidence) {
    HeaderPattern pattern = new HeaderPattern();
    pattern.setRegExp(regExp);
    pattern.setConfidence(confidence);
    return pattern;
  }
}

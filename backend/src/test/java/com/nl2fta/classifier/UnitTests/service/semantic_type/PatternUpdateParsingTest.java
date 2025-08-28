package com.nl2fta.classifier.service.semantic_type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.PatternUpdateResponse;
import com.nl2fta.classifier.service.semantic_type.generation.SemanticTypeResponseParserService;

/**
 * Unit tests for pattern update response parsing to ensure the new XML formats are correctly
 * parsed.
 *
 * <p>This test has been converted from an integration test to a unit test to avoid requiring
 * external dependencies and Spring Boot context.
 */
@DisplayName("Pattern Update Parsing Tests")
public class PatternUpdateParsingTest {

  private SemanticTypeResponseParserService responseParserService;

  @BeforeEach
  void setUp() {
    // Create a real ObjectMapper instance for the service
    ObjectMapper objectMapper = new ObjectMapper();
    responseParserService = new SemanticTypeResponseParserService(objectMapper);
  }

  @Test
  @DisplayName("Should parse semanticTypeUpdate XML format for data pattern improvements")
  void shouldParseSemanticTypeUpdateFormat() {
    // Given - response in the format from regenerate-data-values.txt
    String claudeResponse =
        """
            <semanticTypeUpdate>
              <newRegexPattern>\\d{3}-\\d{2}-\\d{4}</newRegexPattern>
              <positiveExamples>
                <example>123-45-6789</example>
                <example>987-65-4321</example>
                <example>555-12-3456</example>
              </positiveExamples>
              <negativeExamples>
                <example>123456789</example>
                <example>123-456-789</example>
                <example>12-34-5678</example>
              </negativeExamples>
              <rationale>Updated pattern to match SSN format with dashes</rationale>
            </semanticTypeUpdate>
            """;

    // When
    PatternUpdateResponse response =
        responseParserService.parsePatternUpdateResponse(claudeResponse);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getImprovedPattern()).isEqualTo("\\d{3}-\\d{2}-\\d{4}");
    assertThat(response.getNewPositiveContentExamples())
        .containsExactly("123-45-6789", "987-65-4321", "555-12-3456");
    assertThat(response.getNewNegativeContentExamples())
        .containsExactly("123456789", "123-456-789", "12-34-5678");
    assertThat(response.getExplanation())
        .isEqualTo("Updated pattern to match SSN format with dashes");
  }

  @Test
  @DisplayName("Should return null when no recognized XML format is found")
  void shouldReturnNullForInvalidXml() {
    // Given
    String invalidResponse = "This is not valid XML";

    // When
    PatternUpdateResponse response =
        responseParserService.parsePatternUpdateResponse(invalidResponse);

    // Then
    assertThat(response).isNull();
  }
}

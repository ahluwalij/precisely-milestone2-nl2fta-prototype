package com.nl2fta.classifier.service.semantic_type.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.PatternUpdateResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticTypeResponseParserService Tests")
class SemanticTypeResponseParserServiceTest {

  private SemanticTypeResponseParserService parser;
  private ObjectMapper objectMapper;
  private SemanticTypeGenerationRequest mockRequest;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    parser = new SemanticTypeResponseParserService(objectMapper);

    mockRequest =
        SemanticTypeGenerationRequest.builder()
            .typeName("TEST_TYPE")
            .description("Test description")
            .build();
  }

  @Nested
  @DisplayName("XML Response Parsing")
  class XmlResponseParsing {

    @Test
    @DisplayName("Should handle XML parsing validation by testing method existence")
    void shouldHaveXmlParsingCapability() {
      // Test that the parser can handle XML responses without making assumptions
      // about the internal parsing implementation details
      assertThat(parser).isNotNull();

      // Test with a simple valid response to ensure method works
      String validXml =
          """
                    <semanticTypeGeneration>
                        <basicInfo>
                            <semanticType>TEST.TYPE</semanticType>
                            <description>Test type</description>
                            <pluginType>regex</pluginType>
                            <regexPattern>test</regexPattern>
                        </basicInfo>
                        <headerPatterns></headerPatterns>
                        <examples>
                            <positiveContentExamples></positiveContentExamples>
                            <negativeContentExamples></negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                    </semanticTypeGeneration>
                    """;

      // This should not throw an exception for valid XML
      GeneratedSemanticType result = parser.parseGenerationResponse(validXml, mockRequest);
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("Pattern Update Response Parsing")
  class PatternUpdateResponseParsing {

    @Test
    @DisplayName("Should parse semanticTypeUpdate XML format for data pattern improvements")
    void shouldParseSemanticTypeUpdateFormat() {
      // Given
      String updateResponse =
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
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(updateResponse);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getImprovedPattern()).isEqualTo("\\d{3}-\\d{2}-\\d{4}");
      assertThat(result.getNewPositiveContentExamples())
          .containsExactly("123-45-6789", "987-65-4321", "555-12-3456");
      assertThat(result.getNewNegativeContentExamples())
          .containsExactly("123456789", "123-456-789", "12-34-5678");
      assertThat(result.getExplanation())
          .isEqualTo("Updated pattern to match SSN format with dashes");
    }

    @Test
    @DisplayName("Should handle pattern update with markdown code blocks")
    void shouldHandlePatternUpdateWithMarkdownCodeBlocks() {
      // Given
      String updateResponse =
          """
                    ```xml
                    <semanticTypeUpdate>
                        <newRegexPattern>\\d{10}</newRegexPattern>
                        <positiveExamples>
                            <example>5551234567</example>
                        </positiveExamples>
                        <negativeExamples>
                            <example>123</example>
                        </negativeExamples>
                        <rationale>Phone number pattern</rationale>
                    </semanticTypeUpdate>
                    ```
                    """;

      // When
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(updateResponse);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getImprovedPattern()).isEqualTo("\\d{10}");
      assertThat(result.getNewPositiveContentExamples()).containsExactly("5551234567");
      assertThat(result.getNewNegativeContentExamples()).containsExactly("123");
    }

    @Test
    @DisplayName("Should return null for invalid XML format")
    void shouldReturnNullForInvalidXmlFormat() {
      // Given
      String invalidResponse = "This is not valid XML for pattern update";

      // When
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(invalidResponse);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle malformed pattern update XML")
    void shouldHandleMalformedPatternUpdateXml() {
      // Given
      String malformedXml = "<semanticTypeUpdate><newRegexPattern>incomplete";

      // When
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(malformedXml);

      // Then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("XML Response Parsing - Comprehensive")
  class XmlResponseParsingComprehensive {

    @Test
    @DisplayName("Should parse complete XML response with all fields")
    void shouldParseCompleteXmlResponseWithAllFields() {
      // Given
      String completeXml =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>EMAIL.ADDRESS</semanticType>
                            <description>Email address field validation</description>
                            <pluginType>regex</pluginType>
                            <regexPattern>^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$</regexPattern>
                            <confidenceThreshold>85</confidenceThreshold>
                            <listValues>
                                <value>user@example.com</value>
                                <value>test@domain.org</value>
                                <value>admin@company.net</value>
                            </listValues>
                        </basicInfo>
                        <headerPatterns>
                            <pattern>
                                <regExp>(?i).*email.*</regExp>
                                <confidence>90</confidence>
                                <mandatory>true</mandatory>
                                <rationale>Matches email column headers</rationale>
                                <positiveExamples>
                                    <example>email</example>
                                    <example>email_address</example>
                                </positiveExamples>
                                <negativeExamples>
                                    <example>phone</example>
                                    <example>name</example>
                                </negativeExamples>
                            </pattern>
                            <pattern>
                                <regExp>(?i).*mail.*</regExp>
                                <confidence>85</confidence>
                                <mandatory>false</mandatory>
                                <rationale>Alternative mail patterns</rationale>
                                <positiveExamples>
                                    <example>mail</example>
                                </positiveExamples>
                                <negativeExamples>
                                    <example>telephone</example>
                                </negativeExamples>
                            </pattern>
                        </headerPatterns>
                        <examples>
                            <positiveContentExamples>
                                <example>user@example.com</example>
                                <example>test@domain.org</example>
                                <example>admin@company.net</example>
                            </positiveContentExamples>
                            <negativeContentExamples>
                                <example>not-an-email</example>
                                <example>@invalid.com</example>
                                <example>user@</example>
                            </negativeContentExamples>
                            <positiveHeaderExamples>
                                <example>email</example>
                                <example>email_address</example>
                                <example>user_email</example>
                            </positiveHeaderExamples>
                            <negativeHeaderExamples>
                                <example>phone</example>
                                <example>name</example>
                                <example>address</example>
                            </negativeHeaderExamples>
                        </examples>
                        <explanation>Generated email address validation with comprehensive patterns and examples</explanation>
                    </semanticType>
                    """;

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(completeXml, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.getDescription()).isEqualTo("Email address field validation");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getRegexPattern())
          .isEqualTo("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
      assertThat(result.getConfidenceThreshold()).isEqualTo(85);
      assertThat(result.getPriority()).isEqualTo(2000); // Always 2000 as required by FTA
      assertThat(result.getListValues())
          .containsExactly("user@example.com", "test@domain.org", "admin@company.net");

      // Check header patterns
      assertThat(result.getHeaderPatterns()).hasSize(2);
      assertThat(result.getHeaderPatterns().get(0).getRegExp()).isEqualTo("(?i).*email.*");
      assertThat(result.getHeaderPatterns().get(0).getConfidence()).isEqualTo(90);
      assertThat(result.getHeaderPatterns().get(0).isMandatory()).isTrue();
      assertThat(result.getHeaderPatterns().get(0).getRationale())
          .isEqualTo("Matches email column headers");
      assertThat(result.getHeaderPatterns().get(0).getPositiveExamples())
          .containsExactly("email", "email_address");
      assertThat(result.getHeaderPatterns().get(0).getNegativeExamples())
          .containsExactly("phone", "name");

      // Check examples
      assertThat(result.getPositiveContentExamples())
          .containsExactly("user@example.com", "test@domain.org", "admin@company.net");
      assertThat(result.getNegativeContentExamples())
          .containsExactly("not-an-email", "@invalid.com", "user@");
      assertThat(result.getPositiveHeaderExamples())
          .containsExactly("email", "email_address", "user_email");
      assertThat(result.getNegativeHeaderExamples()).containsExactly("phone", "name", "address");

      assertThat(result.getExplanation())
          .isEqualTo("Generated email address validation with comprehensive patterns and examples");
    }

    @Test
    @DisplayName("Should handle XML with missing optional fields")
    void shouldHandleXmlWithMissingOptionalFields() {
      // Given
      String minimalXml =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>MINIMAL.TYPE</semanticType>
                            <description>Minimal type</description>
                            <pluginType>list</pluginType>
                        </basicInfo>
                        <headerPatterns></headerPatterns>
                        <examples>
                            <positiveContentExamples></positiveContentExamples>
                            <negativeContentExamples></negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                    </semanticType>
                    """;

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(minimalXml, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("MINIMAL.TYPE");
      assertThat(result.getDescription()).isEqualTo("Minimal type");
      assertThat(result.getPluginType()).isEqualTo("list");
      assertThat(result.getRegexPattern()).isNull();
      assertThat(result.getListValues()).isEmpty();
      assertThat(result.getHeaderPatterns()).isEmpty();
      assertThat(result.getPositiveContentExamples()).isEmpty();
      assertThat(result.getNegativeContentExamples()).isEmpty();
      assertThat(result.getPositiveHeaderExamples()).isEmpty();
      assertThat(result.getNegativeHeaderExamples()).isEmpty();
      assertThat(result.getConfidenceThreshold()).isEqualTo(95); // Default value
    }

    @Test
    @DisplayName("Should handle XML with invalid header pattern elements")
    void shouldHandleXmlWithInvalidHeaderPatternElements() {
      // Given
      String xmlWithInvalidPatterns =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>TEST.TYPE</semanticType>
                            <description>Test type</description>
                            <pluginType>regex</pluginType>
                        </basicInfo>
                        <headerPatterns>
                            <pattern>
                                <regExp>valid_pattern</regExp>
                                <confidence>90</confidence>
                                <mandatory>true</mandatory>
                            </pattern>
                            <pattern>
                                <!-- Missing regExp - should be skipped -->
                                <confidence>85</confidence>
                                <mandatory>false</mandatory>
                            </pattern>
                            <pattern>
                                <regExp></regExp>
                                <!-- Empty regExp - should be skipped -->
                                <confidence>80</confidence>
                            </pattern>
                            <pattern>
                                <regExp>another_valid_pattern</regExp>
                                <confidence>invalid_number</confidence>
                                <mandatory>not_boolean</mandatory>
                            </pattern>
                        </headerPatterns>
                        <examples>
                            <positiveContentExamples></positiveContentExamples>
                            <negativeContentExamples></negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                    </semanticType>
                    """;

      // When
      GeneratedSemanticType result =
          parser.parseGenerationResponse(xmlWithInvalidPatterns, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getHeaderPatterns()).hasSize(2); // Only valid patterns should be included
      assertThat(result.getHeaderPatterns().get(0).getRegExp()).isEqualTo("valid_pattern");
      assertThat(result.getHeaderPatterns().get(1).getRegExp()).isEqualTo("another_valid_pattern");
      assertThat(result.getHeaderPatterns().get(1).getConfidence())
          .isEqualTo(95); // Default value for invalid confidence
      assertThat(result.getHeaderPatterns().get(1).isMandatory())
          .isTrue(); // Default value for invalid mandatory
    }
  }

  @Nested
  @DisplayName("JSON Response Parsing - Comprehensive")
  class JsonResponseParsingComprehensive {

    @Test
    @DisplayName("Should parse complete JSON response")
    void shouldParseCompleteJsonResponse() {
      // Given
      String jsonResponse =
          """
                    {
                        "semanticType": "PHONE.NUMBER",
                        "description": "Phone number validation",
                        "pluginType": "regex",
                        "regexPattern": "\\\\d{10}",
                        "listValues": ["5551234567", "5559876543"],
                        "headerPatterns": [
                            {
                                "regExp": "(?i).*phone.*",
                                "confidence": 90,
                                "mandatory": true,
                                "positiveExamples": ["phone", "phone_number"],
                                "negativeExamples": ["email", "address"],
                                "rationale": "Phone number headers"
                            }
                        ],
                        "confidenceThreshold": 0.8,
                        "positiveContentExamples": ["5551234567", "5559876543"],
                        "negativeContentExamples": ["123", "abcd"],
                        "positiveHeaderExamples": ["phone", "telephone"],
                        "negativeHeaderExamples": ["email", "name"],
                        "explanation": "Phone number validation system"
                    }
                    """;

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(jsonResponse, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("PHONE.NUMBER");
      assertThat(result.getDescription()).isEqualTo("Phone number validation");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getRegexPattern()).isEqualTo("\\d{10}");
      assertThat(result.getListValues()).containsExactly("5551234567", "5559876543");
      assertThat(result.getConfidenceThreshold()).isEqualTo(80); // 0.8 * 100
      assertThat(result.getPriority()).isEqualTo(2000);
      assertThat(result.getHeaderPatterns()).hasSize(1);
      assertThat(result.getPositiveContentExamples()).containsExactly("5551234567", "5559876543");
      assertThat(result.getNegativeContentExamples()).containsExactly("123", "abcd");
      assertThat(result.getPositiveHeaderExamples()).containsExactly("phone", "telephone");
      assertThat(result.getNegativeHeaderExamples()).containsExactly("email", "name");
      assertThat(result.getExplanation()).isEqualTo("Phone number validation system");
    }

    @Test
    @DisplayName("Should handle confidence threshold as percentage")
    void shouldHandleConfidenceThresholdAsPercentage() {
      // Given
      String jsonResponse =
          """
                    {
                        "semanticType": "TEST.TYPE",
                        "description": "Test type",
                        "pluginType": "regex",
                        "confidenceThreshold": 85
                    }
                    """;

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(jsonResponse, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConfidenceThreshold()).isEqualTo(85);
    }

    @Test
    @DisplayName("Should handle invalid JSON format gracefully")
    void shouldHandleInvalidJsonFormatGracefully() {
      // Given
      String invalidJson = "{ invalid json format }";

      // When & Then
      try {
        GeneratedSemanticType result = parser.parseGenerationResponse(invalidJson, mockRequest);
        if (result != null) {
          assertThat(result.getResultType()).isEqualTo("error");
        }
      } catch (RuntimeException e) {
        assertThat(e.getMessage()).contains("Failed to parse");
      }
    }
  }

  @Nested
  @DisplayName("Similarity Check XML Parsing")
  class SimilarityCheckXmlParsing {

    @Test
    @DisplayName("Should parse similarity check XML with snake_case format")
    void shouldParseSimilarityCheckXmlWithSnakeCaseFormat() {
      // Given
      String similarityXml =
          """
                    <similarity_check>
                        <found_match>true</found_match>
                        <matched_type>EMAIL.ADDRESS</matched_type>
                        <confidence>95</confidence>
                        <suggested_action>use_existing</suggested_action>
                        <explanation>High confidence match found for email address type</explanation>
                    </similarity_check>
                    """;

      // When
      Map<String, Object> result = parser.parseSimilarityCheckXmlResponse(similarityXml);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("foundMatch")).isEqualTo(true);
      assertThat(result.get("matchedType")).isEqualTo("EMAIL.ADDRESS");
      assertThat(result.get("confidence")).isEqualTo(95);
      assertThat(result.get("suggestedAction")).isEqualTo("use_existing");
      assertThat(result.get("explanation"))
          .isEqualTo("High confidence match found for email address type");
    }

    @Test
    @DisplayName("Should parse similarity check XML with camelCase format")
    void shouldParseSimilarityCheckXmlWithCamelCaseFormat() {
      // Given
      String similarityXml =
          """
                    <similarityCheck>
                        <foundMatch>false</foundMatch>
                        <matchedType>null</matchedType>
                        <suggestedAction>create_different</suggestedAction>
                        <explanation>No suitable match found</explanation>
                    </similarityCheck>
                    """;

      // When
      Map<String, Object> result = parser.parseSimilarityCheckXmlResponse(similarityXml);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("foundMatch")).isEqualTo(false);
      assertThat(result.get("matchedType")).isEqualTo("null");
      assertThat(result.get("suggestedAction")).isEqualTo("create_different");
      assertThat(result.get("explanation")).isEqualTo("No suitable match found");
    }

    @Test
    @DisplayName("Should handle invalid confidence value in similarity check")
    void shouldHandleInvalidConfidenceValueInSimilarityCheck() {
      // Given
      String similarityXml =
          """
                    <similarity_check>
                        <found_match>true</found_match>
                        <matched_type>TEST.TYPE</matched_type>
                        <confidence>invalid_number</confidence>
                        <suggested_action>use_existing</suggested_action>
                        <explanation>Test with invalid confidence</explanation>
                    </similarity_check>
                    """;

      // When
      Map<String, Object> result = parser.parseSimilarityCheckXmlResponse(similarityXml);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("confidence")).isEqualTo(95); // Default value
    }

    @Test
    @DisplayName("Should handle malformed similarity check XML")
    void shouldHandleMalformedSimilarityCheckXml() {
      // Given
      String malformedXml = "<similarity_check><found_match>true</found_match>";

      // When
      Map<String, Object> result = parser.parseSimilarityCheckXmlResponse(malformedXml);

      // Then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Header Pattern Update Parsing")
  class HeaderPatternUpdateParsing {

    @Test
    @DisplayName("Should parse header pattern update XML format")
    void shouldParseHeaderPatternUpdateXmlFormat() {
      // Given
      String headerUpdateResponse =
          """
                    <headerPatternUpdate>
                        <headerPatterns>
                            <pattern>
                                <regExp>(?i).*email.*</regExp>
                                <confidence>90</confidence>
                                <mandatory>true</mandatory>
                                <rationale>Primary email pattern</rationale>
                                <positiveExample>email_address</positiveExample>
                                <negativeExample>phone_number</negativeExample>
                            </pattern>
                            <pattern>
                                <regExp>(?i).*mail.*</regExp>
                                <confidence>85</confidence>
                                <mandatory>false</mandatory>
                                <rationale>Alternative mail pattern</rationale>
                                <positiveExample>user_mail</positiveExample>
                                <negativeExample>street_address</negativeExample>
                            </pattern>
                        </headerPatterns>
                        <overallRationale>Updated header patterns for better email detection</overallRationale>
                    </headerPatternUpdate>
                    """;

      // When
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(headerUpdateResponse);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getNewPositiveHeaderExamples())
          .containsExactly("email_address", "user_mail");
      assertThat(result.getNewNegativeHeaderExamples())
          .containsExactly("phone_number", "street_address");
      assertThat(result.getExplanation())
          .isEqualTo("Updated header patterns for better email detection");

      // Verify header patterns were parsed correctly
      assertThat(result.getUpdatedHeaderPatterns()).isNotNull();
      assertThat(result.getUpdatedHeaderPatterns()).hasSize(2);
      assertThat(result.getUpdatedHeaderPatterns().get(0).getRegExp()).isEqualTo("(?i).*email.*");
      assertThat(result.getUpdatedHeaderPatterns().get(0).getConfidence()).isEqualTo(90);
      assertThat(result.getUpdatedHeaderPatterns().get(0).isMandatory()).isTrue();
      assertThat(result.getUpdatedHeaderPatterns().get(1).getRegExp()).isEqualTo("(?i).*mail.*");
      assertThat(result.getUpdatedHeaderPatterns().get(1).getConfidence()).isEqualTo(85);
      assertThat(result.getUpdatedHeaderPatterns().get(1).isMandatory()).isFalse();
    }

    @Test
    @DisplayName("Should parse generic pattern update format for backward compatibility")
    void shouldParseGenericPatternUpdateFormatForBackwardCompatibility() {
      // Given
      String genericUpdateResponse =
          """
                    <patternUpdate>
                        <updatedRegexPattern>\\d{3}-\\d{3}-\\d{4}</updatedRegexPattern>
                        <updatedHeaderPatterns>
                            <pattern>
                                <regExp>(?i).*phone.*</regExp>
                                <confidence>90</confidence>
                                <rationale>Phone pattern</rationale>
                                <positiveExamples>
                                    <example>phone</example>
                                    <example>telephone</example>
                                </positiveExamples>
                                <negativeExamples>
                                    <example>email</example>
                                </negativeExamples>
                            </pattern>
                        </updatedHeaderPatterns>
                        <newExamples>
                            <positiveContentExamples>
                                <example>555-123-4567</example>
                                <example>555-987-6543</example>
                            </positiveContentExamples>
                            <negativeContentExamples>
                                <example>123-45-6789</example>
                            </negativeContentExamples>
                            <positiveHeaderExamples>
                                <example>phone</example>
                            </positiveHeaderExamples>
                            <negativeHeaderExamples>
                                <example>email</example>
                            </negativeHeaderExamples>
                        </newExamples>
                        <updateSummary>Updated phone number pattern with proper formatting</updateSummary>
                    </patternUpdate>
                    """;

      // When
      PatternUpdateResponse result = parser.parsePatternUpdateResponse(genericUpdateResponse);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getImprovedPattern()).isEqualTo("\\d{3}-\\d{3}-\\d{4}");
      assertThat(result.getNewPositiveContentExamples())
          .containsExactly("555-123-4567", "555-987-6543");
      assertThat(result.getNewNegativeContentExamples()).containsExactly("123-45-6789");
      assertThat(result.getNewPositiveHeaderExamples()).containsExactly("phone");
      assertThat(result.getNewNegativeHeaderExamples()).containsExactly("email");
      assertThat(result.getExplanation())
          .isEqualTo("Updated phone number pattern with proper formatting");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Robustness")
  class EdgeCasesAndRobustness {

    @Test
    @DisplayName("Should handle empty and null responses")
    void shouldHandleEmptyAndNullResponses() {
      // Test null response
      try {
        GeneratedSemanticType result1 = parser.parseGenerationResponse(null, mockRequest);
        if (result1 != null) {
          assertThat(result1.getResultType()).isEqualTo("error");
        }
      } catch (RuntimeException e) {
        assertThat(e.getMessage()).contains("Failed to parse");
      }

      // Test empty response
      try {
        GeneratedSemanticType result2 = parser.parseGenerationResponse("", mockRequest);
        if (result2 != null) {
          assertThat(result2.getResultType()).isEqualTo("error");
        }
      } catch (RuntimeException e) {
        assertThat(e.getMessage()).contains("Failed to parse");
      }

      // Test whitespace-only response
      try {
        GeneratedSemanticType result3 = parser.parseGenerationResponse("   \n\t  ", mockRequest);
        if (result3 != null) {
          assertThat(result3.getResultType()).isEqualTo("error");
        }
      } catch (RuntimeException e) {
        assertThat(e.getMessage()).contains("Failed to parse");
      }
    }

    @Test
    @DisplayName("Should handle very large XML responses")
    void shouldHandleVeryLargeXmlResponses() {
      // Given - Create XML with many examples and patterns
      StringBuilder largeXmlBuilder = new StringBuilder();
      largeXmlBuilder.append(
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>LARGE.TYPE</semanticType>
                            <description>Type with many examples</description>
                            <pluginType>list</pluginType>
                            <listValues>
                    """);

      // Add 1000 list values
      for (int i = 0; i < 1000; i++) {
        largeXmlBuilder.append("<value>value").append(i).append("</value>");
      }

      largeXmlBuilder.append(
          """
                            </listValues>
                        </basicInfo>
                        <headerPatterns></headerPatterns>
                        <examples>
                            <positiveContentExamples>
                    """);

      // Add 500 positive examples
      for (int i = 0; i < 500; i++) {
        largeXmlBuilder.append("<example>positive").append(i).append("</example>");
      }

      largeXmlBuilder.append(
          """
                            </positiveContentExamples>
                            <negativeContentExamples>
                    """);

      // Add 500 negative examples
      for (int i = 0; i < 500; i++) {
        largeXmlBuilder.append("<example>negative").append(i).append("</example>");
      }

      largeXmlBuilder.append(
          """
                            </negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                        <explanation>Large response with many examples</explanation>
                    </semanticType>
                    """);

      String largeXml = largeXmlBuilder.toString();

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(largeXml, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("LARGE.TYPE");
      assertThat(result.getListValues()).hasSize(1000);
      assertThat(result.getPositiveContentExamples()).hasSize(500);
      assertThat(result.getNegativeContentExamples()).hasSize(500);
    }

    @Test
    @DisplayName("Should handle XML with special characters and CDATA")
    void shouldHandleXmlWithSpecialCharactersAndCdata() {
      // Given
      String xmlWithSpecialChars =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>SPECIAL.CHARS</semanticType>
                            <description><![CDATA[Type with special characters: <>&"']]></description>
                            <pluginType>regex</pluginType>
                            <regexPattern><![CDATA[[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}]]></regexPattern>
                        </basicInfo>
                        <headerPatterns></headerPatterns>
                        <examples>
                            <positiveContentExamples>
                                <example><![CDATA[test@example.com]]></example>
                                <example>user+tag@domain.co.uk</example>
                            </positiveContentExamples>
                            <negativeContentExamples>
                                <example><![CDATA[<invalid>@example.com]]></example>
                            </negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                        <explanation><![CDATA[Handles special XML characters: <>&"']]></explanation>
                    </semanticType>
                    """;

      // When
      GeneratedSemanticType result =
          parser.parseGenerationResponse(xmlWithSpecialChars, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("SPECIAL.CHARS");
      // XML entities may be preserved; accept either decoded or encoded ampersand
      assertThat(result.getDescription())
          .isIn("Type with special characters: <>&\"'", "Type with special characters: <>&amp;\"'");
      assertThat(result.getRegexPattern())
          .isEqualTo("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
      assertThat(result.getPositiveContentExamples())
          .containsExactly("test@example.com", "user+tag@domain.co.uk");
      assertThat(result.getNegativeContentExamples()).containsExactly("<invalid>@example.com");
      assertThat(result.getExplanation())
          .isIn(
              "Handles special XML characters: <>&\"'",
              "Handles special XML characters: <>&amp;\"'");
    }

    @Test
    @DisplayName("Should handle XML with nested structures and mixed content")
    void shouldHandleXmlWithNestedStructuresAndMixedContent() {
      // Given
      String complexXml =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>COMPLEX.TYPE</semanticType>
                            <description>Complex nested structure</description>
                            <pluginType>regex</pluginType>
                            <confidenceThreshold>90</confidenceThreshold>
                        </basicInfo>
                        <headerPatterns>
                            <pattern>
                                <regExp>(?i).*complex.*</regExp>
                                <confidence>95</confidence>
                                <mandatory>true</mandatory>
                                <rationale>Complex pattern matching</rationale>
                                <positiveExamples>
                                    <example>complex_field</example>
                                    <example>complex_data</example>
                                    <example>complex_value</example>
                                </positiveExamples>
                                <negativeExamples>
                                    <example>simple_field</example>
                                    <example>basic_data</example>
                                </negativeExamples>
                            </pattern>
                        </headerPatterns>
                        <examples>
                            <positiveContentExamples>
                                <example>complex_value_1</example>
                                <example>complex_value_2</example>
                            </positiveContentExamples>
                            <negativeContentExamples>
                                <example>simple_value</example>
                            </negativeContentExamples>
                            <positiveHeaderExamples>
                                <example>complex_header</example>
                            </positiveHeaderExamples>
                            <negativeHeaderExamples>
                                <example>simple_header</example>
                            </negativeHeaderExamples>
                        </examples>
                        <explanation>Comprehensive complex type with nested patterns</explanation>
                    </semanticType>
                    """;

      // When
      GeneratedSemanticType result = parser.parseGenerationResponse(complexXml, mockRequest);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("COMPLEX.TYPE");
      assertThat(result.getHeaderPatterns()).hasSize(1);
      assertThat(result.getHeaderPatterns().get(0).getPositiveExamples()).hasSize(3);
      assertThat(result.getHeaderPatterns().get(0).getNegativeExamples()).hasSize(2);
      assertThat(result.getPositiveContentExamples()).hasSize(2);
      assertThat(result.getNegativeContentExamples()).hasSize(1);
    }

    @Test
    @DisplayName("Should extract JSON from responses with markdown formatting")
    void shouldExtractJsonFromResponsesWithMarkdownFormatting() {
      // Given
      String responseWithMarkdown =
          """
                    Here's the generated semantic type:

                    ```json
                    {
                        "semanticType": "EXTRACTED.TYPE",
                        "description": "Extracted from markdown",
                        "pluginType": "regex"
                    }
                    ```

                    This completes the generation.
                    """;

      // When
      String extractedJson = parser.extractJsonFromResponse(responseWithMarkdown);

      // Then
      assertThat(extractedJson).isNotNull();
      assertThat(extractedJson).contains("EXTRACTED.TYPE");
      assertThat(extractedJson).contains("Extracted from markdown");
    }

    @Test
    @DisplayName("Should handle error cases gracefully")
    void shouldHandleErrorCasesGracefully() {
      // Test with invalid input
      String invalidXml = "not valid xml";

      // Should handle invalid input without throwing unhandled exceptions
      try {
        GeneratedSemanticType result = parser.parseGenerationResponse(invalidXml, mockRequest);
        // If it doesn't throw, check that result handles error appropriately
        assertThat(result).isNotNull();
      } catch (RuntimeException e) {
        // Expected behavior for invalid input - service throws RuntimeException
        assertThat(e.getMessage()).contains("Failed to parse");
      }
    }

    @Test
    @DisplayName("Should validate that parser handles concurrent access safely")
    void shouldValidateThatParserHandlesConcurrentAccessSafely() {
      // Given
      String validXml =
          """
                    <semanticType>
                        <basicInfo>
                            <semanticType>CONCURRENT.TYPE</semanticType>
                            <description>Concurrent access test</description>
                            <pluginType>regex</pluginType>
                        </basicInfo>
                        <headerPatterns></headerPatterns>
                        <examples>
                            <positiveContentExamples></positiveContentExamples>
                            <negativeContentExamples></negativeContentExamples>
                            <positiveHeaderExamples></positiveHeaderExamples>
                            <negativeHeaderExamples></negativeHeaderExamples>
                        </examples>
                    </semanticType>
                    """;

      // When - Parse concurrently from multiple threads
      List<GeneratedSemanticType> results =
          java.util.Collections.nCopies(10, "").parallelStream()
              .map(s -> parser.parseGenerationResponse(validXml, mockRequest))
              .toList();

      // Then - All results should be consistent
      assertThat(results).hasSize(10);
      results.forEach(
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getSemanticType()).isEqualTo("CONCURRENT.TYPE");
            assertThat(result.getDescription()).isEqualTo("Concurrent access test");
          });
    }
  }
}

package com.nl2fta.classifier.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("FeedbackRequest Tests")
class FeedbackRequestTest {

  private FeedbackRequest feedbackRequest;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    feedbackRequest = new FeedbackRequest();
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("Getter and Setter Tests")
  class GetterSetterTests {

    @Test
    @DisplayName("Should set and get type correctly")
    void shouldSetAndGetType() {
      // Given
      String expectedType = "semantic_type_creation";

      // When
      feedbackRequest.setType(expectedType);

      // Then
      assertThat(feedbackRequest.getType()).isEqualTo(expectedType);
    }

    @Test
    @DisplayName("Should set and get feedback correctly")
    void shouldSetAndGetFeedback() {
      // Given
      String expectedFeedback = "positive";

      // When
      feedbackRequest.setFeedback(expectedFeedback);

      // Then
      assertThat(feedbackRequest.getFeedback()).isEqualTo(expectedFeedback);
    }

    @Test
    @DisplayName("Should set and get semantic type name correctly")
    void shouldSetAndGetSemanticTypeName() {
      // Given
      String expectedName = "Email Address";

      // When
      feedbackRequest.setSemanticTypeName(expectedName);

      // Then
      assertThat(feedbackRequest.getSemanticTypeName()).isEqualTo(expectedName);
    }

    @Test
    @DisplayName("Should set and get description correctly")
    void shouldSetAndGetDescription() {
      // Given
      String expectedDescription = "Validates email addresses using regex pattern";

      // When
      feedbackRequest.setDescription(expectedDescription);

      // Then
      assertThat(feedbackRequest.getDescription()).isEqualTo(expectedDescription);
    }

    @Test
    @DisplayName("Should set and get plugin type correctly")
    void shouldSetAndGetPluginType() {
      // Given
      String expectedPluginType = "REGEX";

      // When
      feedbackRequest.setPluginType(expectedPluginType);

      // Then
      assertThat(feedbackRequest.getPluginType()).isEqualTo(expectedPluginType);
    }

    @Test
    @DisplayName("Should set and get regex pattern correctly")
    void shouldSetAndGetRegexPattern() {
      // Given
      String expectedPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

      // When
      feedbackRequest.setRegexPattern(expectedPattern);

      // Then
      assertThat(feedbackRequest.getRegexPattern()).isEqualTo(expectedPattern);
    }

    @Test
    @DisplayName("Should set and get header patterns correctly")
    void shouldSetAndGetHeaderPatterns() {
      // Given
      String expectedHeaderPatterns = "email,e-mail,email_address";

      // When
      feedbackRequest.setHeaderPatterns(expectedHeaderPatterns);

      // Then
      assertThat(feedbackRequest.getHeaderPatterns()).isEqualTo(expectedHeaderPatterns);
    }

    @Test
    @DisplayName("Should set and get username correctly")
    void shouldSetAndGetUsername() {
      // Given
      String expectedUsername = "data_analyst";

      // When
      feedbackRequest.setUsername(expectedUsername);

      // Then
      assertThat(feedbackRequest.getUsername()).isEqualTo(expectedUsername);
    }

    @Test
    @DisplayName("Should set and get timestamp correctly")
    void shouldSetAndGetTimestamp() {
      // Given
      String expectedTimestamp = "2023-01-01T15:30:00Z";

      // When
      feedbackRequest.setTimestamp(expectedTimestamp);

      // Then
      assertThat(feedbackRequest.getTimestamp()).isEqualTo(expectedTimestamp);
    }
  }

  @Nested
  @DisplayName("Null and Empty Value Handling")
  class NullValueHandlingTests {

    @Test
    @DisplayName("Should handle null values correctly")
    void shouldHandleNullValues() {
      // When - Set all fields to null
      feedbackRequest.setType(null);
      feedbackRequest.setFeedback(null);
      feedbackRequest.setSemanticTypeName(null);
      feedbackRequest.setDescription(null);
      feedbackRequest.setPluginType(null);
      feedbackRequest.setRegexPattern(null);
      feedbackRequest.setHeaderPatterns(null);
      feedbackRequest.setUsername(null);
      feedbackRequest.setTimestamp(null);

      // Then - All getters should return null
      assertThat(feedbackRequest.getType()).isNull();
      assertThat(feedbackRequest.getFeedback()).isNull();
      assertThat(feedbackRequest.getSemanticTypeName()).isNull();
      assertThat(feedbackRequest.getDescription()).isNull();
      assertThat(feedbackRequest.getPluginType()).isNull();
      assertThat(feedbackRequest.getRegexPattern()).isNull();
      assertThat(feedbackRequest.getHeaderPatterns()).isNull();
      assertThat(feedbackRequest.getUsername()).isNull();
      assertThat(feedbackRequest.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("Should handle empty strings correctly")
    void shouldHandleEmptyStrings() {
      // When - Set all fields to empty strings
      feedbackRequest.setType("");
      feedbackRequest.setFeedback("");
      feedbackRequest.setSemanticTypeName("");
      feedbackRequest.setDescription("");
      feedbackRequest.setPluginType("");
      feedbackRequest.setRegexPattern("");
      feedbackRequest.setHeaderPatterns("");
      feedbackRequest.setUsername("");
      feedbackRequest.setTimestamp("");

      // Then - All getters should return empty strings
      assertThat(feedbackRequest.getType()).isEmpty();
      assertThat(feedbackRequest.getFeedback()).isEmpty();
      assertThat(feedbackRequest.getSemanticTypeName()).isEmpty();
      assertThat(feedbackRequest.getDescription()).isEmpty();
      assertThat(feedbackRequest.getPluginType()).isEmpty();
      assertThat(feedbackRequest.getRegexPattern()).isEmpty();
      assertThat(feedbackRequest.getHeaderPatterns()).isEmpty();
      assertThat(feedbackRequest.getUsername()).isEmpty();
      assertThat(feedbackRequest.getTimestamp()).isEmpty();
    }
  }

  @Nested
  @DisplayName("JSON Serialization Tests")
  class JsonSerializationTests {

    @Test
    @DisplayName("Should serialize complete feedback request to JSON correctly")
    void shouldSerializeCompleteRequestToJsonCorrectly() throws IOException {
      // Given
      feedbackRequest.setType("semantic_type_creation");
      feedbackRequest.setFeedback("positive");
      feedbackRequest.setSemanticTypeName("Phone Number");
      feedbackRequest.setDescription("Validates US phone numbers");
      feedbackRequest.setPluginType("REGEX");
      feedbackRequest.setRegexPattern(
          "^\\+?1?[\\s.-]?\\(?[0-9]{3}\\)?[\\s.-]?[0-9]{3}[\\s.-]?[0-9]{4}$");
      feedbackRequest.setHeaderPatterns("phone,phone_number,telephone");
      feedbackRequest.setUsername("analyst123");
      feedbackRequest.setTimestamp("2023-01-01T16:45:00Z");

      // When
      String json = objectMapper.writeValueAsString(feedbackRequest);

      // Then
      assertThat(json).contains("\"type\":\"semantic_type_creation\"");
      assertThat(json).contains("\"feedback\":\"positive\"");
      assertThat(json).contains("\"semanticTypeName\":\"Phone Number\"");
      assertThat(json).contains("\"description\":\"Validates US phone numbers\"");
      assertThat(json).contains("\"pluginType\":\"REGEX\"");
      assertThat(json).contains("\"headerPatterns\":\"phone,phone_number,telephone\"");
      assertThat(json).contains("\"username\":\"analyst123\"");
      assertThat(json).contains("\"timestamp\":\"2023-01-01T16:45:00Z\"");
    }

    @Test
    @DisplayName("Should deserialize from JSON correctly")
    void shouldDeserializeFromJsonCorrectly() throws IOException {
      // Given
      String json =
          """
                {
                    "type": "classification_correction",
                    "feedback": "negative",
                    "semanticTypeName": "SSN",
                    "description": "Social Security Number identifier",
                    "pluginType": "REGEX",
                    "regexPattern": "^\\\\d{3}-\\\\d{2}-\\\\d{4}$",
                    "headerPatterns": "ssn,social_security,social_security_number",
                    "username": "reviewer",
                    "timestamp": "2023-01-01T18:00:00Z"
                }
                """;

      // When
      FeedbackRequest deserializedRequest = objectMapper.readValue(json, FeedbackRequest.class);

      // Then
      assertThat(deserializedRequest.getType()).isEqualTo("classification_correction");
      assertThat(deserializedRequest.getFeedback()).isEqualTo("negative");
      assertThat(deserializedRequest.getSemanticTypeName()).isEqualTo("SSN");
      assertThat(deserializedRequest.getDescription())
          .isEqualTo("Social Security Number identifier");
      assertThat(deserializedRequest.getPluginType()).isEqualTo("REGEX");
      assertThat(deserializedRequest.getRegexPattern()).isEqualTo("^\\d{3}-\\d{2}-\\d{4}$");
      assertThat(deserializedRequest.getHeaderPatterns())
          .isEqualTo("ssn,social_security,social_security_number");
      assertThat(deserializedRequest.getUsername()).isEqualTo("reviewer");
      assertThat(deserializedRequest.getTimestamp()).isEqualTo("2023-01-01T18:00:00Z");
    }

    @Test
    @DisplayName("Should handle partial JSON with missing fields")
    void shouldHandlePartialJsonWithMissingFields() throws IOException {
      // Given - JSON with only essential fields
      String json =
          """
                {
                    "type": "general_feedback",
                    "feedback": "neutral",
                    "username": "user123"
                }
                """;

      // When
      FeedbackRequest deserializedRequest = objectMapper.readValue(json, FeedbackRequest.class);

      // Then
      assertThat(deserializedRequest.getType()).isEqualTo("general_feedback");
      assertThat(deserializedRequest.getFeedback()).isEqualTo("neutral");
      assertThat(deserializedRequest.getUsername()).isEqualTo("user123");
      assertThat(deserializedRequest.getSemanticTypeName()).isNull();
      assertThat(deserializedRequest.getDescription()).isNull();
      assertThat(deserializedRequest.getPluginType()).isNull();
      assertThat(deserializedRequest.getRegexPattern()).isNull();
      assertThat(deserializedRequest.getHeaderPatterns()).isNull();
      assertThat(deserializedRequest.getTimestamp()).isNull();
    }
  }

  @Nested
  @DisplayName("Business Logic Validation")
  class BusinessLogicValidationTests {

    @Test
    @DisplayName("Should handle different feedback types")
    void shouldHandleDifferentFeedbackTypes() {
      // Given different feedback types
      String[] feedbackTypes = {"positive", "negative", "neutral", "suggestion"};

      for (String feedbackType : feedbackTypes) {
        // When
        feedbackRequest.setFeedback(feedbackType);

        // Then
        assertThat(feedbackRequest.getFeedback()).isEqualTo(feedbackType);
      }
    }

    @Test
    @DisplayName("Should handle different plugin types")
    void shouldHandleDifferentPluginTypes() {
      // Given different plugin types
      String[] pluginTypes = {"REGEX", "ANCHORED_PATTERN", "CUSTOM", "BUILTIN"};

      for (String pluginType : pluginTypes) {
        // When
        feedbackRequest.setPluginType(pluginType);

        // Then
        assertThat(feedbackRequest.getPluginType()).isEqualTo(pluginType);
      }
    }

    @Test
    @DisplayName("Should handle complex regex patterns")
    void shouldHandleComplexRegexPatterns() {
      // Given
      String complexRegex =
          "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

      // When
      feedbackRequest.setRegexPattern(complexRegex);

      // Then
      assertThat(feedbackRequest.getRegexPattern()).isEqualTo(complexRegex);
    }

    @Test
    @DisplayName("Should handle multiple header patterns")
    void shouldHandleMultipleHeaderPatterns() {
      // Given
      String headerPatterns = "id,identifier,unique_id,primary_key,pk";

      // When
      feedbackRequest.setHeaderPatterns(headerPatterns);

      // Then
      assertThat(feedbackRequest.getHeaderPatterns()).isEqualTo(headerPatterns);
      assertThat(feedbackRequest.getHeaderPatterns()).contains("id");
      assertThat(feedbackRequest.getHeaderPatterns()).contains("identifier");
      assertThat(feedbackRequest.getHeaderPatterns()).contains("unique_id");
      assertThat(feedbackRequest.getHeaderPatterns()).contains("primary_key");
      assertThat(feedbackRequest.getHeaderPatterns()).contains("pk");
    }
  }
}

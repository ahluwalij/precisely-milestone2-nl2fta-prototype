package com.nl2fta.classifier.dto.semantic_type;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("HeaderPattern Tests")
class HeaderPatternTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("Should create HeaderPattern using builder with default mandatory value")
  void shouldCreateHeaderPatternUsingBuilderWithDefaultMandatory() {
    // When
    HeaderPattern pattern =
        HeaderPattern.builder()
            .regExp("(?i)(email|e_mail)")
            .confidence(95)
            .positiveExamples(Arrays.asList("email", "e_mail", "Email_Address"))
            .negativeExamples(Arrays.asList("phone", "id", "name"))
            .rationale("Pattern matches common email header variations")
            .build();

    // Then
    assertThat(pattern.getRegExp()).isEqualTo("(?i)(email|e_mail)");
    assertThat(pattern.getConfidence()).isEqualTo(95);
    assertThat(pattern.isMandatory()).isTrue(); // Default value
    assertThat(pattern.getPositiveExamples()).containsExactly("email", "e_mail", "Email_Address");
    assertThat(pattern.getNegativeExamples()).containsExactly("phone", "id", "name");
    assertThat(pattern.getRationale()).isEqualTo("Pattern matches common email header variations");
  }

  @Test
  @DisplayName("Should create HeaderPattern with explicit mandatory false")
  void shouldCreateHeaderPatternWithExplicitMandatoryFalse() {
    // When
    HeaderPattern pattern =
        HeaderPattern.builder()
            .regExp("(?i)(optional_field)")
            .confidence(70)
            .mandatory(false)
            .build();

    // Then
    assertThat(pattern.isMandatory()).isFalse();
    assertThat(pattern.getConfidence()).isEqualTo(70);
  }

  @Test
  @DisplayName("Should serialize and deserialize correctly")
  void shouldSerializeAndDeserializeCorrectly() throws IOException {
    // Given
    List<String> positiveExamples = Arrays.asList("user_id", "userId", "USER_ID");
    List<String> negativeExamples = Arrays.asList("user_name", "email");

    HeaderPattern originalPattern =
        HeaderPattern.builder()
            .regExp("(?i)(user_id|userid)")
            .confidence(90)
            .mandatory(true)
            .positiveExamples(positiveExamples)
            .negativeExamples(negativeExamples)
            .rationale("Matches user identifier patterns")
            .build();

    // When - Serialize to JSON
    String json = objectMapper.writeValueAsString(originalPattern);

    // Then - JSON should contain expected fields
    assertThat(json).contains("\"regExp\":\"(?i)(user_id|userid)\"");
    assertThat(json).contains("\"confidence\":90");
    assertThat(json).contains("\"mandatory\":true");

    // When - Deserialize back
    HeaderPattern deserializedPattern = objectMapper.readValue(json, HeaderPattern.class);

    // Then - Objects should be equal
    assertThat(deserializedPattern).isEqualTo(originalPattern);
    assertThat(deserializedPattern.getRegExp()).isEqualTo(originalPattern.getRegExp());
    assertThat(deserializedPattern.getConfidence()).isEqualTo(originalPattern.getConfidence());
    assertThat(deserializedPattern.isMandatory()).isEqualTo(originalPattern.isMandatory());
    assertThat(deserializedPattern.getPositiveExamples())
        .isEqualTo(originalPattern.getPositiveExamples());
    assertThat(deserializedPattern.getNegativeExamples())
        .isEqualTo(originalPattern.getNegativeExamples());
    assertThat(deserializedPattern.getRationale()).isEqualTo(originalPattern.getRationale());
  }
}

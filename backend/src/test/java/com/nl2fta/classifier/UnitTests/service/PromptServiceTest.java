package com.nl2fta.classifier.service.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nl2fta.classifier.service.PromptService;
import com.nl2fta.classifier.service.PromptService.SemanticTypeGenerationParams;

/**
 * Unit tests for {@link PromptService}. Tests prompt template loading and semantic type generation
 * prompt building.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromptService Unit Tests")
public class PromptServiceTest {

  private PromptService promptService;

  @BeforeEach
  void setUp() {
    promptService = new PromptService();
  }

  @Nested
  @DisplayName("Load Prompt Template Tests")
  class LoadPromptTemplateTests {

    @Test
    @DisplayName("Should load existing prompt template successfully")
    void shouldLoadExistingPromptTemplate() throws IOException {
      // When
      String template = promptService.loadPromptTemplate("semantic-type-generation");

      // Then
      assertThat(template).isNotNull();
      assertThat(template).isNotEmpty();
      assertThat(template).contains("{{TYPE_NAME}}");
      assertThat(template).contains("{{DESCRIPTION}}");
    }

    @Test
    @DisplayName("Should throw IOException for non-existent template")
    void shouldThrowIOExceptionForNonExistentTemplate() {
      // When & Then
      assertThatThrownBy(() -> promptService.loadPromptTemplate("non-existent-template"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining(
              "Failed to load prompt template: prompts/non-existent-template.txt");
    }

    @Test
    @DisplayName("Should handle null prompt name")
    void shouldHandleNullPromptName() {
      // When & Then
      assertThatThrownBy(() -> promptService.loadPromptTemplate(null))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle empty prompt name")
    void shouldHandleEmptyPromptName() {
      // When & Then
      assertThatThrownBy(() -> promptService.loadPromptTemplate(""))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Failed to load prompt template: prompts/.txt");
    }
  }

  @Nested
  @DisplayName("Build Semantic Type Generation Prompt Tests")
  class BuildSemanticTypeGenerationPromptTests {

    @Test
    @DisplayName("Should build prompt with minimal parameters")
    void shouldBuildPromptWithMinimalParameters() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .existingTypes(Arrays.asList("ExistingType1", "ExistingType2"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("TestType");
      assertThat(prompt).contains("Test description");
      assertThat(prompt).contains("ExistingType1");
      assertThat(prompt).contains("ExistingType2");
    }

    @Test
    @DisplayName("Should build prompt with all parameters")
    void shouldBuildPromptWithAllParameters() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("FullTestType")
              .description("Complete test description")
              .positiveContentExamples(Arrays.asList("positive1", "positive2"))
              .negativeContentExamples(Arrays.asList("negative1", "negative2"))
              .positiveHeaderExamples(Arrays.asList("header1", "header2"))
              .negativeHeaderExamples(Arrays.asList("badheader1", "badheader2"))
              .existingTypes(Arrays.asList("Type1", "Type2"))
              .columnHeader("test_column")
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("FullTestType");
      assertThat(prompt).contains("Complete test description");
      assertThat(prompt).contains("positive1");
      assertThat(prompt).contains("negative1");
      assertThat(prompt).contains("header1");
      assertThat(prompt).contains("badheader1");
    }

    @Test
    @DisplayName("Should handle AVOID prefix in type name")
    void shouldHandleAvoidPrefixInTypeName() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("AVOID:SimilarType")
              .description("Test description")
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).doesNotContain("GENERATE_APPROPRIATE_NAME");
      // Should contain the avoid instruction that gets appended to description
      assertThat(prompt).contains("SimilarType");
    }

    @Test
    @DisplayName("Should handle null type name")
    void shouldHandleNullTypeName() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName(null)
              .description("Test description")
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).doesNotContain("GENERATE_APPROPRIATE_NAME");
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description(null)
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("TestType");
    }

    @Test
    @DisplayName("Should handle empty content examples")
    void shouldHandleEmptyContentExamples() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .positiveContentExamples(Arrays.asList())
              .negativeContentExamples(Arrays.asList())
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("No positive content examples provided");
      assertThat(prompt).contains("No negative content examples provided");
    }

    @Test
    @DisplayName("Should handle null example lists")
    void shouldHandleNullExampleLists() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .positiveContentExamples(null)
              .negativeContentExamples(null)
              .positiveHeaderExamples(null)
              .negativeHeaderExamples(null)
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("No positive content examples provided");
      assertThat(prompt).contains("No negative content examples provided");
      assertThat(prompt).contains("No positive header examples provided");
      assertThat(prompt).contains("No negative header examples provided");
    }

    @Test
    @DisplayName("Should throw NullPointerException when existingTypes is null")
    void shouldThrowNullPointerExceptionWhenExistingTypesIsNull() {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .existingTypes(null)
              .build();

      // When & Then
      assertThatThrownBy(() -> promptService.buildSemanticTypeGenerationPrompt(params))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle column header properly")
    void shouldHandleColumnHeaderProperly() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .columnHeader("test_column_header")
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("test_column_header");
    }

    @Test
    @DisplayName("Should handle empty column header")
    void shouldHandleEmptyColumnHeader() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .columnHeader("")
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      // Column header sections should be removed when column header is empty
    }

    @Test
    @DisplayName("Should handle null column header")
    void shouldHandleNullColumnHeader() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .columnHeader(null)
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      // Column header sections should be removed when column header is null
    }

    @Test
    @DisplayName("Should handle empty string type name")
    void shouldHandleEmptyStringTypeName() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("")
              .description("Test description")
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      // Empty string should be preserved in the prompt
    }

    @Test
    @DisplayName("Should handle examples with special characters")
    void shouldHandleExamplesWithSpecialCharacters() throws IOException {
      // Given
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .positiveContentExamples(
                  Arrays.asList(
                      "test@example.com", "user+tag@domain.co.uk", "name.surname@company-name.org"))
              .negativeContentExamples(
                  Arrays.asList("not-an-email", "@invalid.com", "missing@domain"))
              .existingTypes(Arrays.asList("Type1"))
              .build();

      // When
      String prompt = promptService.buildSemanticTypeGenerationPrompt(params);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains("test@example.com");
      assertThat(prompt).contains("user+tag@domain.co.uk");
      assertThat(prompt).contains("not-an-email");
    }
  }

  @Nested
  @DisplayName("SemanticTypeGenerationParams Builder Tests")
  class SemanticTypeGenerationParamsBuilderTests {

    @Test
    @DisplayName("Should build params with all fields")
    void shouldBuildParamsWithAllFields() {
      // Given
      List<String> positiveContent = Arrays.asList("pos1", "pos2");
      List<String> negativeContent = Arrays.asList("neg1", "neg2");
      List<String> positiveHeaders = Arrays.asList("header1", "header2");
      List<String> negativeHeaders = Arrays.asList("badheader1", "badheader2");
      List<String> existingTypes = Arrays.asList("Type1", "Type2");

      // When
      SemanticTypeGenerationParams params =
          SemanticTypeGenerationParams.builder()
              .typeName("TestType")
              .description("Test description")
              .positiveContentExamples(positiveContent)
              .negativeContentExamples(negativeContent)
              .positiveHeaderExamples(positiveHeaders)
              .negativeHeaderExamples(negativeHeaders)
              .existingTypes(existingTypes)
              .columnHeader("test_column")
              .build();

      // Then
      assertThat(params.getTypeName()).isEqualTo("TestType");
      assertThat(params.getDescription()).isEqualTo("Test description");
      assertThat(params.getPositiveContentExamples()).isEqualTo(positiveContent);
      assertThat(params.getNegativeContentExamples()).isEqualTo(negativeContent);
      assertThat(params.getPositiveHeaderExamples()).isEqualTo(positiveHeaders);
      assertThat(params.getNegativeHeaderExamples()).isEqualTo(negativeHeaders);
      assertThat(params.getExistingTypes()).isEqualTo(existingTypes);
      assertThat(params.getColumnHeader()).isEqualTo("test_column");
    }

    @Test
    @DisplayName("Should build empty params")
    void shouldBuildEmptyParams() {
      // When
      SemanticTypeGenerationParams params = SemanticTypeGenerationParams.builder().build();

      // Then
      assertThat(params.getTypeName()).isNull();
      assertThat(params.getDescription()).isNull();
      assertThat(params.getPositiveContentExamples()).isNull();
      assertThat(params.getNegativeContentExamples()).isNull();
      assertThat(params.getPositiveHeaderExamples()).isNull();
      assertThat(params.getNegativeHeaderExamples()).isNull();
      assertThat(params.getExistingTypes()).isNull();
      assertThat(params.getColumnHeader()).isNull();
    }

    @Test
    @DisplayName("Should build params incrementally")
    void shouldBuildParamsIncrementally() {
      // When
      SemanticTypeGenerationParams.Builder builder = SemanticTypeGenerationParams.builder();
      builder.typeName("TestType");
      builder.description("Test description");
      SemanticTypeGenerationParams params = builder.build();

      // Then
      assertThat(params.getTypeName()).isEqualTo("TestType");
      assertThat(params.getDescription()).isEqualTo("Test description");
      assertThat(params.getPositiveContentExamples()).isNull();
    }
  }

  @Nested
  @DisplayName("Build Regenerate Data Values Prompt Tests")
  class BuildRegenerateDataValuesPromptTests {

    @Test
    @DisplayName("Should build regenerate data values prompt with all parameters")
    void shouldBuildRegenerateDataValuesPromptWithAllParameters() throws IOException {
      // Given
      String semanticTypeName = "EMAIL.ADDRESS";
      String currentRegexPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
      List<String> positiveExamples = Arrays.asList("test@example.com", "user@domain.org");
      List<String> negativeExamples = Arrays.asList("invalid-email", "missing@domain");
      String userDescription = "Make it more restrictive for corporate emails";
      String description = "Corporate email addresses";

      // When
      String prompt =
          promptService.buildRegenerateDataValuesPrompt(
              semanticTypeName,
              currentRegexPattern,
              positiveExamples,
              negativeExamples,
              userDescription,
              description);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains(semanticTypeName);
      assertThat(prompt).contains(currentRegexPattern);
      assertThat(prompt).contains("test@example.com");
      assertThat(prompt).contains("invalid-email");
      assertThat(prompt).contains(userDescription);
      assertThat(prompt).contains(description);
    }

    @Test
    @DisplayName("Should handle null parameters in regenerate data values prompt")
    void shouldHandleNullParametersInRegenerateDataValuesPrompt() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateDataValuesPrompt(null, null, null, null, null, null);

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty examples in regenerate data values prompt")
    void shouldHandleEmptyExamplesInRegenerateDataValuesPrompt() throws IOException {
      // Given
      List<String> emptyExamples = Arrays.asList();

      // When
      String prompt =
          promptService.buildRegenerateDataValuesPrompt(
              "TEST.TYPE",
              "pattern",
              emptyExamples,
              emptyExamples,
              "description",
              "type description");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty user description in regenerate data values prompt")
    void shouldHandleEmptyUserDescriptionInRegenerateDataValuesPrompt() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateDataValuesPrompt(
              "TEST.TYPE",
              "pattern",
              Arrays.asList("example"),
              Arrays.asList("negative"),
              "",
              "description");

      // Then
      assertThat(prompt).isNotNull();
    }
  }

  @Nested
  @DisplayName("Build Regenerate Header Values Prompt Tests")
  class BuildRegenerateHeaderValuesPromptTests {

    @Test
    @DisplayName("Should build regenerate header values prompt with all parameters")
    void shouldBuildRegenerateHeaderValuesPromptWithAllParameters() throws IOException {
      // Given
      String semanticTypeName = "USER.ID";
      String currentHeaderPatterns = "user.*id|userid|user_id";
      List<String> positiveExamples = Arrays.asList("user_id", "userid", "user-identifier");
      List<String> negativeExamples = Arrays.asList("name", "email", "address");
      String userDescription = "Include variations with 'identifier' keyword";

      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              semanticTypeName,
              currentHeaderPatterns,
              positiveExamples,
              negativeExamples,
              userDescription);

      // Then
      assertThat(prompt).isNotNull();
      assertThat(prompt).contains(semanticTypeName);
      assertThat(prompt).contains(currentHeaderPatterns);
      assertThat(prompt).contains("user_id");
      assertThat(prompt).contains("name");
      assertThat(prompt).contains(userDescription);
    }

    @Test
    @DisplayName("Should handle null parameters in regenerate header values prompt")
    void shouldHandleNullParametersInRegenerateHeaderValuesPrompt() throws IOException {
      // When
      String prompt = promptService.buildRegenerateHeaderValuesPrompt(null, null, null, null, null);

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty current header patterns")
    void shouldHandleEmptyCurrentHeaderPatterns() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", "", Arrays.asList("header"), Arrays.asList("bad"), "description");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle null current header patterns")
    void shouldHandleNullCurrentHeaderPatterns() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", null, Arrays.asList("header"), Arrays.asList("bad"), "description");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty example lists in header regeneration")
    void shouldHandleEmptyExampleListsInHeaderRegeneration() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", "patterns", Arrays.asList(), Arrays.asList(), "description");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle null example lists in header regeneration")
    void shouldHandleNullExampleListsInHeaderRegeneration() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", "patterns", null, null, "description");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty user description in header regeneration")
    void shouldHandleEmptyUserDescriptionInHeaderRegeneration() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", "patterns", Arrays.asList("header"), Arrays.asList("bad"), "");

      // Then
      assertThat(prompt).isNotNull();
    }

    @Test
    @DisplayName("Should handle null user description in header regeneration")
    void shouldHandleNullUserDescriptionInHeaderRegeneration() throws IOException {
      // When
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              "TEST.TYPE", "patterns", Arrays.asList("header"), Arrays.asList("bad"), null);

      // Then
      assertThat(prompt).isNotNull();
    }
  }
}

package com.nl2fta.classifier.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

  private static final String PROMPTS_PATH = "prompts/";

  public static final class SemanticTypeGenerationParams {
    private String typeName;
    private String description;
    private List<String> positiveContentExamples;
    private List<String> negativeContentExamples;
    private List<String> positiveHeaderExamples;
    private List<String> negativeHeaderExamples;
    private List<String> existingTypes;
    private String columnHeader;

    private SemanticTypeGenerationParams() {
      // Private constructor for builder pattern
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private final SemanticTypeGenerationParams params = new SemanticTypeGenerationParams();

      public Builder typeName(String typeName) {
        params.typeName = typeName;
        return this;
      }

      public Builder description(String description) {
        params.description = description;
        return this;
      }

      public Builder positiveContentExamples(List<String> examples) {
        params.positiveContentExamples = examples;
        return this;
      }

      public Builder negativeContentExamples(List<String> examples) {
        params.negativeContentExamples = examples;
        return this;
      }

      public Builder positiveHeaderExamples(List<String> examples) {
        params.positiveHeaderExamples = examples;
        return this;
      }

      public Builder negativeHeaderExamples(List<String> examples) {
        params.negativeHeaderExamples = examples;
        return this;
      }

      public Builder existingTypes(List<String> types) {
        params.existingTypes = types;
        return this;
      }

      public Builder columnHeader(String header) {
        params.columnHeader = header;
        return this;
      }

      public SemanticTypeGenerationParams build() {
        return params;
      }
    }

    // Getters
    public String getTypeName() {
      return typeName;
    }

    public String getDescription() {
      return description;
    }

    public List<String> getPositiveContentExamples() {
      return positiveContentExamples == null
          ? null
          : Collections.unmodifiableList(positiveContentExamples);
    }

    public List<String> getNegativeContentExamples() {
      return negativeContentExamples == null
          ? null
          : Collections.unmodifiableList(negativeContentExamples);
    }

    public List<String> getPositiveHeaderExamples() {
      return positiveHeaderExamples == null
          ? null
          : Collections.unmodifiableList(positiveHeaderExamples);
    }

    public List<String> getNegativeHeaderExamples() {
      return negativeHeaderExamples == null
          ? null
          : Collections.unmodifiableList(negativeHeaderExamples);
    }

    public List<String> getExistingTypes() {
      return existingTypes == null ? null : Collections.unmodifiableList(existingTypes);
    }

    public String getColumnHeader() {
      return columnHeader;
    }
  }

  public String buildSemanticTypeGenerationPrompt(SemanticTypeGenerationParams params)
      throws IOException {
    String promptTemplate = loadPromptTemplate("semantic-type-generation");

    // Check if typeName contains AVOID: prefix
    String typeNameToAvoid = null;
    String actualTypeName = params.getTypeName();

    if (params.getTypeName() != null && params.getTypeName().startsWith("AVOID:")) {
      typeNameToAvoid = params.getTypeName().substring(6); // Remove "AVOID:" prefix
      actualTypeName = "GENERATE_APPROPRIATE_NAME";
    }

    promptTemplate =
        promptTemplate.replace(
            "{{DESCRIPTION}}", params.getDescription() != null ? params.getDescription() : "");

    // Handle type name conditional sections
    if (actualTypeName != null && !actualTypeName.equals("GENERATE_APPROPRIATE_NAME")) {
      // Replace the conditional sections with actual content (use DOTALL flag for multiline)
      promptTemplate =
          promptTemplate.replaceAll("(?s)\\{\\{#TYPE_NAME\\}\\}(.*?)\\{\\{/TYPE_NAME\\}\\}", "$1");
      promptTemplate = promptTemplate.replace("{{TYPE_NAME}}", actualTypeName);
    } else {
      // Remove the conditional sections if no type name (use DOTALL flag for multiline)
      promptTemplate =
          promptTemplate.replaceAll("(?s)\\{\\{#TYPE_NAME\\}\\}.*?\\{\\{/TYPE_NAME\\}\\}", "");
    }

    // Remove feedback conditional sections since feedback is no longer supported
    promptTemplate = promptTemplate.replaceAll("\\{\\{#FEEDBACK\\}\\}.*?\\{\\{/FEEDBACK\\}\\}", "");

    // Handle similar type to avoid conditional sections
    if (typeNameToAvoid != null) {
      // Replace the conditional sections with actual content (use DOTALL flag for multiline)
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#SIMILAR_TYPE_TO_AVOID\\}\\}(.*?)\\{\\{/SIMILAR_TYPE_TO_AVOID\\}\\}",
              "$1");
      promptTemplate = promptTemplate.replace("{{SIMILAR_TYPE_TO_AVOID}}", typeNameToAvoid);

      // Also add instruction to avoid specific type name
      String avoidInstruction =
          "\n\nIMPORTANT: You must generate a DIFFERENT semantic type name than '"
              + typeNameToAvoid
              + "'. The user explicitly wants to create a new type that is distinct from the existing '"
              + typeNameToAvoid
              + "' type.";
      // Append to description
      String currentDescription = params.getDescription() != null ? params.getDescription() : "";
      promptTemplate =
          promptTemplate.replace("{{DESCRIPTION}}", currentDescription + avoidInstruction);
    } else {
      // Remove the conditional sections if no similar type to avoid (use DOTALL flag for multiline)
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#SIMILAR_TYPE_TO_AVOID\\}\\}.*?\\{\\{/SIMILAR_TYPE_TO_AVOID\\}\\}", "");
    }

    // Handle column header conditional sections
    if (params.getColumnHeader() != null && !params.getColumnHeader().isEmpty()) {
      // Replace the conditional sections with actual content
      promptTemplate =
          promptTemplate.replaceAll(
              "\\{\\{#COLUMN_HEADER\\}\\}(.*?)\\{\\{/COLUMN_HEADER\\}\\}", "$1");
      promptTemplate = promptTemplate.replace("{{COLUMN_HEADER}}", params.getColumnHeader());
    } else {
      // Remove the conditional sections if no column header
      promptTemplate =
          promptTemplate.replaceAll("\\{\\{#COLUMN_HEADER\\}\\}.*?\\{\\{/COLUMN_HEADER\\}\\}", "");
    }

    // Replace content examples
    String positiveContentExamplesStr =
        params.getPositiveContentExamples() != null
                && !params.getPositiveContentExamples().isEmpty()
            ? params.getPositiveContentExamples().stream()
                .map(ex -> "- " + ex)
                .collect(Collectors.joining("\n"))
            : "No positive content examples provided";
    promptTemplate =
        promptTemplate.replace("{{POSITIVE_CONTENT_EXAMPLES}}", positiveContentExamplesStr);

    String negativeContentExamplesStr =
        params.getNegativeContentExamples() != null
                && !params.getNegativeContentExamples().isEmpty()
            ? params.getNegativeContentExamples().stream()
                .map(ex -> "- " + ex)
                .collect(Collectors.joining("\n"))
            : "No negative content examples provided";
    promptTemplate =
        promptTemplate.replace("{{NEGATIVE_CONTENT_EXAMPLES}}", negativeContentExamplesStr);

    // Replace header examples
    String positiveHeaderExamplesStr =
        params.getPositiveHeaderExamples() != null && !params.getPositiveHeaderExamples().isEmpty()
            ? params.getPositiveHeaderExamples().stream()
                .map(ex -> "- " + ex)
                .collect(Collectors.joining("\n"))
            : "No positive header examples provided";
    promptTemplate =
        promptTemplate.replace("{{POSITIVE_HEADER_EXAMPLES}}", positiveHeaderExamplesStr);

    String negativeHeaderExamplesStr =
        params.getNegativeHeaderExamples() != null && !params.getNegativeHeaderExamples().isEmpty()
            ? params.getNegativeHeaderExamples().stream()
                .map(ex -> "- " + ex)
                .collect(Collectors.joining("\n"))
            : "No negative header examples provided";
    promptTemplate =
        promptTemplate.replace("{{NEGATIVE_HEADER_EXAMPLES}}", negativeHeaderExamplesStr);

    String existingTypesStr = params.getExistingTypes().stream().collect(Collectors.joining("\n"));
    promptTemplate = promptTemplate.replace("{{EXISTING_TYPES}}", existingTypesStr);

    return promptTemplate;
  }

  public String loadPromptTemplate(String promptName) throws IOException {
    String fileName = PROMPTS_PATH + promptName + ".txt";
    ClassPathResource resource = new ClassPathResource(fileName);

    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    } catch (IOException e) {
      log.error("Failed to load prompt template: {}", fileName, e);
      throw new IOException("Failed to load prompt template: " + fileName, e);
    }
  }

  /** Builds prompt for regenerating data values based on example changes */
  public String buildRegenerateDataValuesPrompt(
      String semanticTypeName,
      String currentRegexPattern,
      List<String> positiveExamples,
      List<String> negativeExamples,
      String userDescription,
      String description)
      throws IOException {

    // Load the new template but use it in legacy mode
    String promptTemplate = loadPromptTemplate("regenerate-data-values");

    // Legacy mode - no delta tracking, just current state and feedback
    promptTemplate =
        promptTemplate.replace(
            "{{SEMANTIC_TYPE_NAME}}", semanticTypeName != null ? semanticTypeName : "");
    promptTemplate = promptTemplate.replace("{{PLUGIN_TYPE}}", "regex");
    promptTemplate =
        promptTemplate.replace("{{DESCRIPTION}}", description != null ? description : "");

    // Handle current state
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#CURRENT_REGEX_PATTERN\\}\\}(.*?)\\{\\{/CURRENT_REGEX_PATTERN\\}\\}", "$1");
    promptTemplate =
        promptTemplate.replace(
            "{{CURRENT_REGEX_PATTERN}}", currentRegexPattern != null ? currentRegexPattern : "");
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#CURRENT_LIST_VALUES\\}\\}.*?\\{\\{/CURRENT_LIST_VALUES\\}\\}", "");

    // Current examples
    if (positiveExamples != null && !positiveExamples.isEmpty()) {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_POSITIVE_EXAMPLES\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_EXAMPLES\\}\\}",
              "$1");
      String examples =
          positiveExamples.stream().map(ex -> "- " + ex).collect(Collectors.joining("\n"));
      promptTemplate = promptTemplate.replace("{{CURRENT_POSITIVE_EXAMPLES}}", examples);
      // Remove the inverted conditional since we have examples
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_POSITIVE_EXAMPLES\\}\\}.*?\\{\\{/CURRENT_POSITIVE_EXAMPLES\\}\\}",
              "");
    } else {
      // Remove the positive conditional
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_POSITIVE_EXAMPLES\\}\\}.*?\\{\\{/CURRENT_POSITIVE_EXAMPLES\\}\\}",
              "");
      // Keep the inverted conditional content
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_POSITIVE_EXAMPLES\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_EXAMPLES\\}\\}",
              "$1");
    }

    if (negativeExamples != null && !negativeExamples.isEmpty()) {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_NEGATIVE_EXAMPLES\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_EXAMPLES\\}\\}",
              "$1");
      String examples =
          negativeExamples.stream().map(ex -> "- " + ex).collect(Collectors.joining("\n"));
      promptTemplate = promptTemplate.replace("{{CURRENT_NEGATIVE_EXAMPLES}}", examples);
      // Remove the inverted conditional since we have examples
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_NEGATIVE_EXAMPLES\\}\\}.*?\\{\\{/CURRENT_NEGATIVE_EXAMPLES\\}\\}",
              "");
    } else {
      // Remove the negative conditional
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_NEGATIVE_EXAMPLES\\}\\}.*?\\{\\{/CURRENT_NEGATIVE_EXAMPLES\\}\\}",
              "");
      // Keep the inverted conditional content
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_NEGATIVE_EXAMPLES\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_EXAMPLES\\}\\}",
              "$1");
    }

    // Remove all change tracking sections for legacy mode (use DOTALL flag for multiline)
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#PLUGIN_VALUE_CHANGES\\}\\}.*?\\{\\{/PLUGIN_VALUE_CHANGES\\}\\}", "");
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#EXAMPLE_CHANGES\\}\\}.*?\\{\\{/EXAMPLE_CHANGES\\}\\}", "");

    // Add natural language feedback
    if (userDescription != null && !userDescription.isEmpty()) {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#NATURAL_LANGUAGE_FEEDBACK\\}\\}(.*?)\\{\\{/NATURAL_LANGUAGE_FEEDBACK\\}\\}",
              "$1");
      promptTemplate = promptTemplate.replace("{{NATURAL_LANGUAGE_FEEDBACK}}", userDescription);
    } else {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#NATURAL_LANGUAGE_FEEDBACK\\}\\}.*?\\{\\{/NATURAL_LANGUAGE_FEEDBACK\\}\\}",
              "");
    }

    // Handle plugin type sections
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#PLUGIN_TYPE_REGEX\\}\\}(.*?)\\{\\{/PLUGIN_TYPE_REGEX\\}\\}", "$1");
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#PLUGIN_TYPE_LIST\\}\\}.*?\\{\\{/PLUGIN_TYPE_LIST\\}\\}", "");

    return promptTemplate;
  }

  /** Builds prompt for regenerating header patterns based on example changes */
  public String buildRegenerateHeaderValuesPrompt(
      String semanticTypeName,
      String currentHeaderPatterns,
      List<String> positiveExamples,
      List<String> negativeExamples,
      String userDescription)
      throws IOException {

    // Load the new template but use it in legacy mode
    String promptTemplate = loadPromptTemplate("regenerate-header-values");

    // Legacy mode - no delta tracking
    promptTemplate =
        promptTemplate.replace(
            "{{SEMANTIC_TYPE_NAME}}", semanticTypeName != null ? semanticTypeName : "");
    promptTemplate = promptTemplate.replace("{{DESCRIPTION}}", "");

    // Current header patterns - simplified format
    if (currentHeaderPatterns != null && !currentHeaderPatterns.isEmpty()) {
      // Remove the mustache conditional and keep only the pattern listing
      promptTemplate =
          promptTemplate.replaceFirst(
              "(?s)Current Header Patterns:\\s*\\{\\{#CURRENT_HEADER_PATTERNS\\}\\}.*?\\{\\{/CURRENT_HEADER_PATTERNS\\}\\}",
              "Current Header Patterns:\n- Patterns: " + currentHeaderPatterns);
      // Remove the "no patterns" fallback
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_HEADER_PATTERNS\\}\\}.*?\\{\\{/CURRENT_HEADER_PATTERNS\\}\\}",
              "");
    } else {
      // Remove the pattern section entirely and keep the "no patterns" message
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_HEADER_PATTERNS\\}\\}.*?\\{\\{/CURRENT_HEADER_PATTERNS\\}\\}",
              "");
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_HEADER_PATTERNS\\}\\}(.*?)\\{\\{/CURRENT_HEADER_PATTERNS\\}\\}",
              "$1");
    }

    // Current examples - handle comma-separated values
    if (positiveExamples != null && !positiveExamples.isEmpty()) {
      // Remove both the "if exists" and "if not exists" template markers
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_POSITIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_HEADERS\\}\\}",
              "$1");
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_POSITIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_HEADERS\\}\\}",
              "");

      // Split comma-separated values and flatten the list
      String examples =
          positiveExamples.stream()
              .flatMap(ex -> Arrays.stream(ex.split(",\\s*")))
              .map(ex -> "- " + ex.trim())
              .filter(ex -> !ex.equals("- "))
              .collect(Collectors.joining("\n"));
      promptTemplate = promptTemplate.replace("{{CURRENT_POSITIVE_HEADERS}}", examples);
    } else {
      // Remove the "if exists" template and keep the "if not exists" content
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_POSITIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_HEADERS\\}\\}",
              "");
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_POSITIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_POSITIVE_HEADERS\\}\\}",
              "$1");
    }

    if (negativeExamples != null && !negativeExamples.isEmpty()) {
      // Remove both the "if exists" and "if not exists" template markers
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_NEGATIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_HEADERS\\}\\}",
              "$1");
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_NEGATIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_HEADERS\\}\\}",
              "");

      // Split comma-separated values and flatten the list
      String examples =
          negativeExamples.stream()
              .flatMap(ex -> Arrays.stream(ex.split(",\\s*")))
              .map(ex -> "- " + ex.trim())
              .filter(ex -> !ex.equals("- "))
              .collect(Collectors.joining("\n"));
      promptTemplate = promptTemplate.replace("{{CURRENT_NEGATIVE_HEADERS}}", examples);
    } else {
      // Remove the "if exists" template and keep the "if not exists" content
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#CURRENT_NEGATIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_HEADERS\\}\\}",
              "");
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{\\^CURRENT_NEGATIVE_HEADERS\\}\\}(.*?)\\{\\{/CURRENT_NEGATIVE_HEADERS\\}\\}",
              "$1");
    }

    // Remove all change tracking sections
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#HEADER_PATTERN_CHANGES\\}\\}.*?\\{\\{/HEADER_PATTERN_CHANGES\\}\\}", "");
    promptTemplate =
        promptTemplate.replaceAll(
            "(?s)\\{\\{#EXAMPLE_CHANGES\\}\\}.*?\\{\\{/EXAMPLE_CHANGES\\}\\}", "");

    // Natural language feedback
    if (userDescription != null && !userDescription.isEmpty()) {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#NATURAL_LANGUAGE_FEEDBACK\\}\\}(.*?)\\{\\{/NATURAL_LANGUAGE_FEEDBACK\\}\\}",
              "$1");
      promptTemplate = promptTemplate.replace("{{NATURAL_LANGUAGE_FEEDBACK}}", userDescription);
    } else {
      promptTemplate =
          promptTemplate.replaceAll(
              "(?s)\\{\\{#NATURAL_LANGUAGE_FEEDBACK\\}\\}.*?\\{\\{/NATURAL_LANGUAGE_FEEDBACK\\}\\}",
              "");
    }

    // Clean up any remaining mustache template artifacts in the response format section
    promptTemplate = promptTemplate.replaceAll("\\{\\{#EACH_PATTERN\\}\\}", "");
    promptTemplate = promptTemplate.replaceAll("\\{\\{/EACH_PATTERN\\}\\}", "");
    promptTemplate = promptTemplate.replaceAll("\\{\\{#IF_NEW_OR_MODIFIED\\}\\}", "");
    promptTemplate = promptTemplate.replaceAll("\\{\\{/IF_NEW_OR_MODIFIED\\}\\}", "");
    promptTemplate = promptTemplate.replaceAll("\\{\\{PATTERN\\}\\}", "{PATTERN}");
    promptTemplate = promptTemplate.replaceAll("\\{\\{CHANGE_TYPE\\}\\}", "{CHANGE_TYPE}");
    promptTemplate =
        promptTemplate.replaceAll("\\{\\{POSITIVE_EXAMPLE\\}\\}", "{POSITIVE_EXAMPLE}");
    promptTemplate =
        promptTemplate.replaceAll("\\{\\{NEGATIVE_EXAMPLE\\}\\}", "{NEGATIVE_EXAMPLE}");
    promptTemplate =
        promptTemplate.replaceAll("\\{\\{PATTERN_RATIONALE\\}\\}", "{PATTERN_RATIONALE}");

    return promptTemplate;
  }
}

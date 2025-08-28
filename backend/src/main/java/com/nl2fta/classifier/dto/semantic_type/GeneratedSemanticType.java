package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of semantic type generation")
public class GeneratedSemanticType {

  @Schema(
      description = "Type of result - 'generated', 'existing', or 'error'",
      example = "generated")
  private String resultType;

  @Schema(description = "Generated semantic type identifier", example = "CUSTOM.EMPLOYEE_ID")
  private String semanticType;

  @Schema(description = "Description of the semantic type", example = "Employee ID format")
  private String description;

  @Schema(
      description = "Base type for FTA - STRING, LONG, DOUBLE, LOCALDATE, LOCALDATETIME",
      example = "STRING")
  private String baseType;

  @Schema(description = "Plugin type - 'regex' or 'list'", example = "regex")
  private String pluginType;

  @Schema(description = "Regular expression pattern", example = "E\\\\d{5}[PF]")
  private String regexPattern;

  @Schema(description = "List of valid values")
  private List<String> listValues;

  @Schema(description = "Broader fallback regex pattern for list types")
  private String backout;

  @Schema(description = "Examples of data values that match the type")
  private List<String> positiveContentExamples;

  @Schema(description = "Examples of data values that should not match")
  private List<String> negativeContentExamples;

  @Schema(description = "Examples of column headers that identify this type")
  private List<String> positiveHeaderExamples;

  @Schema(description = "Examples of column headers that should not identify this type")
  private List<String> negativeHeaderExamples;

  @Schema(description = "Data Threshold for matching", example = "0.95")
  private double confidenceThreshold;

  @Schema(description = "Multiple header patterns with individual confidence levels and examples")
  private List<HeaderPattern> headerPatterns;

  @Schema(
      description = "Priority for semantic type matching (must be >= 2000 for custom types)",
      example = "2000")
  private Integer priority;

  @Schema(description = "Explanation of the generation process")
  private String explanation;

  // Fields for existing type detection
  @Schema(description = "Name of existing type that matches", example = "NAME.FIRST")
  private String existingTypeMatch;

  @Schema(description = "Description of the existing type")
  private String existingTypeDescription;

  @Schema(description = "Pattern of the existing type (if regex)")
  private String existingTypePattern;

  @Schema(description = "Header patterns of the existing type")
  private List<String> existingTypeHeaderPatterns;

  @Schema(description = "Whether the existing type is built-in (true) or custom (false)")
  private boolean existingTypeIsBuiltIn;

  @Schema(description = "Suggested action - 'extend', 'replace', or 'create_different'")
  private String suggestedAction;

  @Schema(description = "Detailed comparison with existing type if similarity was found")
  private SemanticTypeComparison comparison;

  @Schema(description = "Correlation ID to link all logs for this generation flow")
  @com.fasterxml.jackson.annotation.JsonProperty("correlationId")
  @lombok.EqualsAndHashCode.Exclude
  @lombok.ToString.Exclude
  private String correlationId;
}

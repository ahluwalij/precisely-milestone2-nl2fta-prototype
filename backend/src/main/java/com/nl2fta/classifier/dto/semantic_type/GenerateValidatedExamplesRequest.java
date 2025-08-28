package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for generating validated examples with background validation")
public class GenerateValidatedExamplesRequest {

  @NotBlank(message = "Regex pattern is required")
  @Schema(description = "The regex pattern to validate examples against", example = "^[A-Z][a-z]+$")
  private String regexPattern;

  @NotBlank(message = "Semantic type name is required")
  @Schema(description = "Name of the semantic type", example = "NAME.FIRST")
  private String semanticTypeName;

  @Schema(description = "Existing positive examples to include in context")
  private List<String> existingPositiveExamples;

  @Schema(description = "Existing negative examples to include in context")
  private List<String> existingNegativeExamples;

  @Schema(
      description = "User description for the type of examples to generate",
      example = "Generate examples with international names")
  private String userDescription;

  @Builder.Default
  @Schema(description = "Only generate positive examples", example = "false")
  private boolean generatePositiveOnly = false;

  @Builder.Default
  @Schema(description = "Only generate negative examples", example = "false")
  private boolean generateNegativeOnly = false;

  @Builder.Default
  @Schema(description = "Maximum retry attempts if validation fails", example = "3")
  private int maxRetries = 3;

  @Schema(description = "Description of the semantic type")
  private String description;

  @Schema(description = "Plugin type (regex or list)")
  private String pluginType;

  @Builder.Default
  @JsonProperty("isPatternImprovement")
  @Schema(
      description = "Whether this is a pattern improvement request for data content",
      example = "false")
  private boolean isPatternImprovement = false;

  @Builder.Default
  @JsonProperty("isHeaderPatternImprovement")
  @Schema(description = "Whether this is a header pattern improvement request", example = "false")
  private boolean isHeaderPatternImprovement = false;
}

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
@Schema(description = "Header pattern with its own confidence and examples")
public class HeaderPattern {

  @Schema(
      description = "Regular expression pattern for header matching",
      example = "(?i)(employee_id|emp_id)")
  private String regExp;

  @Schema(description = "Confidence level for this pattern", example = "95")
  private int confidence;

  @Schema(description = "Whether this pattern is mandatory", example = "true")
  @Builder.Default
  private boolean mandatory = true;

  @Schema(description = "Positive examples that match this pattern")
  private List<String> positiveExamples;

  @Schema(description = "Negative examples that should not match this pattern")
  private List<String> negativeExamples;

  @Schema(description = "Explanation for this pattern and confidence level")
  private String rationale;
}

package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to run a single-description optimization job")
public class OptimizationRequest {

  @NotBlank
  @Schema(description = "Natural language description of the semantic type")
  private String description;

  @Schema(description = "Optional explicit type name suggestion")
  private String typeName;

  @Schema(description = "Positive value examples (should match)")
  private List<String> positiveValues;

  @Schema(description = "Negative value examples (should not match)")
  private List<String> negativeValues;

  @Schema(description = "Positive header examples (boost detection)")
  private List<String> positiveHeaders;

  @Schema(description = "Negative header examples (guard against)")
  private List<String> negativeHeaders;

  @Schema(description = "CSV file name in evaluator datasets to profile (optional)")
  private String datasetCsv;

  @Schema(description = "If true, require finite list match for acceptance")
  @Builder.Default
  private boolean requireFinite = false;

  @Schema(description = "Minimum samples required to consider a column")
  @Builder.Default
  private int minSamples = 5;

  @Schema(description = "Threshold for finite list acceptance (0-100)")
  @Builder.Default
  private int finiteThreshold = 92;

  @Schema(description = "Threshold for regex acceptance (0-100)")
  @Builder.Default
  private int regexThreshold = 96;

  @Schema(description = "Top-K unmatched values to retain for diagnostics")
  @Builder.Default
  private int topKUnmatched = 10;

  @Schema(description = "If true, persist the generated types and index vectors")
  @Builder.Default
  private boolean persist = false;

  @Schema(description = "If true, auto-extend finite list with suggested additions (top-K)")
  @Builder.Default
  private boolean autoLearn = true;
}



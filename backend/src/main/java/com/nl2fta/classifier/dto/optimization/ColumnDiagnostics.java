package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Diagnostics and coverage metrics for a single column optimization run")
public class ColumnDiagnostics {

  @Schema(description = "Coverage of finite (list) matching in [0,1]")
  private double finiteCoverage;

  @Schema(description = "Coverage of regex shape matching in [0,1]")
  private double regexCoverage;

  @Schema(description = "Number of non-null values evaluated")
  private int nonNullCount;

  @Schema(description = "Number of sampled values considered (post limits)")
  private int sampleCount;

  @Schema(description = "Top unmatched values after normalization")
  private List<String> unmatchedTop;

  @Schema(description = "Frequencies for unmatched values (normalized)")
  private Map<String, Integer> unmatchedFrequencies;

  @Schema(description = "Suggested list additions inferred from gaps")
  private List<String> suggestedAdditions;

  @Schema(description = "Suggested header regex candidates inferred from header examples")
  private List<String> suggestedHeaderPatterns;

  @Schema(description = "Decision code for this column (finite_match, regex_match, rejected, etc.)")
  private String decisionReason;
}



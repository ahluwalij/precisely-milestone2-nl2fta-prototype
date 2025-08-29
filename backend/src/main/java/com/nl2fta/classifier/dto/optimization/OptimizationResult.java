package com.nl2fta.classifier.dto.optimization;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of running an optimization job for a single description")
public class OptimizationResult {

  @Schema(description = "Derived or confirmed semantic type identifier")
  private String semanticType;

  @Schema(description = "Candidate finite (list) plugin produced")
  private CustomSemanticType finitePlugin;

  @Schema(description = "Candidate regex plugin produced (when applicable)")
  private CustomSemanticType regexPlugin;

  @Schema(description = "Header regex patterns with confidences to register")
  private List<HeaderPatternCandidate> headerPatterns;

  @Schema(description = "Diagnostics per profiled column")
  private List<PerColumnOutcome> outcomes;

  @Schema(description = "Freeform rationale and notes")
  private String rationale;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HeaderPatternCandidate {
    private String pattern;
    private int confidence;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PerColumnOutcome {
    private String dataset;
    private String tableName;
    private String columnName;
    private ColumnDiagnostics diagnostics;
  }
}



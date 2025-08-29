package com.nl2fta.classifier.dto.optimization;

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
@Schema(description = "Result of F1 evaluation for a CSV")
public class F1EvaluationResult {

  private double precision;
  private double recall;
  private double f1;

  private int totalColumns;
  private int evaluatedColumns;
  private int truePositives;
  private int falsePositives;
  private int falseNegatives;

  private List<PerColumn> details;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PerColumn {
    private String columnName;
    private String predictedType;
    private String expectedType;
    private boolean correct;
  }
}



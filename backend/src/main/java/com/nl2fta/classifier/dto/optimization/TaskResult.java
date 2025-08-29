package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of an agent's task execution")
public class TaskResult {
  private String taskId;
  private OptimizationResult optimizationResult;
  private F1EvaluationResult baseline;
  private F1EvaluationResult after;
  private double deltaF1;
  private boolean persisted;
  private String notes;
  private Instant completedAt;
}



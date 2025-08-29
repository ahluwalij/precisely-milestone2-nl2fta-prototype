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
@Schema(description = "Task assigned to an agent")
public class AgentTask {
  public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

  private String taskId;
  private String agentId;
  private OptimizationRequest optimization;
  private F1EvaluationRequest evaluation;
  private Status status;
  private Instant createdAt;
  private Instant updatedAt;
}



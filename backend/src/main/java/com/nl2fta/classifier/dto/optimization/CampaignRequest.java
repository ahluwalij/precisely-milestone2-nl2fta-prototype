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
@Schema(description = "Batch campaign to assign multiple optimization tasks to agents")
public class CampaignRequest {
  private String campaignId;
  private List<OptimizationRequest> optimizations;
  private List<F1EvaluationRequest> evaluations;
  private int maxParallelAgents;
}



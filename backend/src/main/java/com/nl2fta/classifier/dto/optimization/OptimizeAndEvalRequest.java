package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Run optimization then evaluate F1 in a single call")
public class OptimizeAndEvalRequest {

  @Valid
  @NotNull
  private OptimizationRequest optimization;

  @Valid
  @NotNull
  private F1EvaluationRequest evaluation;
}



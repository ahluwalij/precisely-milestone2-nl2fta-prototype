package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to evaluate F1 on a single CSV with ground truth mappings")
public class F1EvaluationRequest {

  @NotBlank
  @Schema(description = "Dataset CSV file under evaluator/datasets/data (relative path)")
  private String datasetCsv;

  @Schema(description = "Columns to evaluate (optional; if empty, evaluate all)")
  private List<String> columns;

  @Schema(description = "Map-like flattened pairs: columnName=semanticType (semanticType must match expected)")
  private List<String> groundTruthPairs;
}



package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for semantic type comparison results. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed comparison between two semantic types")
public class SemanticTypeComparison {

  @Schema(description = "Name of the existing semantic type", example = "NAME.FIRST")
  private String existingTypeName;

  @Schema(description = "Description of the existing type", example = "First Name")
  private String existingTypeDescription;

  @Schema(description = "Similarity score between 0 and 1", example = "0.95")
  private double similarityScore;

  @Schema(description = "Similarity percentage for display", example = "95.0")
  public double getSimilarityPercentage() {
    return similarityScore * 100;
  }

  @Schema(description = "List of similarities between the types")
  private List<String> similarities;

  @Schema(description = "List of differences between the types")
  private List<Difference> differences;

  @Schema(description = "Whether to recommend using the existing type")
  private boolean recommendUseExisting;

  @Schema(description = "Reason for the recommendation")
  private String recommendationReason;

  @Schema(description = "Summary of the comparison")
  private String summary;

  /** Represents a specific difference between semantic types. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "A specific difference between semantic types")
  public static class Difference {

    @Schema(description = "Aspect of difference", example = "Scope")
    private String aspect;

    @Schema(description = "Description of the difference")
    private String description;
  }
}

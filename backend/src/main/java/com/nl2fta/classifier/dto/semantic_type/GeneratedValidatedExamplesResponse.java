package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing validated examples that have passed regex validation")
public class GeneratedValidatedExamplesResponse {

  @Schema(description = "Generated positive examples (all guaranteed to match the regex)")
  private List<String> positiveExamples;

  @Schema(description = "Generated negative examples (all guaranteed to NOT match the regex)")
  private List<String> negativeExamples;

  @Schema(description = "Number of attempts required to generate valid examples", example = "2")
  private int attemptsUsed;

  @Schema(description = "Whether validation was successful", example = "true")
  private boolean validationSuccessful;

  @Schema(description = "Error message if generation failed")
  private String error;

  @Schema(description = "Detailed validation summary")
  private ValidationSummary validationSummary;

  @Schema(description = "Updated regex pattern (for data pattern improvements)")
  private String updatedRegexPattern;

  @Schema(description = "Updated header patterns (for header pattern improvements)")
  private List<String> updatedHeaderPatterns;

  @Schema(description = "Rationale for pattern updates or example generation")
  private String rationale;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Summary of the validation process")
  public static class ValidationSummary {

    @Schema(description = "Total positive examples generated before validation", example = "5")
    private int totalPositiveGenerated;

    @Schema(description = "Total negative examples generated before validation", example = "5")
    private int totalNegativeGenerated;

    @Schema(description = "Positive examples that passed validation", example = "4")
    private int positiveExamplesValidated;

    @Schema(description = "Negative examples that passed validation", example = "5")
    private int negativeExamplesValidated;

    @Schema(description = "Positive examples that failed validation", example = "1")
    private int positiveExamplesFailed;

    @Schema(description = "Negative examples that failed validation", example = "0")
    private int negativeExamplesFailed;
  }
}

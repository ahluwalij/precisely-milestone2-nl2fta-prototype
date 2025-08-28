package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Response from pattern update/improvement operations. Used for validating examples against
 * improved patterns.
 */
@Data
@Builder
public class PatternUpdateResponse {
  private String improvedPattern;
  private List<String> newPositiveContentExamples;
  private List<String> newNegativeContentExamples;
  private List<String> newPositiveHeaderExamples;
  private List<String> newNegativeHeaderExamples;
  private List<HeaderPattern> updatedHeaderPatterns;
  private String explanation;
  private String errorMessage;
}

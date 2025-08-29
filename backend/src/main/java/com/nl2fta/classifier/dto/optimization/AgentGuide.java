package com.nl2fta.classifier.dto.optimization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optimization guide for coordinating multiple agents")
public class AgentGuide {
  private String name;
  private String version;
  private String objective;
  private List<String> datasets; // relative CSV paths under evaluator/datasets/data
  private List<String> priorityDescriptions; // e.g., 3,4,6
  private Map<String, Object> constraints;
  private List<PlaybookStep> playbook;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PlaybookStep {
    private String id;
    private String description;
    private Map<String, Object> parameters;
  }
}



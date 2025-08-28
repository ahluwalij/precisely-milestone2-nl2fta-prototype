package com.nl2fta.classifier.dto.analysis;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableClassificationRequest {

  @JsonProperty("table_name")
  private String tableName;

  @NotNull
  @NotEmpty
  @JsonProperty("columns")
  private List<String> columns;

  @NotNull
  @JsonProperty("data")
  private List<Map<String, Object>> data;

  @JsonProperty("max_samples")
  private Integer maxSamples;

  @JsonProperty("locale")
  private String locale;

  @JsonProperty("include_statistics")
  private Boolean includeStatistics;

  @JsonProperty("custom_only")
  private Boolean customOnly;

  @JsonProperty("use_all_semantic_types")
  private Boolean useAllSemanticTypes;
}

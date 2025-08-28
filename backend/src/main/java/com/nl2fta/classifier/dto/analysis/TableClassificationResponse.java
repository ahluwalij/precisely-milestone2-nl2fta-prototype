package com.nl2fta.classifier.dto.analysis;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableClassificationResponse {

  @JsonProperty("table_name")
  private String tableName;

  @JsonProperty("column_classifications")
  private Map<String, ColumnClassification> columnClassifications;

  @JsonProperty("processing_metadata")
  private ProcessingMetadata processingMetadata;

  @JsonProperty("data")
  private List<Map<String, Object>> data;

  @JsonProperty("analysis_id")
  private String analysisId;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ColumnClassification {

    @JsonProperty("column_name")
    private String columnName;

    @JsonProperty("base_type")
    private String baseType;

    @JsonProperty("semantic_type")
    private String semanticType;

    @JsonProperty("type_modifier")
    private String typeModifier;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("pattern")
    private String pattern;

    @JsonProperty("description")
    private String description;

    @JsonProperty("is_built_in")
    private Boolean isBuiltIn;

    @JsonProperty("statistics")
    private Statistics statistics;

    @JsonProperty("shape_details")
    private List<ShapeDetail> shapeDetails;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Statistics {

    @JsonProperty("sample_count")
    private Long sampleCount;

    @JsonProperty("null_count")
    private Long nullCount;

    @JsonProperty("blank_count")
    private Long blankCount;

    @JsonProperty("distinct_count")
    private Long distinctCount;

    @JsonProperty("min_value")
    private String minValue;

    @JsonProperty("max_value")
    private String maxValue;

    @JsonProperty("min_length")
    private Integer minLength;

    @JsonProperty("max_length")
    private Integer maxLength;

    @JsonProperty("mean")
    private Double mean;

    @JsonProperty("standard_deviation")
    private Double standardDeviation;

    @JsonProperty("cardinality")
    private Integer cardinality;

    @JsonProperty("uniqueness")
    private Double uniqueness;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShapeDetail {

    @JsonProperty("shape")
    private String shape;

    @JsonProperty("count")
    private Long count;

    @JsonProperty("examples")
    private List<String> examples;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ProcessingMetadata {

    @JsonProperty("total_columns")
    private Integer totalColumns;

    @JsonProperty("analyzed_columns")
    private Integer analyzedColumns;

    @JsonProperty("total_rows_processed")
    private Integer totalRowsProcessed;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    @JsonProperty("fta_version")
    private String ftaVersion;

    @JsonProperty("locale_used")
    private String localeUsed;
  }
}

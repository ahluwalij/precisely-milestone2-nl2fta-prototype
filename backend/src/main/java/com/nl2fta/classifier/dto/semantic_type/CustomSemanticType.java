package com.nl2fta.classifier.dto.semantic_type;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Custom semantic type definition")
public class CustomSemanticType {

  @NotBlank
  @Schema(description = "Unique semantic type identifier", example = "IDENTIFIER.EMPLOYEE_ID")
  private String semanticType;

  @NotBlank
  @Schema(
      description = "Description of the semantic type",
      example = "Employee ID - Format: E followed by 5 digits and P/F")
  private String description;

  @NotBlank
  @Pattern(regexp = "regex|list|java")
  @Schema(description = "Plugin type: regex, list, or java", example = "regex")
  private String pluginType;

  @Schema(description = "Locale configurations for the type")
  private List<LocaleConfig> validLocales;

  @Schema(description = "Data Threshold (0-100)", example = "95")
  @Builder.Default
  private Integer threshold = 95;

  @NotBlank
  @Schema(description = "Base type", example = "STRING")
  @Builder.Default
  private String baseType = "STRING";

  @Schema(description = "Documentation references")
  private List<Documentation> documentation;

  @Schema(description = "Content configuration for list types")
  private ContentConfig content;

  @Schema(description = "Java class name for java plugin types")
  private String clazz;

  @Schema(description = "Signature for java plugin types")
  private String signature;

  @Schema(
      description =
          "Tracks if this originated from a built-in FTA type (true) or is user-created (false). "
              + "All types are stored as custom types in FTA for override capability, "
              + "but this flag helps the frontend show the correct badge color.")
  @Builder.Default
  private Boolean isBuiltIn = false;

  // Additional fields for conversion from FTA PluginDefinition
  private String minimum;
  private String maximum;
  private Integer minSamples;
  private Boolean minMaxPresent;
  private Boolean localeSensitive;
  private Integer priority;
  private String pluginOptions;
  private String backout;
  private List<String> invalidList;
  private List<String> ignoreList;

  @Schema(description = "Creation timestamp in milliseconds since epoch. Built-in types use 0.")
  private Long createdAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Locale-specific configuration")
  public static class LocaleConfig {
    @Schema(description = "Locale tag (* for all locales)", example = "*")
    @Builder.Default
    private String localeTag = "*";

    @Schema(description = "Header regular expressions for context matching")
    private List<HeaderRegExp> headerRegExps;

    @Schema(description = "Match entries for regex types")
    private List<MatchEntry> matchEntries;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Header regular expression configuration")
  public static class HeaderRegExp {
    @NotBlank
    @Schema(description = "Regular expression pattern", example = ".*(?i)(employee.*id|emp.*id).*")
    private String regExp;

    @Schema(description = "Confidence level", example = "99")
    @Builder.Default
    private Integer confidence = 99;

    @Schema(description = "Whether the header match is mandatory", example = "true")
    @Builder.Default
    private Boolean mandatory = true;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Match entry for regex types")
  public static class MatchEntry {
    @Schema(
        description = "Regular expressions to match against FTA-generated patterns",
        example = "[\"\\\\d{2,4}\"]")
    private List<String> regExpsToMatch;

    @NotBlank
    @Schema(description = "Regular expression pattern", example = "E\\\\d{5}[PF]")
    private String regExpReturned;

    @Schema(description = "Whether the regex is complete", example = "true")
    @Builder.Default
    private Boolean isRegExpComplete = true;

    @Schema(
        description = "Description of what this match entry represents",
        example = "Employee ID pattern")
    private String description;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Documentation reference")
  public static class Documentation {
    @Schema(description = "Source of documentation", example = "internal")
    private String source;

    @Schema(description = "Reference URL or description", example = "Company Employee ID Format")
    private String reference;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Content configuration for list types")
  public static class ContentConfig {
    @Schema(description = "Content type", example = "resource")
    private String type;

    @Schema(description = "Reference to content", example = "/reference/employee_ids.csv")
    private String reference;

    @Schema(description = "Inline values for list types")
    private List<String> values;
  }
}

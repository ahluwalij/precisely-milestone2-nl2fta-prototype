package com.nl2fta.classifier.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cobber.fta.AnalyzerContext;
import com.cobber.fta.RecordAnalysisResult;
import com.cobber.fta.RecordAnalyzer;
import com.cobber.fta.TextAnalysisResult;
import com.cobber.fta.TextAnalyzer;
import com.cobber.fta.dates.DateTimeParser.DateResolutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ColumnClassification;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ProcessingMetadata;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ShapeDetail;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.Statistics;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeRegistryService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeValidationService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableClassificationService {

  private final CustomSemanticTypeService customSemanticTypeService;
  private final SemanticTypeRegistryService semanticTypeRegistry;
  private final SemanticTypeValidationService validationService;
  private final HybridCustomSemanticTypeRepository hybridRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${fta.version:16.0.3}")
  private String ftaVersion;

  @Value("${fta.detect.window:20}")
  private int detectWindow;

  @Value("${fta.max.cardinality:12000}")
  private int maxCardinality;

  @Value("${fta.enable-default-semantic-types:false}")
  private boolean enableDefaultSemanticTypes;

  // Add a map to track sample values for each column
  private final Map<String, Set<String>> columnSampleValues = new HashMap<>();

  public TableClassificationResponse classifyTable(TableClassificationRequest request) {
    long startTime = System.currentTimeMillis();

    log.info(
        "Starting table classification for table: {} with {} columns",
        request.getTableName(),
        request.getColumns().size());

    Map<String, ColumnClassification> columnClassifications = new LinkedHashMap<>();

    // Clear previous sample values
    columnSampleValues.clear();

    try {
      // Create context for record-based analysis
      String[] headers = request.getColumns().toArray(new String[0]);
      AnalyzerContext context =
          new AnalyzerContext(
              null,
              DateResolutionMode.Auto,
              request.getTableName() != null ? request.getTableName() : "table",
              headers);

      // Set locale (default to en-US if not specified)
      String localeStr = request.getLocale() != null ? request.getLocale() : "en-US";
      Locale locale = Locale.forLanguageTag(localeStr.replace('_', '-'));

      // In evaluation custom-only mode, do NOT force semantic types by header.
      // Forcing unregistered types can lead to analyzer inconsistencies.
      // We rely on registered plugins below.

      // Create template analyzer
      TextAnalyzer template = new TextAnalyzer(context);
      template.setLocale(locale);
      template.setDetectWindow(detectWindow);
      template.setMaxCardinality(maxCardinality);

      // Toggle built-ins vs custom types based on request mode
      boolean customOnlyMode = request.getCustomOnly() != null && request.getCustomOnly();
      boolean useAllSemanticTypes =
          request.getUseAllSemanticTypes() != null && request.getUseAllSemanticTypes();
      if (customOnlyMode && !useAllSemanticTypes) {
        // Custom-only evaluation: disable built-ins; register only user-generated custom types
        // (exclude converted built-ins)
        template.configure(TextAnalyzer.Feature.DEFAULT_SEMANTIC_TYPES, false);
        log.info("EVALUATION MODE: custom-only -> built-ins disabled");
      } else if (!useAllSemanticTypes) {
        // Baseline (or normal) evaluation: enable built-ins; do not register repository custom
        // types
        template.configure(TextAnalyzer.Feature.DEFAULT_SEMANTIC_TYPES, true);
        log.info(
            "BASELINE/NORMAL MODE: built-ins enabled; repository custom types will not be registered");
      } else {
        // Frontend request: combine converted built-ins + user customs
        template.configure(TextAnalyzer.Feature.DEFAULT_SEMANTIC_TYPES, false);
        log.info(
            "COMBINED MODE: DEFAULT_SEMANTIC_TYPES disabled; will register converted built-ins + user custom types");
      }
      template.configure(TextAnalyzer.Feature.COLLECT_STATISTICS, true); // Keep statistics enabled
      template.configure(TextAnalyzer.Feature.DISTRIBUTIONS, true); // Keep distributions enabled

      // Initialize S3 repository only if we are in custom-only mode and need access to custom types
      if (customOnlyMode && !useAllSemanticTypes) {
        try {
          if (!hybridRepository.isUsingS3Storage()) {
            log.info(
                "Custom-only mode: attempting to initialize S3 repository for semantic type access");

            hybridRepository.initializeS3Repository();
            if (hybridRepository.isUsingS3Storage()) {
              log.info("S3 repository successfully initialized for classification");
            } else {
              log.warn("Failed to initialize S3 repository - custom types may not be available");
            }
          }
        } catch (Exception e) {
          log.warn("Error initializing S3 repository: {}", e.getMessage());
        }
      }

      // Register custom types only in custom-only mode (exclude converted built-ins)
      if (customOnlyMode && !useAllSemanticTypes && hybridRepository.isUsingS3Storage()) {
        customSemanticTypeService.registerSemanticTypesForEvaluation(template, true);
        log.info(
            "EVALUATION MODE: Registered custom types only (excluding converted built-in types)");
      }
      if (useAllSemanticTypes) {
        // Combined registration: converted built-ins + custom repository types
        customSemanticTypeService.registerSemanticTypes(template);
      }

      // Get count of types for logging (only relevant in custom-only mode)
      int totalTypes = 0;
      if (customOnlyMode && !useAllSemanticTypes) {
        List<CustomSemanticType> allTypes = customSemanticTypeService.getAllCustomTypes();
        totalTypes = allTypes != null ? allTypes.size() : 0;
        log.info("EVALUATION MODE: Using custom types only (excluding converted built-in types)");
      } else if (!useAllSemanticTypes) {
        log.info("BASELINE/NORMAL MODE: Built-ins only; repository custom types not registered");
      } else {
        try {
          List<CustomSemanticType> all = customSemanticTypeService.getAllSemanticTypes();
          totalTypes = all != null ? all.size() : 0;
          log.info("COMBINED MODE: Registered {} total types", totalTypes);
        } catch (Exception ignore) {
        }
      }

      // Log registered plugins for debugging
      log.debug("Analyzing table with {} columns: {}", headers.length, Arrays.toString(headers));

      // Create record analyzer
      RecordAnalyzer recordAnalyzer = new RecordAnalyzer(template);

      // Process data rows
      int rowsProcessed = 0;
      int maxSamplesToProcess =
          request.getMaxSamples() != null ? request.getMaxSamples() : Integer.MAX_VALUE;

      try {
        for (Map<String, Object> row : request.getData()) {
          if (rowsProcessed >= maxSamplesToProcess) {
            break;
          }

          String[] values = new String[headers.length];
          for (int i = 0; i < headers.length; i++) {
            Object value = row.get(headers[i]);
            values[i] = value != null ? value.toString() : null;

            // Collect sample values for each column
            if (value != null && !value.toString().trim().isEmpty()) {
              String columnName = headers[i];
              Set<String> samples =
                  columnSampleValues.computeIfAbsent(columnName, k -> new LinkedHashSet<>());

              // Store up to 10 unique sample values per column
              if (samples.size() < 10) {
                samples.add(value.toString());
              }
            }
          }

          recordAnalyzer.train(values);
          rowsProcessed++;
        }
      } catch (Exception trainEx) {
        log.error(
            "Training failed: {}. Returning empty analysis to avoid 500.",
            trainEx.getMessage(),
            trainEx);
        return TableClassificationResponse.builder()
            .tableName(request.getTableName())
            .columnClassifications(columnClassifications)
            .data(request.getData())
            .processingMetadata(
                ProcessingMetadata.builder()
                    .totalColumns(request.getColumns().size())
                    .analyzedColumns(0)
                    .totalRowsProcessed(rowsProcessed)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .ftaVersion(ftaVersion)
                    .localeUsed(request.getLocale())
                    .build())
            .build();
      }

      // Get results with guard to prevent regex parse crashes from bubbling
      RecordAnalysisResult recordResult;
      try {
        recordResult = recordAnalyzer.getResult();
      } catch (Exception ex) {
        log.error(
            "FTA getResult failed: {}. Falling back to empty analysis to keep eval alive.",
            ex.getMessage());
        return TableClassificationResponse.builder()
            .tableName(request.getTableName())
            .columnClassifications(columnClassifications)
            .data(request.getData())
            .processingMetadata(
                ProcessingMetadata.builder()
                    .totalColumns(request.getColumns().size())
                    .analyzedColumns(0)
                    .totalRowsProcessed(0)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .ftaVersion(ftaVersion)
                    .localeUsed(request.getLocale())
                    .build())
            .build();
      }

      // Debug logging for custom list types
      log.debug("FTA Analysis complete. Processing {} columns", headers.length);

      // Log each column's detected type for debugging custom list types
      int idx = 0;
      for (TextAnalysisResult ftaResult : recordResult.getStreamResults()) {
        if (idx < headers.length) {
          String colName = headers[idx];
          String detectedType = ftaResult.getSemanticType();
          String pattern = ftaResult.getRegExp();
          log.debug(
              "Column '{}': Detected type='{}', Pattern='{}'", colName, detectedType, pattern);
          idx++;
        }
      }

      // Log raw FTA output for each column
      log.debug("=== RAW FTA OUTPUT FOR TABLE: {} ===", request.getTableName());

      // Process each column result
      int columnIndex = 0;
      // Preload repository custom types only in custom-only mode
      List<CustomSemanticType> repoCustomTypes = null;
      if (request.getCustomOnly() != null && request.getCustomOnly()) {
        try {
          repoCustomTypes = customSemanticTypeService.getCustomTypesOnly();
        } catch (Exception e) {
          log.warn("Unable to load repository custom types for override: {}", e.getMessage());
        }
      }
      for (TextAnalysisResult columnResult : recordResult.getStreamResults()) {
        String columnName = headers[columnIndex];

        // Log raw FTA result object in pretty format
        try {
          String prettyJson =
              objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(columnResult);
          log.debug("Column [{}] '{}' - Raw FTA Result:\n{}", columnIndex, columnName, prettyJson);
        } catch (Exception e) {
          // Fallback to toString if JSON serialization fails
          log.debug("Column [{}] '{}' - Raw FTA Result: {}", columnIndex, columnName, columnResult);
        }

        // Get detected semantic type - all types are now custom
        String detectedType = columnResult.getSemanticType();

        log.debug(
            "Column '{}': detected semantic type = '{}', confidence = {}",
            columnName,
            detectedType != null ? detectedType : "NONE",
            columnResult.getConfidence());

        ColumnClassification classification =
            buildColumnClassification(
                columnName,
                columnResult,
                request.getIncludeStatistics() != null ? request.getIncludeStatistics() : false);

        // If FTA produced no semantic type (base type only), attempt header+value override
        // in COMBINED front-end mode as well (useAllSemanticTypes=true)
        if (useAllSemanticTypes) {
          boolean missingSemantic =
              classification.getSemanticType() == null
                  || classification.getSemanticType().isEmpty();
          if (missingSemantic) {
            try {
              // Prefer repository customs, then consider converted built-ins
              List<CustomSemanticType> repoCustoms = customSemanticTypeService.getCustomTypesOnly();
              List<CustomSemanticType> allTypes = customSemanticTypeService.getAllSemanticTypes();
              if ((repoCustoms != null && !repoCustoms.isEmpty())
                  || (allTypes != null && !allTypes.isEmpty())) {
                // 1) Try header+values with repo customs first
                String override =
                    repoCustoms != null
                        ? decideSemanticTypeOverride(columnName, repoCustoms)
                        : null;
                // 2) If not found, try header+values with all types (includes converted built-ins)
                if (override == null && allTypes != null) {
                  override = decideSemanticTypeOverride(columnName, allTypes);
                }
                if (override != null) {
                  log.info(
                      "Combined-mode override applied: column '{}' -> semanticType '{}'",
                      columnName,
                      override);
                  classification =
                      ColumnClassification.builder()
                          .columnName(columnName)
                          .baseType(
                              columnResult.getType() != null
                                  ? columnResult.getType().toString()
                                  : "UNKNOWN")
                          .semanticType(override)
                          .typeModifier(columnResult.getTypeModifier())
                          .confidence(1.0)
                          .pattern(columnResult.getRegExp())
                          .description(generateDescription(columnResult, override))
                          .isBuiltIn(false)
                          .build();
                }
              }
            } catch (Exception e) {
              log.debug(
                  "Override check failed in combined mode for column '{}': {}",
                  columnName,
                  e.getMessage());
            }
          }
        }

        // In custom-only evaluation, prefer dataset custom plugin if FTA returned no semantic type
        if (request.getCustomOnly() != null && request.getCustomOnly()) {
          boolean missingSemantic =
              classification.getSemanticType() == null
                  || classification.getSemanticType().isEmpty();

          if (missingSemantic && repoCustomTypes != null && !repoCustomTypes.isEmpty()) {
            String override = decideSemanticTypeOverride(columnName, repoCustomTypes);
            if (override != null) {
              log.info(
                  "Custom-only override applied: column '{}' -> semanticType '{}'",
                  columnName,
                  override);

              classification =
                  ColumnClassification.builder()
                      .columnName(columnName)
                      .baseType(
                          columnResult.getType() != null
                              ? columnResult.getType().toString()
                              : "UNKNOWN")
                      .semanticType(override)
                      .typeModifier(columnResult.getTypeModifier())
                      .confidence(1.0)
                      .pattern(columnResult.getRegExp())
                      .description(generateDescription(columnResult, override))
                      .isBuiltIn(false)
                      .build();
            }
          }
        }

        columnClassifications.put(columnName, classification);
        columnIndex++;
      }

      long processingTime = System.currentTimeMillis() - startTime;

      return TableClassificationResponse.builder()
          .tableName(request.getTableName())
          .columnClassifications(columnClassifications)
          .data(request.getData()) // Include original data for frontend display
          .processingMetadata(
              ProcessingMetadata.builder()
                  .totalColumns(request.getColumns().size())
                  .analyzedColumns(columnClassifications.size())
                  .totalRowsProcessed(rowsProcessed)
                  .processingTimeMs(processingTime)
                  .ftaVersion(ftaVersion)
                  .localeUsed(request.getLocale())
                  .build())
          .build();

    } catch (Exception e) {
      log.error(
          "Unexpected error during table classification, returning empty analysis to keep eval alive",
          e);
      return TableClassificationResponse.builder()
          .tableName(request.getTableName())
          .columnClassifications(new LinkedHashMap<>())
          .data(request.getData())
          .processingMetadata(
              ProcessingMetadata.builder()
                  .totalColumns(request.getColumns() != null ? request.getColumns().size() : 0)
                  .analyzedColumns(0)
                  .totalRowsProcessed(0)
                  .processingTimeMs(0L)
                  .ftaVersion(ftaVersion)
                  .localeUsed(request.getLocale())
                  .build())
          .build();
    }
  }

  private ColumnClassification buildColumnClassification(
      String columnName, TextAnalysisResult result, boolean includeStatistics) {

    String semanticType = result.getSemanticType();
    String description = generateDescription(result, semanticType);

    // Determine if this is a built-in (predefined) type or custom type
    Boolean isBuiltIn = null;
    if (semanticType != null) {
      try {
        // First check if this is a known built-in type name
        if (validationService.isBuiltInType(semanticType)) {
          // This is a built-in type name
          // Check if there's a custom override with different patterns
          List<CustomSemanticType> repositoryCustomTypes =
              customSemanticTypeService.getCustomTypesOnly();
          CustomSemanticType repositoryType =
              repositoryCustomTypes.stream()
                  .filter(type -> semanticType.equals(type.getSemanticType()))
                  .findFirst()
                  .orElse(null);

          if (repositoryType != null && validationService.hasCustomPatterns(repositoryType)) {
            // Has custom patterns, so it's a custom override
            isBuiltIn = false;
          } else {
            // No custom patterns, so it's still built-in
            isBuiltIn = true;
          }
        } else {
          // Not a built-in type name, so it's custom
          isBuiltIn = false;
        }
      } catch (Exception e) {
        log.warn("Failed to determine if type '{}' is built-in", semanticType, e);
        // Default to null if we can't determine
      }
    }

    log.debug(
        "Setting isBuiltIn for column '{}' with semantic type '{}': {}",
        columnName,
        semanticType,
        isBuiltIn);

    ColumnClassification.ColumnClassificationBuilder builder =
        ColumnClassification.builder()
            .columnName(columnName)
            .baseType(result.getType() != null ? result.getType().toString() : "UNKNOWN")
            .semanticType(semanticType)
            .typeModifier(result.getTypeModifier())
            .confidence(result.getConfidence())
            .pattern(result.getRegExp())
            .description(description)
            .isBuiltIn(isBuiltIn);

    if (includeStatistics) {
      Statistics stats = buildStatistics(result);
      builder.statistics(stats);

      // Add shape details if available
      if (result.getShapeCount() > 0) {
        List<ShapeDetail> shapeDetails = buildShapeDetails(result, columnName);
        builder.shapeDetails(shapeDetails);
      }
    }

    return builder.build();
  }

  private Statistics buildStatistics(TextAnalysisResult result) {
    return Statistics.builder()
        .sampleCount(result.getSampleCount())
        .nullCount(result.getNullCount())
        .blankCount(result.getBlankCount())
        .distinctCount(result.getDistinctCount())
        .minValue(result.getMinValue())
        .maxValue(result.getMaxValue())
        .minLength(result.getMinLength())
        .maxLength(result.getMaxLength())
        .mean(result.getMean())
        .standardDeviation(result.getStandardDeviation())
        .cardinality(result.getCardinality())
        .uniqueness(result.getUniqueness())
        .build();
  }

  private List<ShapeDetail> buildShapeDetails(TextAnalysisResult result, String columnName) {
    List<ShapeDetail> shapeDetails = new ArrayList<>();

    if (result.getShapeDetails() != null) {
      // Get sample values for this column
      Set<String> sampleValues = columnSampleValues.get(columnName);

      // Get top 5 shapes by frequency
      result.getShapeDetails().entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(5)
          .forEach(
              entry -> {
                ShapeDetail.ShapeDetailBuilder detailBuilder =
                    ShapeDetail.builder().shape(entry.getKey()).count(entry.getValue());

                // Add sample values as examples
                if (sampleValues != null && !sampleValues.isEmpty()) {
                  List<String> examples = new ArrayList<>(sampleValues);
                  detailBuilder.examples(examples);
                }

                shapeDetails.add(detailBuilder.build());
              });
    }

    return shapeDetails;
  }

  private String generateDescription(TextAnalysisResult result) {
    return generateDescription(result, result.getSemanticType());
  }

  private String generateDescription(TextAnalysisResult result, String semanticType) {
    StringBuilder description = new StringBuilder();

    if (semanticType != null) {
      // Get description from the semantic type registry
      String typeDescription = semanticTypeRegistry.getDescription(semanticType);
      description.append(typeDescription);

      // Add additional info from registry if available
      SemanticTypeRegistryService.SemanticTypeInfo typeInfo =
          semanticTypeRegistry.getSemanticTypeInfo(semanticType);
      if (typeInfo != null && typeInfo.getDocumentationUrl() != null) {
        log.debug(
            "Semantic type {} has documentation: {}", semanticType, typeInfo.getDocumentationUrl());
      }
    } else {
      description.append("Base Type: ").append(result.getType());
    }

    if (result.getTypeModifier() != null) {
      description.append(" (").append(result.getTypeModifier()).append(")");
    }

    return description.toString();
  }

  /**
   * Determines which semantic types should be forced for each column based on header patterns. This
   * replicates how the frontend applies custom semantic types with complete control.
   *
   * @param headers Column headers
   * @param customTypes Available custom semantic types
   * @param locale Locale for pattern matching
   * @return Array of semantic type names (same length as headers), null for columns without matches
   */
  private String[] determineSemanticTypesForColumns(
      String[] headers, List<CustomSemanticType> customTypes, Locale locale) {
    if (customTypes == null || customTypes.isEmpty()) {
      return null;
    }

    String[] semanticTypes = new String[headers.length];
    boolean hasAnyMatch = false;

    for (int i = 0; i < headers.length; i++) {
      String header = headers[i];
      String matchedSemanticType = findMatchingSemanticType(header, customTypes, locale);

      if (matchedSemanticType != null) {
        semanticTypes[i] = matchedSemanticType;
        hasAnyMatch = true;
        log.debug("FORCE MODE: Column '{}' -> Semantic Type '{}'", header, matchedSemanticType);
      }
    }

    return hasAnyMatch ? semanticTypes : null;
  }

  /**
   * Finds the best matching custom semantic type for a given column header.
   *
   * @param header Column header name
   * @param customTypes Available custom semantic types
   * @param locale Locale for pattern matching
   * @return Semantic type name if match found, null otherwise
   */
  private String findMatchingSemanticType(
      String header, List<CustomSemanticType> customTypes, Locale locale) {
    // Try exact name match first
    for (CustomSemanticType customType : customTypes) {
      if (customType.getSemanticType().equalsIgnoreCase(header)) {
        return customType.getSemanticType();
      }
    }

    // Try header pattern matching
    for (CustomSemanticType customType : customTypes) {
      if (customType.getValidLocales() != null) {
        for (CustomSemanticType.LocaleConfig localeConfig : customType.getValidLocales()) {
          if ("*".equals(localeConfig.getLocaleTag())
              || locale.toLanguageTag().equals(localeConfig.getLocaleTag())) {
            if (localeConfig.getHeaderRegExps() != null) {
              for (CustomSemanticType.HeaderRegExp headerRegExp : localeConfig.getHeaderRegExps()) {
                if (headerMatchesPattern(header, headerRegExp.getRegExp())) {
                  return customType.getSemanticType();
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * Decide an override semantic type in custom-only eval when FTA returned NONE. Strategy: exact
   * header-name match first; then header regex patterns from custom types.
   */
  private String decideSemanticTypeOverride(String header, List<CustomSemanticType> customTypes) {
    Locale locale = Locale.forLanguageTag("en-US");
    // Require BOTH: header regex match AND values conform to the plugin
    String headerMatched = findMatchingSemanticType(header, customTypes, locale);
    if (headerMatched != null) {
      for (CustomSemanticType t : customTypes) {
        if (headerMatched.equals(t.getSemanticType()) && valuesConformToCustomType(header, t)) {
          return headerMatched;
        }
      }
    }
    return null;
  }

  /**
   * Checks whether collected sample values for a column conform to the given custom plugin. Uses
   * strict full-match checks for regex plugins and membership for list plugins.
   */
  private boolean valuesConformToCustomType(String columnName, CustomSemanticType customType) {
    try {
      // Fetch up to 10 collected sample values for this column
      java.util.Set<String> samples = columnSampleValues.get(columnName);
      if (samples == null || samples.isEmpty()) {
        return false;
      }

      String pluginType = customType.getPluginType();
      if ("regex".equals(pluginType)) {
        // Get primary pattern
        if (customType.getValidLocales() == null || customType.getValidLocales().isEmpty()) {
          return false;
        }
        CustomSemanticType.LocaleConfig loc = customType.getValidLocales().get(0);
        if (loc.getMatchEntries() == null || loc.getMatchEntries().isEmpty()) {
          return false;
        }
        String pattern = loc.getMatchEntries().get(0).getRegExpReturned();
        if (pattern == null || pattern.isEmpty()) {
          return false;
        }
        java.util.regex.Pattern rx = java.util.regex.Pattern.compile("^(?:" + pattern + ")$");
        for (String v : samples) {
          if (v == null || v.isEmpty()) {
            continue;
          }
          if (!rx.matcher(v).matches()) {
            return false;
          }
        }
        return true;
      } else if ("list".equals(pluginType)) {
        if (customType.getContent() == null || customType.getContent().getValues() == null) {
          return false;
        }
        java.util.Set<String> members = new java.util.HashSet<>();
        for (String m : customType.getContent().getValues()) {
          if (m != null) {
            members.add(m.toUpperCase());
          }
        }
        for (String v : samples) {
          if (v == null || v.isEmpty()) {
            continue;
          }
          if (!members.contains(v.toUpperCase())) {
            return false;
          }
        }
        return true;
      }
    } catch (Exception e) {
      log.debug("valuesConformToCustomType error for column '{}': {}", columnName, e.getMessage());
    }
    return false;
  }

  /**
   * Tests if a header matches a regex pattern.
   *
   * @param header Header to test
   * @param pattern Regex pattern
   * @return True if header matches pattern
   */
  private boolean headerMatchesPattern(String header, String pattern) {
    try {
      return header.matches(pattern);
    } catch (Exception e) {
      log.warn("Invalid regex pattern '{}': {}", pattern, e.getMessage());
      return false;
    }
  }
}

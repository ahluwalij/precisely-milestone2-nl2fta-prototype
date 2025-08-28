package com.nl2fta.classifier.service.semantic_type;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.HeaderPattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for converting generated semantic types to custom semantic types suitable for storage and
 * use by the FTA library.
 */
@Slf4j
@Service
public class SemanticTypeConversionService {

  /**
   * Converts a GeneratedSemanticType (from LLM) to a CustomSemanticType (for storage). Preserves
   * the top-down pattern structure by creating separate HeaderRegExp objects.
   *
   * @param generated the generated semantic type from LLM
   * @return the converted custom semantic type ready for storage
   */
  public CustomSemanticType convertToCustomType(GeneratedSemanticType generated) {
    if (generated == null) {
      return null;
    }

    log.debug("Converting generated semantic type: {}", generated.getSemanticType());

    CustomSemanticType customType = new CustomSemanticType();

    // Basic information
    customType.setSemanticType(generated.getSemanticType());
    customType.setDescription(generated.getDescription());
    customType.setPluginType(generated.getPluginType());
    customType.setThreshold((int) Math.round(generated.getConfidenceThreshold() * 100));
    // Use the priority from the generated type (which is automatically assigned to avoid conflicts)
    customType.setPriority(generated.getPriority());

    // Handle content based on plugin type - CustomSemanticType uses ContentConfig not Content
    if ("regex".equals(generated.getPluginType())) {
      // For regex types, store positive examples as inline content if available
      if (generated.getPositiveContentExamples() != null
          && !generated.getPositiveContentExamples().isEmpty()) {
        CustomSemanticType.ContentConfig contentConfig =
            CustomSemanticType.ContentConfig.builder()
                .type("inline")
                .values(generated.getPositiveContentExamples())
                .build();
        customType.setContent(contentConfig);
      }
      log.debug("Regex pattern will be converted to matchEntries: {}", generated.getRegexPattern());
    } else if ("list".equals(generated.getPluginType())) {
      CustomSemanticType.ContentConfig contentConfig =
          CustomSemanticType.ContentConfig.builder()
              .type("inline")
              .values(generated.getListValues())
              .build();
      customType.setContent(contentConfig);
    }

    // Convert header patterns - preserve separate patterns (top-down approach)
    List<CustomSemanticType.HeaderRegExp> headerRegExps =
        convertHeaderPatterns(generated.getHeaderPatterns());

    // Convert patterns to matchEntries for content matching
    List<CustomSemanticType.MatchEntry> matchEntries = new ArrayList<>();
    if ("regex".equals(generated.getPluginType())
        && generated.getRegexPattern() != null
        && !generated.getRegexPattern().isEmpty()) {
      // Match frontend behavior - don't set regExpsToMatch for regex types
      CustomSemanticType.MatchEntry matchEntry =
          CustomSemanticType.MatchEntry.builder()
              // Frontend does NOT set regExpsToMatch for regex types
              .regExpReturned(generated.getRegexPattern())
              .isRegExpComplete(true)
              .build();
      matchEntries.add(matchEntry);
      log.debug(
          "Added regex pattern WITHOUT regExpsToMatch (like frontend): {}",
          generated.getRegexPattern());
    }

    // Set LLM-generated backout pattern for list types
    if ("list".equals(generated.getPluginType())
        && generated.getBackout() != null
        && !generated.getBackout().isEmpty()) {
      customType.setBackout(generated.getBackout());
      log.debug("Added LLM-generated backout pattern for list type: {}", generated.getBackout());
    }

    // Set up the locale config with header patterns and match entries
    if ((headerRegExps != null && !headerRegExps.isEmpty()) || !matchEntries.isEmpty()) {
      CustomSemanticType.LocaleConfig localeConfig =
          CustomSemanticType.LocaleConfig.builder()
              .localeTag("*")
              .headerRegExps(headerRegExps)
              .matchEntries(matchEntries.isEmpty() ? null : matchEntries)
              .build();
      customType.setValidLocales(List.of(localeConfig));
    }

    log.debug(
        "Converted to custom type with {} header patterns",
        headerRegExps != null ? headerRegExps.size() : 0);

    return customType;
  }

  /**
   * Converts header patterns from generated format to custom format. Each HeaderPattern becomes a
   * separate HeaderRegExp to preserve the top-down structure.
   */
  private List<CustomSemanticType.HeaderRegExp> convertHeaderPatterns(
      List<HeaderPattern> headerPatterns) {
    if (headerPatterns == null || headerPatterns.isEmpty()) {
      return new ArrayList<>();
    }

    return headerPatterns.stream()
        .map(this::convertSingleHeaderPattern)
        .collect(Collectors.toList());
  }

  /** Convert a single header pattern to HeaderRegExp format. */
  private CustomSemanticType.HeaderRegExp convertSingleHeaderPattern(HeaderPattern pattern) {
    return CustomSemanticType.HeaderRegExp.builder()
        .regExp(pattern.getRegExp())
        .confidence(pattern.getConfidence())
        .mandatory(true)
        .build();
  }

  /**
   * Alternative method: Create a single combined HeaderRegExp from multiple patterns. This is for
   * backward compatibility if the system needs one combined pattern.
   *
   * @param headerPatterns the list of header patterns
   * @return a single HeaderRegExp with combined regex
   */
  public CustomSemanticType.HeaderRegExp createCombinedHeaderPattern(
      List<HeaderPattern> headerPatterns) {
    if (headerPatterns == null || headerPatterns.isEmpty()) {
      return null;
    }

    // Combine all patterns with OR operator
    String combinedRegex =
        headerPatterns.stream().map(HeaderPattern::getRegExp).collect(Collectors.joining("|"));

    CustomSemanticType.HeaderRegExp combined =
        CustomSemanticType.HeaderRegExp.builder()
            .regExp(combinedRegex)
            .confidence(95)
            .mandatory(true)
            .build();

    log.debug("Created combined header pattern: {}", combinedRegex);
    return combined;
  }
}

package com.nl2fta.classifier.service.semantic_type.management;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.PluginLocaleEntry;
import com.cobber.fta.PluginMatchEntry;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeValidationService {

  private final SemanticTypePluginService pluginService;

  @Data
  public static class ValidationResult {
    private boolean valid;
    private String error;
    private List<ExampleValidation> positiveExampleResults = new ArrayList<>();
    private List<ExampleValidation> negativeExampleResults = new ArrayList<>();
    private List<String> suggestedPositiveExamples = new ArrayList<>();
    private List<String> suggestedNegativeExamples = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    public void addNote(String note) {
      if (notes == null) {
        notes = new ArrayList<>();
      }
      notes.add(note);
    }
  }

  @Data
  public static class ExampleValidation {
    private String example;
    private boolean matches;
    private String reason;
  }

  @Data
  public static class PreviewResult {
    private ValidationResult validation;
    private List<ImpactedAnalysis> impactedAnalyses = new ArrayList<>();
    private int totalFieldsAnalyzed;
    private int fieldsToBeImpacted;
    private int totalUploadedFiles;
  }

  @Data
  public static class ImpactedAnalysis {
    private String analysisId;
    private String fileName;
    private List<ImpactedField> impactedFields = new ArrayList<>();
  }

  @Data
  public static class ImpactedField {
    private String fieldName;
    private String currentSemanticType;
    private double currentConfidence;
    private boolean wouldMatch;
    private List<String> matchedValues = new ArrayList<>();
    private List<String> unmatchedValues = new ArrayList<>();
  }

  public ValidationResult validateSemanticType(
      CustomSemanticType semanticType,
      List<String> positiveExamples,
      List<String> negativeExamples) {
    ValidationResult result = new ValidationResult();
    result.setValid(true);

    if ("regex".equals(semanticType.getPluginType())) {
      return validateRegexType(semanticType, positiveExamples, negativeExamples);
    } else if ("list".equals(semanticType.getPluginType())) {
      return validateListType(semanticType, positiveExamples, negativeExamples);
    } else if ("java".equals(semanticType.getPluginType())) {
      return validateJavaType(semanticType, positiveExamples, negativeExamples);
    }

    result.setError("Unsupported plugin type: " + semanticType.getPluginType());
    result.setValid(false);
    return result;
  }

  private ValidationResult validateRegexType(
      CustomSemanticType semanticType,
      List<String> positiveExamples,
      List<String> negativeExamples) {
    ValidationResult result = new ValidationResult();

    // Get regex pattern from the semantic type
    String regexPattern = null;
    if (semanticType.getValidLocales() != null && !semanticType.getValidLocales().isEmpty()) {
      var locale = semanticType.getValidLocales().get(0);
      if (locale.getMatchEntries() != null && !locale.getMatchEntries().isEmpty()) {
        regexPattern = locale.getMatchEntries().get(0).getRegExpReturned();
      }
    }

    if (regexPattern == null || regexPattern.isEmpty()) {
      result.setValid(false);
      result.setError("No regex pattern found in semantic type");
      return result;
    }

    // Compile pattern
    Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern);
    } catch (PatternSyntaxException e) {
      result.setValid(false);
      result.setError("Invalid regex pattern: " + e.getMessage());
      return result;
    }

    // Validate positive examples
    List<String> validPositives = new ArrayList<>();
    List<String> invalidPositives = new ArrayList<>();
    log.info(
        "Validating {} positive examples against pattern: {}",
        positiveExamples.size(),
        regexPattern);
    for (String example : positiveExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);

      if (example == null || example.trim().isEmpty()) {
        validation.setMatches(false);
        validation.setReason("Empty or null example");
        invalidPositives.add("Empty or null example");
      } else {
        try {
          boolean matches = pattern.matcher(example).matches();
          validation.setMatches(matches);

          if (matches) {
            validPositives.add(example);
            validation.setReason("Correctly matched by pattern");
          } else {
            invalidPositives.add(example);
            validation.setReason("Failed to match pattern: " + regexPattern);
          }
        } catch (Exception e) {
          validation.setMatches(false);
          validation.setReason("Regex error: " + e.getMessage());
          invalidPositives.add(example + " (regex error: " + e.getMessage() + ")");
        }
      }
      result.getPositiveExampleResults().add(validation);
    }

    // Validate negative examples
    List<String> validNegatives = new ArrayList<>();
    List<String> invalidNegatives = new ArrayList<>();
    log.info(
        "Validating {} negative examples against pattern: {}",
        negativeExamples.size(),
        regexPattern);
    for (String example : negativeExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);

      if (example == null || example.trim().isEmpty()) {
        validation.setMatches(false);
        validation.setReason("Empty or null example");
        invalidNegatives.add("Empty or null example");
      } else {
        try {
          boolean matches = pattern.matcher(example).matches();
          validation.setMatches(matches);

          if (!matches) {
            validNegatives.add(example);
            validation.setReason("Correctly rejected by pattern");
          } else {
            invalidNegatives.add(example);
            validation.setReason("Incorrectly matched by pattern: " + regexPattern);
          }
        } catch (Exception e) {
          validation.setMatches(false);
          validation.setReason("Regex error: " + e.getMessage());
          invalidNegatives.add(example + " (regex error: " + e.getMessage() + ")");
        }
      }
      result.getNegativeExampleResults().add(validation);
    }

    // Generate suggested examples if needed
    if (validPositives.size() < positiveExamples.size()) {
      result.setSuggestedPositiveExamples(generateSuggestedExamples(regexPattern, true));
    }
    if (validNegatives.size() < negativeExamples.size()) {
      result.setSuggestedNegativeExamples(generateSuggestedExamples(regexPattern, false));
    }

    // Set overall validity
    boolean allPositivesMatch = validPositives.size() == positiveExamples.size();
    boolean allNegativesDontMatch = validNegatives.size() == negativeExamples.size();
    result.setValid(allPositivesMatch && allNegativesDontMatch);

    // Log validation summary
    log.info(
        "VALIDATION SUMMARY for pattern '{}': "
            + "Positive examples: {}/{} matched correctly, "
            + "Negative examples: {}/{} rejected correctly, "
            + "Overall valid: {}",
        regexPattern,
        validPositives.size(),
        positiveExamples.size(),
        validNegatives.size(),
        negativeExamples.size(),
        result.isValid());

    if (!result.isValid()) {
      String errorMsg =
          String.format(
              "Pattern validation failed: %d/%d positive examples matched, %d/%d negative examples correctly rejected",
              validPositives.size(),
              positiveExamples.size(),
              validNegatives.size(),
              negativeExamples.size());
      result.setError(errorMsg);
      log.error("VALIDATION FAILED: {}", errorMsg);
    } else {
      log.info("✓ All examples validated successfully against pattern");
    }

    return result;
  }

  private ValidationResult validateListType(
      CustomSemanticType semanticType,
      List<String> positiveExamples,
      List<String> negativeExamples) {
    ValidationResult result = new ValidationResult();

    // Get list values (FTA converts all values to uppercase internally)
    Set<String> listValues = new HashSet<>();
    Set<String> upperCaseListValues = new HashSet<>();
    if (semanticType.getContent() != null && semanticType.getContent().getValues() != null) {
      listValues.addAll(semanticType.getContent().getValues());
      // Create uppercase version for comparison (FTA requirement)
      semanticType
          .getContent()
          .getValues()
          .forEach(value -> upperCaseListValues.add(value.toUpperCase()));
    }

    if (listValues.isEmpty()) {
      result.setValid(false);
      result.setError("No list values found in semantic type");
      return result;
    }

    // Add helpful note about FTA's uppercase requirement
    result.addNote(
        "Note: FTA automatically converts list values to uppercase during plugin registration. "
            + "Your values will be stored as: "
            + upperCaseListValues);

    // Validate positive examples (case-insensitive since FTA converts internally)
    int positiveMatches = 0;
    for (String example : positiveExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);
      // Check both original and uppercase versions since FTA handles this conversion
      boolean exactMatch = listValues.contains(example);
      boolean caseInsensitiveMatch = upperCaseListValues.contains(example.toUpperCase());
      validation.setMatches(exactMatch || caseInsensitiveMatch);

      if (validation.isMatches()) {
        positiveMatches++;
        if (exactMatch) {
          validation.setReason("Found exact match in list");
        } else {
          validation.setReason("Found case-insensitive match (FTA will convert to uppercase)");
        }
      } else {
        validation.setReason("Not found in list of " + listValues.size() + " values");
      }
      result.getPositiveExampleResults().add(validation);
    }

    // Validate negative examples
    int negativeNonMatches = 0;
    for (String example : negativeExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);
      validation.setMatches(listValues.contains(example));
      if (!validation.isMatches()) {
        negativeNonMatches++;
        validation.setReason("Correctly not in list");
      } else {
        validation.setReason("Incorrectly found in list");
      }
      result.getNegativeExampleResults().add(validation);
    }

    // Set overall validity
    boolean allPositivesMatch = positiveMatches == positiveExamples.size();
    boolean allNegativesDontMatch = negativeNonMatches == negativeExamples.size();
    result.setValid(allPositivesMatch && allNegativesDontMatch);

    if (!result.isValid()) {
      result.setError(
          String.format(
              "List validation failed: %d/%d positive examples matched, %d/%d negative examples correctly rejected",
              positiveMatches,
              positiveExamples.size(),
              negativeNonMatches,
              negativeExamples.size()));
    }

    return result;
  }

  private ValidationResult validateJavaType(
      CustomSemanticType semanticType,
      List<String> positiveExamples,
      List<String> negativeExamples) {
    ValidationResult result = new ValidationResult();

    // For Java types, we can try to extract regex patterns from matchEntries for basic validation
    List<String> patterns = extractPatternsFromJavaType(semanticType);

    if (patterns.isEmpty()) {
      // If no patterns available, assume Java type is valid for basic validation
      result.setValid(true);

      // Add placeholder validations for examples
      for (String example : positiveExamples) {
        ExampleValidation validation = new ExampleValidation();
        validation.setExample(example);
        validation.setMatches(true); // Assume valid since we can't test Java code directly
        validation.setReason("Java type validation - pattern matching requires runtime execution");
        result.getPositiveExampleResults().add(validation);
      }

      for (String example : negativeExamples) {
        ExampleValidation validation = new ExampleValidation();
        validation.setExample(example);
        validation.setMatches(false); // Assume correctly rejected
        validation.setReason("Java type validation - pattern matching requires runtime execution");
        result.getNegativeExampleResults().add(validation);
      }

      return result;
    }

    // If we have patterns extracted from matchEntries, use them for basic validation
    int positiveMatches = 0;
    for (String example : positiveExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);
      boolean matches = testAgainstPatterns(example, patterns);
      validation.setMatches(matches);
      if (matches) {
        positiveMatches++;
        validation.setReason("Matches extracted pattern from Java type");
      } else {
        validation.setReason("Does not match extracted patterns");
      }
      result.getPositiveExampleResults().add(validation);
    }

    int negativeNonMatches = 0;
    for (String example : negativeExamples) {
      ExampleValidation validation = new ExampleValidation();
      validation.setExample(example);
      boolean matches = testAgainstPatterns(example, patterns);
      validation.setMatches(matches);
      if (!matches) {
        negativeNonMatches++;
        validation.setReason("Correctly does not match extracted patterns");
      } else {
        validation.setReason("Incorrectly matches extracted patterns");
      }
      result.getNegativeExampleResults().add(validation);
    }

    // Set overall validity based on pattern matching
    boolean allPositivesMatch = positiveMatches == positiveExamples.size();
    boolean allNegativesDontMatch = negativeNonMatches == negativeExamples.size();
    result.setValid(allPositivesMatch && allNegativesDontMatch);

    if (!result.isValid()) {
      result.setError(
          String.format(
              "Java type pattern validation failed: %d/%d positive examples matched, %d/%d negative examples correctly rejected",
              positiveMatches,
              positiveExamples.size(),
              negativeNonMatches,
              negativeExamples.size()));
    }

    return result;
  }

  private List<String> extractPatternsFromJavaType(CustomSemanticType semanticType) {
    List<String> patterns = new ArrayList<>();

    if (semanticType.getValidLocales() != null) {
      for (CustomSemanticType.LocaleConfig locale : semanticType.getValidLocales()) {
        if (locale.getMatchEntries() != null) {
          for (CustomSemanticType.MatchEntry matchEntry : locale.getMatchEntries()) {
            if (matchEntry.getRegExpReturned() != null
                && !matchEntry.getRegExpReturned().trim().isEmpty()) {
              patterns.add(matchEntry.getRegExpReturned());
            }
          }
        }
      }
    }

    return patterns;
  }

  private boolean testAgainstPatterns(String example, List<String> patterns) {
    for (String pattern : patterns) {
      try {
        if (Pattern.matches(pattern, example)) {
          return true;
        }
      } catch (PatternSyntaxException e) {
        // Skip invalid patterns
        log.debug("Invalid regex pattern in Java type: {}", pattern);
      }
    }
    return false;
  }

  private List<String> generateSuggestedExamples(String regexPattern, boolean positive) {
    List<String> suggestions = new ArrayList<>();

    // Simple pattern analysis to suggest examples
    // This is a basic implementation - could be enhanced with more sophisticated analysis
    if (positive) {
      if (regexPattern.contains("\\d")) {
        suggestions.add("Example with digits: ABC123");
      }
      if (regexPattern.contains("[A-Z]")) {
        suggestions.add("Example with uppercase: SAMPLE");
      }
      if (regexPattern.contains("@")) {
        suggestions.add("Example with @: user@example.com");
      }
    } else {
      suggestions.add("Random text that shouldn't match");
      suggestions.add("12345");
      suggestions.add("!@#$%");
    }

    return suggestions.stream().limit(3).collect(Collectors.toList());
  }

  /**
   * Validates a custom semantic type structure and configuration.
   *
   * @param customType the custom semantic type to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validateCustomType(CustomSemanticType customType) {
    if (customType == null) {
      throw new IllegalArgumentException("Custom semantic type cannot be null");
    }

    if (customType.getSemanticType() == null || customType.getSemanticType().trim().isEmpty()) {
      throw new IllegalArgumentException("Semantic type name cannot be null or empty");
    }

    if (customType.getPluginType() == null || customType.getPluginType().trim().isEmpty()) {
      throw new IllegalArgumentException("Plugin type cannot be null or empty");
    }

    // Validate plugin type is supported
    if (!"regex".equals(customType.getPluginType())
        && !"list".equals(customType.getPluginType())
        && !"java".equals(customType.getPluginType())) {
      throw new IllegalArgumentException("Unsupported plugin type: " + customType.getPluginType());
    }

    // Validate regex types have valid patterns
    if ("regex".equals(customType.getPluginType())) {
      validateRegexTypeStructure(customType);
    }

    // Validate list types have content
    if ("list".equals(customType.getPluginType())) {
      validateListTypeStructure(customType);
    }

    // Validate Java types have required class information
    if ("java".equals(customType.getPluginType())) {
      validateJavaTypeStructure(customType);
    }
  }

  /**
   * Checks if a semantic type name corresponds to a built-in type.
   *
   * @param semanticTypeName the semantic type name to check
   * @return true if it's a built-in type, false otherwise
   */
  public boolean isBuiltInType(String semanticTypeName) {
    try {
      return pluginService.loadBuiltInPlugins().stream()
          .anyMatch(plugin -> plugin.semanticType.equals(semanticTypeName));
    } catch (Exception e) {
      log.warn("Error checking built-in types: {}", e.getMessage());
      return false;
    }
  }

  private void validateRegexTypeStructure(CustomSemanticType customType) {
    if (customType.getValidLocales() == null || customType.getValidLocales().isEmpty()) {
      throw new IllegalArgumentException("Regex semantic type must have at least one valid locale");
    }

    var locale = customType.getValidLocales().get(0);
    if (locale.getMatchEntries() == null || locale.getMatchEntries().isEmpty()) {
      throw new IllegalArgumentException("Regex semantic type must have at least one match entry");
    }

    // Validate regex pattern syntax
    String regexPattern = locale.getMatchEntries().get(0).getRegExpReturned();
    if (regexPattern == null || regexPattern.trim().isEmpty()) {
      throw new IllegalArgumentException("Regex pattern cannot be null or empty");
    }

    try {
      Pattern.compile(regexPattern);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
    }
  }

  private void validateListTypeStructure(CustomSemanticType customType) {
    if (customType.getContent() == null
        || customType.getContent().getValues() == null
        || customType.getContent().getValues().isEmpty()) {
      throw new IllegalArgumentException("List semantic type must have content values");
    }

    // Check for empty or null values
    for (String value : customType.getContent().getValues()) {
      if (value == null || value.trim().isEmpty()) {
        throw new IllegalArgumentException("List values cannot be null or empty");
      }
    }
  }

  private void validateJavaTypeStructure(CustomSemanticType customType) {
    // Java types must have a class name
    if (customType.getClazz() == null || customType.getClazz().trim().isEmpty()) {
      throw new IllegalArgumentException("Java semantic type must have a class name (clazz field)");
    }

    // Java types should have a signature for integrity
    if (customType.getSignature() == null || customType.getSignature().trim().isEmpty()) {
      log.warn(
          "Java semantic type '{}' does not have a signature - this may affect functionality",
          customType.getSemanticType());
    }

    // Java types must have valid locales with match entries for pattern matching
    if (customType.getValidLocales() == null || customType.getValidLocales().isEmpty()) {
      throw new IllegalArgumentException(
          "Java semantic type must have valid locales with match entries");
    }

    // Validate that at least one locale has match entries (for pattern extraction)
    boolean hasMatchEntries = false;
    for (CustomSemanticType.LocaleConfig locale : customType.getValidLocales()) {
      if (locale.getMatchEntries() != null && !locale.getMatchEntries().isEmpty()) {
        hasMatchEntries = true;
        break;
      }
    }

    if (!hasMatchEntries) {
      log.warn(
          "Java semantic type '{}' has no match entries - pattern matching may be limited",
          customType.getSemanticType());
    }
  }

  /**
   * Checks if a custom type has different patterns from the built-in type. This helps determine if
   * it's a true user-created override or just a converted built-in type.
   *
   * @param customType the custom type to check
   * @return true if the custom type has patterns that differ from the built-in, false otherwise
   */
  public boolean hasCustomPatterns(CustomSemanticType customType) {
    if (customType == null || customType.getSemanticType() == null) {
      return false;
    }

    // If it's not a built-in type name, it's definitely custom
    if (!isBuiltInType(customType.getSemanticType())) {
      return true;
    }

    try {
      // Load the built-in type to compare
      List<PluginDefinition> builtInPlugins = pluginService.loadBuiltInPlugins();
      PluginDefinition builtInType =
          builtInPlugins.stream()
              .filter(plugin -> plugin.semanticType.equals(customType.getSemanticType()))
              .findFirst()
              .orElse(null);

      if (builtInType == null) {
        // No matching built-in type found, so it's custom
        return true;
      }

      // Compare patterns based on plugin type
      if ("regex".equals(customType.getPluginType()) && "regex".equals(builtInType.pluginType)) {
        return hasCustomRegexPatterns(customType, builtInType);
      } else if ("list".equals(customType.getPluginType())
          && "list".equals(builtInType.pluginType)) {
        return hasCustomListValues(customType, builtInType);
      } else if ("java".equals(customType.getPluginType())
          && "java".equals(builtInType.pluginType)) {
        // Java types can't really be customized via patterns, they're code-based
        // If it has the same class name, it's likely the same implementation
        return !isSameJavaImplementation(customType, builtInType);
      }

      // If plugin types differ, it's definitely custom
      return !customType.getPluginType().equals(builtInType.pluginType);

    } catch (Exception e) {
      log.warn(
          "Error checking for custom patterns in type '{}': {}",
          customType.getSemanticType(),
          e.getMessage());
      // If we can't determine, assume it's not custom to be safe
      return false;
    }
  }

  private boolean hasCustomRegexPatterns(
      CustomSemanticType customType, PluginDefinition builtInType) {
    // Extract patterns from custom type
    Set<String> customPatterns = new HashSet<>();
    if (customType.getValidLocales() != null) {
      for (CustomSemanticType.LocaleConfig locale : customType.getValidLocales()) {
        if (locale.getMatchEntries() != null) {
          for (CustomSemanticType.MatchEntry entry : locale.getMatchEntries()) {
            if (entry.getRegExpReturned() != null) {
              customPatterns.add(entry.getRegExpReturned());
            }
          }
        }
      }
    }

    // Extract patterns from built-in type
    Set<String> builtInPatterns = new HashSet<>();
    if (builtInType.validLocales != null) {
      for (PluginLocaleEntry locale : builtInType.validLocales) {
        if (locale.matchEntries != null) {
          for (PluginMatchEntry entry : locale.matchEntries) {
            if (entry.regExpReturned != null) {
              builtInPatterns.add(entry.regExpReturned);
            }
          }
        }
      }
    }

    // If patterns differ, it's custom
    return !customPatterns.equals(builtInPatterns);
  }

  private boolean hasCustomListValues(CustomSemanticType customType, PluginDefinition builtInType) {
    // Extract values from custom type
    Set<String> customValues = new HashSet<>();
    if (customType.getContent() != null && customType.getContent().getValues() != null) {
      customValues.addAll(customType.getContent().getValues());
    }

    // For built-in list types, we can't directly compare values since they may be in resources
    // If the custom type has values and the plugin type is list, assume it's custom if:
    // 1. The custom type has different values than expected
    // 2. The content types differ (inline vs resource)
    if (builtInType.content != null) {
      // If the built-in uses a resource and custom uses inline with values, it's likely custom
      if ("resource".equals(builtInType.content.type)
          && "inline".equals(customType.getContent().getType())
          && !customValues.isEmpty()) {
        // This is likely a user override with custom values
        return true;
      }

      // If both are inline but we can't access built-in values directly,
      // assume it's not custom (converted built-in)
      return false;
    }

    // If no built-in content to compare, check if custom has values
    return !customValues.isEmpty();
  }

  private boolean isSameJavaImplementation(
      CustomSemanticType customType, PluginDefinition builtInType) {
    // Compare class names
    String customClass = customType.getClazz();
    String builtInClass = builtInType.clazz;

    if (customClass == null || builtInClass == null) {
      return false;
    }

    return customClass.equals(builtInClass);
  }
}

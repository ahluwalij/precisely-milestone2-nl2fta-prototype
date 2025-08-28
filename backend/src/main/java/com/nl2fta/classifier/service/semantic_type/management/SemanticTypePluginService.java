package com.nl2fta.classifier.service.semantic_type.management;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Service;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.PluginDocumentationEntry;
import com.cobber.fta.PluginLocaleEntry;
import com.cobber.fta.PluginMatchEntry;
import com.cobber.fta.TextAnalyzer;
import com.cobber.fta.core.HeaderEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Use standard Java regex compilation for preflight; FTA uses brics internally

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypePluginService {

  private final ObjectMapper objectMapper;

  public List<PluginDefinition> loadBuiltInPlugins() throws IOException {
    try (InputStream is = PluginDefinition.class.getResourceAsStream("/reference/plugins.json");
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      return objectMapper.readValue(reader, new TypeReference<List<PluginDefinition>>() {});
    } catch (Exception e) {
      throw new IOException("Failed to load built-in plugins", e);
    }
  }

  public boolean hasEnglishOrUniversalLocale(PluginDefinition plugin) {
    if (plugin.validLocales == null || plugin.validLocales.length == 0) {
      return true;
    }

    for (PluginLocaleEntry localeEntry : plugin.validLocales) {
      if (localeEntry.localeTag == null) {
        continue;
      }

      String[] localeTags = localeEntry.localeTag.split(",");
      for (String tag : localeTags) {
        tag = tag.trim();
        if ("*".equals(tag) || tag.startsWith("en")) {
          return true;
        }
      }
    }

    return false;
  }

  public CustomSemanticType convertPluginDefinitionToCustomType(PluginDefinition plugin) {
    CustomSemanticType customType = new CustomSemanticType();
    customType.setSemanticType(plugin.semanticType);
    customType.setDescription(plugin.description);
    customType.setPluginType(plugin.pluginType);
    customType.setThreshold(plugin.threshold);
    customType.setBaseType(plugin.baseType != null ? plugin.baseType.toString() : "STRING");
    customType.setMinimum(plugin.minimum);
    customType.setMaximum(plugin.maximum);
    customType.setMinSamples(plugin.minSamples);
    customType.setMinMaxPresent(plugin.minMaxPresent);
    customType.setLocaleSensitive(plugin.localeSensitive);
    // Set priority for converted built-in types: original priority + 2000
    // This ensures built-in types maintain their relative priorities but are above the 2000
    // threshold
    customType.setPriority(plugin.priority + 2000);

    customType.setSignature(plugin.signature);
    customType.setClazz(plugin.clazz);
    customType.setPluginOptions(plugin.pluginOptions);
    customType.setBackout(plugin.backout);

    // Mark as built-in type since this is converted from FTA built-in plugin
    customType.setIsBuiltIn(true);
    // Built-in types should have createdAt = 0 to indicate predefined
    customType.setCreatedAt(0L);

    // If this is a finite list type without a backout, add a default one
    // FTA requires finite list types to have a backout pattern
    if ("list".equals(plugin.pluginType)
        && plugin.content != null
        && plugin.content.type != null
        && "inline".equals(plugin.content.type)
        && (customType.getBackout() == null || customType.getBackout().isEmpty())) {
      customType.setBackout(".*"); // Default backout pattern
      log.debug("Added default backout pattern for finite list type: {}", plugin.semanticType);
    }

    convertValidLocales(plugin, customType);
    convertDocumentation(plugin, customType);
    convertContent(plugin, customType);
    convertLists(plugin, customType);

    return customType;
  }

  public void registerCustomTypes(
      TextAnalyzer analyzer, Map<String, CustomSemanticType> customTypes) {
    if (customTypes.isEmpty()) {
      return;
    }

    try {
      List<Map<String, Object>> pluginDefinitions = new ArrayList<>();

      for (CustomSemanticType customType : customTypes.values()) {
        if (!isCustomTypeStructurallyValid(customType)) {
          log.warn(
              "Skipping invalid custom type '{}' (structural validation failed)",
              customType.getSemanticType());
          continue;
        }
        if ("regex".equals(customType.getPluginType()) && !isPrimaryRegexValid(customType)) {
          log.warn(
              "Skipping custom type '{}' due to invalid regex pattern",
              customType.getSemanticType());
          continue;
        }
        Map<String, Object> pluginDef = convertToFTAFormat(customType);
        pluginDefinitions.add(pluginDef);
      }

      log.info(
          "Attempting registration of {} custom types (per-plugin isolation)",
          pluginDefinitions.size());
      int success = 0;
      int failed = 0;
      for (Map<String, Object> pluginDef : pluginDefinitions) {
        try {
          String singleJson = objectMapper.writeValueAsString(java.util.List.of(pluginDef));
          analyzer
              .getPlugins()
              .registerPlugins(
                  new StringReader(singleJson),
                  (String) pluginDef.getOrDefault("semanticType", "custom_types"),
                  analyzer.getConfig());
          success++;
        } catch (Exception ex) {
          failed++;
          log.warn(
              "Failed to register plugin '{}': {}", pluginDef.get("semanticType"), ex.getMessage());
        }
      }
      log.info("Successfully registered {} custom types ({} failed)", success, failed);

    } catch (Exception e) {
      log.error("Failed to register custom types", e);
      throw new RuntimeException("Failed to register custom semantic types", e);
    }
  }

  private boolean isCustomTypeStructurallyValid(CustomSemanticType type) {
    if (type == null || type.getSemanticType() == null || type.getSemanticType().trim().isEmpty()) {
      return false;
    }
    String pluginType = type.getPluginType();
    if (!"regex".equals(pluginType) && !"list".equals(pluginType) && !"java".equals(pluginType)) {
      return false;
    }
    if ("list".equals(pluginType)) {
      return type.getContent() != null
          && type.getContent().getValues() != null
          && !type.getContent().getValues().isEmpty();
    }
    if ("regex".equals(pluginType)) {
      return type.getValidLocales() != null
          && !type.getValidLocales().isEmpty()
          && type.getValidLocales().get(0).getMatchEntries() != null
          && !type.getValidLocales().get(0).getMatchEntries().isEmpty();
    }
    return true;
  }

  private boolean isPrimaryRegexValid(CustomSemanticType type) {
    try {
      String pattern = type.getValidLocales().get(0).getMatchEntries().get(0).getRegExpReturned();
      if (pattern == null || pattern.trim().isEmpty()) {
        return false;
      }
      Pattern.compile(pattern);
      // Additional cheap sanity checks to reduce brics failures
      return !containsUnbalancedQuotes(pattern) && !containsIllegalClassRanges(pattern);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  private boolean containsUnbalancedQuotes(String s) {
    long quotes = s.chars().filter(c -> c == '"').count();
    return (quotes % 2) != 0;
  }

  private boolean containsIllegalClassRanges(String s) {
    // Heuristic: detect common mistakes like unescaped '-' at class edge
    return s.contains("[\n") || s.contains("[\r");
  }

  private Map<String, Object> convertToFTAFormat(CustomSemanticType customType) {
    Map<String, Object> plugin = new HashMap<>();

    plugin.put("semanticType", customType.getSemanticType());
    plugin.put("description", customType.getDescription());
    plugin.put("pluginType", customType.getPluginType());
    plugin.put("threshold", customType.getThreshold());
    plugin.put("baseType", normalizeBaseType(customType.getBaseType()));

    // Add signature for list types (FTA may require this)
    if ("list".equals(customType.getPluginType())) {
      plugin.put("signature", generateSignatureForListType(customType.getSemanticType()));
    }

    if (customType.getPriority() != null) {
      plugin.put("priority", customType.getPriority());
    }

    if (customType.getMinSamples() != null) {
      plugin.put("minSamples", customType.getMinSamples());
    }

    convertValidLocalesToMap(customType, plugin);
    convertDocumentationToMap(customType, plugin);
    convertContentToMap(customType, plugin);

    // Add backout if present (required for finite list types)
    if (customType.getBackout() != null && !customType.getBackout().isEmpty()) {
      plugin.put("backout", customType.getBackout());
    } else if ("list".equals(customType.getPluginType())
        && customType.getContent() != null
        && customType.getContent().getType() != null
        && "inline".equals(customType.getContent().getType())) {
      // FTA requires finite list types to have a backout pattern - use fallback if none provided
      plugin.put("backout", ".*");
      log.debug(
          "Added fallback backout pattern for finite list type: {}", customType.getSemanticType());
    }

    if ("java".equals(customType.getPluginType())) {
      if (customType.getClazz() != null) {
        plugin.put("clazz", customType.getClazz());
      }
      if (customType.getSignature() != null) {
        plugin.put("signature", customType.getSignature());
      }
    }

    return plugin;
  }

  /** Normalizes baseType to match FTAType enum values */
  private String normalizeBaseType(String baseType) {
    if (baseType == null) {
      return "STRING";
    }

    // Convert to uppercase to match FTAType enum values
    String normalized = baseType.toUpperCase();

    // Map common variations to valid enum values
    switch (normalized) {
      case "STRING":
      case "ZONEDDATETIME":
      case "LOCALDATETIME":
      case "LOCALTIME":
      case "LOCALDATE":
      case "LONG":
      case "OFFSETDATETIME":
      case "DOUBLE":
      case "BOOLEAN":
        return normalized;
      default:
        // For any unrecognized type, default to STRING
        log.debug("Unknown baseType '{}', defaulting to STRING", baseType);
        return "STRING";
    }
  }

  private void convertValidLocales(PluginDefinition plugin, CustomSemanticType customType) {
    if (plugin.validLocales != null) {
      List<CustomSemanticType.LocaleConfig> localeConfigs = new ArrayList<>();
      for (PluginLocaleEntry localeEntry : plugin.validLocales) {
        CustomSemanticType.LocaleConfig localeConfig = new CustomSemanticType.LocaleConfig();
        localeConfig.setLocaleTag(localeEntry.localeTag);

        if (localeEntry.headerRegExps != null) {
          List<CustomSemanticType.HeaderRegExp> headerRegExps = new ArrayList<>();
          for (HeaderEntry headerEntry : localeEntry.headerRegExps) {
            CustomSemanticType.HeaderRegExp headerRegExp = new CustomSemanticType.HeaderRegExp();
            headerRegExp.setRegExp(headerEntry.regExp);
            headerRegExp.setConfidence(headerEntry.confidence);
            headerRegExp.setMandatory(headerEntry.mandatory);
            headerRegExps.add(headerRegExp);
          }
          localeConfig.setHeaderRegExps(headerRegExps);
        }

        if (localeEntry.matchEntries != null) {
          List<CustomSemanticType.MatchEntry> matchEntries = new ArrayList<>();
          for (PluginMatchEntry me : localeEntry.matchEntries) {
            CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
            matchEntry.setRegExpReturned(me.getRegExpReturned());
            matchEntry.setIsRegExpComplete(me.isRegExpComplete());
            matchEntries.add(matchEntry);
          }
          localeConfig.setMatchEntries(matchEntries);
        }

        localeConfigs.add(localeConfig);
      }
      customType.setValidLocales(localeConfigs);
    }
  }

  private void convertDocumentation(PluginDefinition plugin, CustomSemanticType customType) {
    if (plugin.documentation != null) {
      List<CustomSemanticType.Documentation> docs = new ArrayList<>();
      for (PluginDocumentationEntry docEntry : plugin.documentation) {
        CustomSemanticType.Documentation doc = new CustomSemanticType.Documentation();
        doc.setSource(docEntry.source);
        doc.setReference(docEntry.reference);
        docs.add(doc);
      }
      customType.setDocumentation(docs);
    }
  }

  private void convertContent(PluginDefinition plugin, CustomSemanticType customType) {
    if (plugin.content != null) {
      CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();

      if ("resource".equals(plugin.content.type)) {
        // For resource-based content, we need to load the actual values from the CSV file
        // Convert to inline type with actual values for database storage
        content.setType("inline");
        content.setReference(plugin.content.reference);

        // Load values from the resource file
        List<String> values = loadResourceContent(plugin.content.reference);
        if (values != null && !values.isEmpty()) {
          content.setValues(values);
        } else {
          log.warn(
              "No values loaded from resource {} for type {}",
              plugin.content.reference,
              plugin.semanticType);
          // Keep as resource type if we can't load the values
          content.setType(plugin.content.type);
        }
      } else {
        // For inline content, copy as-is
        content.setType(plugin.content.type);
        content.setReference(plugin.content.reference);
      }

      customType.setContent(content);
    }
  }

  private List<String> loadResourceContent(String resourcePath) {
    if (resourcePath == null) {
      return null;
    }

    try {
      // Load the resource from the classpath (FTA resources)
      String ftaResourcePath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
      InputStream inputStream = getClass().getResourceAsStream(ftaResourcePath);

      if (inputStream == null) {
        log.warn("Could not find resource: {}", ftaResourcePath);
        return null;
      }

      List<String> values = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (!line.isEmpty() && !line.startsWith("#")) {
            // For CSV files, take the first column
            String[] parts = line.split(",");
            if (parts.length > 0) {
              String value = parts[0].trim();
              // Remove quotes if present
              if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
              }
              if (!value.isEmpty()) {
                values.add(value);
              }
            }
          }
        }
      }

      return values;

    } catch (Exception e) {
      log.error("Error loading resource content from {}: {}", resourcePath, e.getMessage());
      return null;
    }
  }

  private void convertLists(PluginDefinition plugin, CustomSemanticType customType) {
    if (plugin.invalidList != null) {
      customType.setInvalidList(new ArrayList<>(plugin.invalidList));
    }
    if (plugin.ignoreList != null) {
      customType.setIgnoreList(new ArrayList<>(plugin.ignoreList));
    }
  }

  private void convertValidLocalesToMap(CustomSemanticType customType, Map<String, Object> plugin) {
    if (customType.getValidLocales() != null) {
      List<Map<String, Object>> locales = new ArrayList<>();
      for (CustomSemanticType.LocaleConfig localeConfig : customType.getValidLocales()) {
        Map<String, Object> locale = new HashMap<>();
        locale.put("localeTag", localeConfig.getLocaleTag());

        if (localeConfig.getHeaderRegExps() != null) {
          List<Map<String, Object>> headerRegExps = new ArrayList<>();
          for (CustomSemanticType.HeaderRegExp headerRegExp : localeConfig.getHeaderRegExps()) {
            Map<String, Object> header = new HashMap<>();
            header.put("regExp", headerRegExp.getRegExp());
            header.put("confidence", headerRegExp.getConfidence());
            if (headerRegExp.getMandatory() != null) {
              header.put("mandatory", headerRegExp.getMandatory());
            }
            headerRegExps.add(header);
          }
          locale.put("headerRegExps", headerRegExps);
        }

        // Auto-generate matchEntries for list types from list values
        List<Map<String, Object>> matchEntries = new ArrayList<>();
        if (localeConfig.getMatchEntries() != null) {
          // Use existing matchEntries if present
          for (CustomSemanticType.MatchEntry matchEntry : localeConfig.getMatchEntries()) {
            Map<String, Object> match = new HashMap<>();
            if (matchEntry.getRegExpsToMatch() != null
                && !matchEntry.getRegExpsToMatch().isEmpty()) {
              match.put("regExpsToMatch", matchEntry.getRegExpsToMatch().toArray(new String[0]));
            }
            match.put("regExpReturned", matchEntry.getRegExpReturned());
            match.put("isRegExpComplete", matchEntry.getIsRegExpComplete());
            matchEntries.add(match);
          }
        }

        if (!matchEntries.isEmpty()) {
          locale.put("matchEntries", matchEntries);
        }

        locales.add(locale);
      }
      plugin.put("validLocales", locales);
    }
  }

  private void convertDocumentationToMap(
      CustomSemanticType customType, Map<String, Object> plugin) {
    if (customType.getDocumentation() != null) {
      List<Map<String, String>> docs = new ArrayList<>();
      for (CustomSemanticType.Documentation doc : customType.getDocumentation()) {
        Map<String, String> docMap = new HashMap<>();
        docMap.put("source", doc.getSource());
        docMap.put("reference", doc.getReference());
        docs.add(docMap);
      }
      plugin.put("documentation", docs);
    }
  }

  private void convertContentToMap(CustomSemanticType customType, Map<String, Object> plugin) {
    if (customType.getContent() != null) {
      Map<String, Object> content = new HashMap<>();
      content.put("type", customType.getContent().getType());

      if (customType.getContent().getReference() != null) {
        content.put("reference", customType.getContent().getReference());
      }

      if (customType.getContent().getValues() != null) {
        // FTA expects "members" not "values" for inline list content
        // CRITICAL: FTA requires all list members to be UPPERCASE for English locales
        List<String> members =
            customType.getContent().getValues().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toList());
        content.put("members", members);
      }

      plugin.put("content", content);
    }
  }

  /**
   * Generates a case-insensitive regex pattern from list values. Creates a pattern like
   * "(?i)(VALUE1|VALUE2|VALUE3)" Note: FTA requires list members to be UPPERCASE, so we convert
   * them here.
   */
  private String generateRegexFromListValues(List<String> values) {
    if (values == null || values.isEmpty()) {
      return ".*";
    }

    // CRITICAL: Convert to uppercase and escape regex special characters
    // FTA requires list members to be UPPERCASE for English locales
    String escapedValues =
        values.stream()
            .map(String::toUpperCase)
            .map(this::escapeRegexSpecialChars)
            .collect(java.util.stream.Collectors.joining("|"));

    return "(?i)(" + escapedValues + ")";
  }

  /**
   * Generates a signature for list types based on the semantic type name. This mimics the signature
   * format used by built-in list types.
   */
  private String generateSignatureForListType(String semanticType) {
    // Generate a simple hash-like signature based on the semantic type name
    // Built-in types use SHA-based signatures, but for custom types we'll use a simple approach
    return "custom_" + Math.abs(semanticType.hashCode()) + "=" + semanticType.length();
  }

  /** Escapes regex special characters in a string. */
  private String escapeRegexSpecialChars(String input) {
    if (input == null) {
      return "";
    }
    // Escape common regex special characters
    return input
        .replace("\\", "\\\\")
        .replace(".", "\\.")
        .replace("^", "\\^")
        .replace("$", "\\$")
        .replace("*", "\\*")
        .replace("+", "\\+")
        .replace("?", "\\?")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("|", "\\|");
  }
}

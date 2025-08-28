package com.nl2fta.classifier.service.semantic_type.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.TextAnalyzer;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.storage.ICustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main service for managing custom semantic types. Orchestrates between repository, validation,
 * plugin services, and vector indexing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomSemanticTypeService {

  private final ICustomSemanticTypeRepository repository;
  private final SemanticTypePluginService pluginService;
  private final SemanticTypeValidationService validationService;

  @Autowired @Lazy // Use lazy loading to avoid circular dependency
  private VectorIndexInitializationService vectorIndexService;

  /**
   * Adds a new custom semantic type.
   *
   * @param customType the custom semantic type to add
   * @return the added custom semantic type
   * @throws IllegalArgumentException if validation fails or type already exists
   */
  public CustomSemanticType addCustomType(CustomSemanticType customType) {
    validationService.validateCustomType(customType);

    if (repository.existsBySemanticType(customType.getSemanticType())) {
      throw new IllegalArgumentException(
          String.format(
              "Type '%s' already exists. Use a different name.", customType.getSemanticType()));
    }

    // Ensure priority is set properly
    // FTA requires all external plugins to have priority >= 2000 (PRIORITY_EXTERNAL)
    // Built-in converted types should keep their original + 2000 priority
    // User-created types must have priority >= 2000 but we preserve their specific values
    if (customType.getPriority() == null) {
      customType.setPriority(2500);
    } else if (!Boolean.TRUE.equals(customType.getIsBuiltIn()) && customType.getPriority() < 2000) {
      // FTA enforces minimum priority of 2000 for external plugins
      // Log warning but allow the user's priority to be preserved for debugging
      log.warn(
          "Priority {} is below FTA minimum of 2000 for type: {}, using 2000",
          customType.getPriority(),
          customType.getSemanticType());
      customType.setPriority(2000);
    }
    // Allow any priority >= 2000 to be used as-is for proper type differentiation

    // Set isBuiltIn flag if not already set
    // For user-created types, this will be false
    // For converted built-in types, this should already be set to true
    if (customType.getIsBuiltIn() == null) {
      customType.setIsBuiltIn(false);
    }

    // Assign createdAt for new custom types if not provided
    if (customType.getCreatedAt() == null) {
      customType.setCreatedAt(System.currentTimeMillis());
    }

    // CRITICAL: Fix list types to ensure FTA compatibility
    if ("list".equals(customType.getPluginType())) {
      // Fix content type if incorrectly set to "list" instead of "inline"
      if (customType.getContent() != null && "list".equals(customType.getContent().getType())) {
        customType.getContent().setType("inline");
        log.debug(
            "Fixed content type from 'list' to 'inline' for: {}", customType.getSemanticType());
      }

      // FTA requires uppercase for all list values (like built-in types)
      if (customType.getContent() != null && customType.getContent().getValues() != null) {
        List<String> uppercasedValues =
            customType.getContent().getValues().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        customType.getContent().setValues(uppercasedValues);
        log.debug("Uppercased list values for FTA compatibility: {}", customType.getSemanticType());
      }

      // FTA requires backout pattern for finite list types
      // If none provided, SemanticTypePluginService will add default ".*" later
      if (customType.getBackout() == null || customType.getBackout().isEmpty()) {
        // Don't throw error - let SemanticTypePluginService add default backout
        // This matches frontend behavior which doesn't set backout for list types
        customType.setBackout(".*");
        log.debug("Added default backout pattern for list type: {}", customType.getSemanticType());
      }
      // Test: Allow generic .* pattern to see if it actually works
      // Removed validation that blocks ".*" pattern
    }

    boolean isOverridingBuiltIn = validationService.isBuiltInType(customType.getSemanticType());
    if (isOverridingBuiltIn) {
      log.debug(
          "Creating custom type '{}' that will override the built-in type",
          customType.getSemanticType());
    }

    CustomSemanticType savedType = repository.save(customType);

    // Update vector index
    if (vectorIndexService != null) {
      vectorIndexService.reindexSemanticType(savedType);
    }

    return savedType;
  }

  /**
   * Updates an existing custom semantic type.
   *
   * @param semanticType the semantic type identifier to update
   * @param updatedType the new custom semantic type data
   * @return the updated custom semantic type
   * @throws IllegalArgumentException if the type doesn't exist or validation fails
   */
  public CustomSemanticType updateCustomType(String semanticType, CustomSemanticType updatedType) {
    if (!repository.existsBySemanticType(semanticType)) {
      throw new IllegalArgumentException(
          String.format("Semantic type not found: %s", semanticType));
    }

    validationService.validateCustomType(updatedType);

    // Ensure priority is set properly
    // FTA requires all external plugins to have priority >= 2000 (PRIORITY_EXTERNAL)
    // Built-in converted types should keep their original + 2000 priority
    // User-created types must have priority >= 2000 but we preserve their specific values
    if (updatedType.getPriority() == null) {
      updatedType.setPriority(2500);
    } else if (!Boolean.TRUE.equals(updatedType.getIsBuiltIn())
        && updatedType.getPriority() < 2000) {
      // FTA enforces minimum priority of 2000 for external plugins
      // Log warning but allow the user's priority to be preserved for debugging
      log.warn(
          "Priority {} is below FTA minimum of 2000 for type: {}, using 2000",
          updatedType.getPriority(),
          updatedType.getSemanticType());
      updatedType.setPriority(2000);
    }
    // Allow any priority >= 2000 to be used as-is for proper type differentiation

    // Don't override isBuiltIn flag if it's already set
    // This preserves the flag for converted built-in types
    if (updatedType.getIsBuiltIn() == null) {
      updatedType.setIsBuiltIn(false);
    }

    // CRITICAL: Fix list types to ensure FTA compatibility
    if ("list".equals(updatedType.getPluginType())) {
      // Fix content type if incorrectly set to "list" instead of "inline"
      if (updatedType.getContent() != null && "list".equals(updatedType.getContent().getType())) {
        updatedType.getContent().setType("inline");
        log.debug(
            "Fixed content type from 'list' to 'inline' for: {}", updatedType.getSemanticType());
      }

      // FTA requires uppercase for all list values (like built-in types)
      if (updatedType.getContent() != null && updatedType.getContent().getValues() != null) {
        List<String> uppercasedValues =
            updatedType.getContent().getValues().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        updatedType.getContent().setValues(uppercasedValues);
        log.debug(
            "Uppercased list values for FTA compatibility: {}", updatedType.getSemanticType());
      }

      // FTA requires backout pattern for finite list types
      // If none provided, SemanticTypePluginService will add default ".*" later
      if (updatedType.getBackout() == null || updatedType.getBackout().isEmpty()) {
        // Don't throw error - let SemanticTypePluginService add default backout
        // This matches frontend behavior which doesn't set backout for list types
        updatedType.setBackout(".*");
        log.debug("Added default backout pattern for list type: {}", updatedType.getSemanticType());
      }
      // Test: Allow generic .* pattern to see if it actually works
      // Removed validation that blocks ".*" pattern
    }

    if (!semanticType.equals(updatedType.getSemanticType())) {
      if (repository.existsBySemanticType(updatedType.getSemanticType())) {
        throw new IllegalArgumentException(
            String.format(
                "Type '%s' already exists. Use a different name.", updatedType.getSemanticType()));
      }

      boolean isOverridingBuiltIn = validationService.isBuiltInType(updatedType.getSemanticType());
      if (isOverridingBuiltIn) {
        log.debug(
            "Updating to type '{}' that will override the built-in type",
            updatedType.getSemanticType());
      }
    }

    // Preserve createdAt if not provided on update
    repository
        .findBySemanticType(semanticType)
        .ifPresent(
            existing -> {
              if (updatedType.getCreatedAt() == null) {
                updatedType.setCreatedAt(existing.getCreatedAt());
              }
            });
    CustomSemanticType updated = repository.update(semanticType, updatedType);

    // Update vector index
    if (vectorIndexService != null) {
      vectorIndexService.reindexSemanticType(updated);
    }

    return updated;
  }

  /**
   * Removes a custom semantic type.
   *
   * @param semanticType the semantic type identifier to remove
   * @throws IllegalArgumentException if the type doesn't exist
   */
  public void removeCustomType(String semanticType) {
    if (!repository.deleteBySemanticType(semanticType)) {
      throw new IllegalArgumentException(
          String.format("Semantic type not found: %s", semanticType));
    }

    // Remove from vector index
    if (vectorIndexService != null) {
      vectorIndexService.removeFromIndex(semanticType);
    }
  }

  /** Reloads custom types from persistent storage. */
  public void reloadCustomTypes() {
    repository.reload();
  }

  /**
   * Gets all semantic types (built-in and custom), with custom types taking precedence.
   *
   * @return list of all semantic types
   * @throws IOException if there's an error loading built-in types
   */
  public List<CustomSemanticType> getAllSemanticTypes() throws IOException {
    Map<String, CustomSemanticType> mergedTypes = new LinkedHashMap<>();

    List<PluginDefinition> builtInPlugins = pluginService.loadBuiltInPlugins();
    for (PluginDefinition plugin : builtInPlugins) {
      if (pluginService.hasEnglishOrUniversalLocale(plugin)) {
        CustomSemanticType builtInType = pluginService.convertPluginDefinitionToCustomType(plugin);
        if (builtInType != null) {
          mergedTypes.put(builtInType.getSemanticType(), builtInType);
        }
      }
    }

    List<CustomSemanticType> repositoryTypes = repository.findAll();
    log.trace(
        "repository.findAll() returned {} custom types (may include converted built-ins)",
        repositoryTypes.size());

    for (CustomSemanticType customType : repositoryTypes) {
      log.trace(
          "Found repository type: {} (isBuiltIn={})",
          customType.getSemanticType(),
          customType.getIsBuiltIn());
      mergedTypes.put(customType.getSemanticType(), customType);
    }

    // Provide a concise, disambiguated summary so logs are not misleading
    int poolTotal = mergedTypes.size();
    long poolBuiltIn =
        mergedTypes.values().stream().filter(t -> Boolean.TRUE.equals(t.getIsBuiltIn())).count();
    long poolCustom = poolTotal - poolBuiltIn;
    log.debug(
        "Semantic type pool (availability): total={}, builtInConverted={}, repoOrUserCustom={}. Note: this is an availability pool; the set actually registered depends on evaluation mode.",
        poolTotal,
        poolBuiltIn,
        poolCustom);
    return new ArrayList<>(mergedTypes.values());
  }

  /**
   * Gets all semantic types (built-in and custom).
   *
   * @return list of all semantic types
   * @throws IOException if there's an error loading built-in types
   */
  public List<CustomSemanticType> getAllCustomTypes() throws IOException {
    return getAllSemanticTypes();
  }

  /**
   * Gets only custom semantic types (excludes built-in types).
   *
   * @return list of custom semantic types only
   */
  public List<CustomSemanticType> getCustomTypesOnly() {
    return repository.findAll();
  }

  /**
   * Checks if a semantic type name is a known built-in type.
   *
   * @param semanticType the semantic type name to check
   * @return true if it's a known built-in type name, false otherwise
   */
  public boolean isKnownBuiltInType(String semanticType) {
    return validationService.isBuiltInType(semanticType);
  }

  /**
   * Gets only built-in semantic types as CustomSemanticType objects.
   *
   * @return list of built-in semantic types only
   * @throws IOException if there's an error loading built-in types
   */
  public List<CustomSemanticType> getBuiltInTypesAsCustomTypes() throws IOException {
    List<CustomSemanticType> builtInTypes = new ArrayList<>();
    List<PluginDefinition> builtInPlugins = pluginService.loadBuiltInPlugins();

    for (PluginDefinition plugin : builtInPlugins) {
      if (pluginService.hasEnglishOrUniversalLocale(plugin)) {
        CustomSemanticType builtInType = pluginService.convertPluginDefinitionToCustomType(plugin);
        if (builtInType != null) {
          builtInTypes.add(builtInType);
        }
      }
    }

    return builtInTypes;
  }

  /**
   * Gets a specific custom semantic type by identifier.
   *
   * @param semanticType the semantic type identifier
   * @return the custom semantic type
   * @throws IllegalArgumentException if the type is not found
   * @throws IOException if there's an error loading built-in types
   */
  public CustomSemanticType getCustomType(String semanticType) throws IOException {
    Optional<CustomSemanticType> typeOpt = repository.findBySemanticType(semanticType);
    if (typeOpt != null && typeOpt.isPresent()) {
      return typeOpt.get();
    }

    List<PluginDefinition> builtInPlugins = pluginService.loadBuiltInPlugins();
    for (PluginDefinition plugin : builtInPlugins) {
      if (plugin.semanticType.equals(semanticType)) {
        if (pluginService.hasEnglishOrUniversalLocale(plugin)) {
          return pluginService.convertPluginDefinitionToCustomType(plugin);
        }
      }
    }

    throw new IllegalArgumentException(String.format("Semantic type not found: %s", semanticType));
  }

  /**
   * Registers semantic types with FTA analyzer. Registers ALL custom types including those that
   * override built-in types. This ensures custom patterns take precedence over built-in ones.
   *
   * @param analyzer the text analyzer to register types with
   * @return empty map (no longer used for post-processing)
   */
  public Map<String, CustomSemanticType> registerSemanticTypes(TextAnalyzer analyzer) {
    try {
      // Get ALL semantic types (both built-in converted types and repository custom types)
      List<CustomSemanticType> allSemanticTypes = getAllSemanticTypes();
      Map<String, CustomSemanticType> allCustomTypes = new HashMap<>();

      // Register ALL semantic types to ensure they work with FTA
      for (CustomSemanticType customType : allSemanticTypes) {
        allCustomTypes.put(customType.getSemanticType(), customType);
      }

      if (!allCustomTypes.isEmpty()) {
        log.debug("Registering {} semantic types with FTA", allCustomTypes.size());
        pluginService.registerCustomTypes(analyzer, allCustomTypes);
      } else {
        log.warn("No semantic types available for registration");
      }

      return new HashMap<>();

    } catch (Exception e) {
      log.error("Failed to register semantic types", e);
      throw new RuntimeException("Failed to register semantic types", e);
    }
  }

  /**
   * Registers custom types with optional filtering for evaluation purposes. For evaluation mode,
   * this excludes converted built-in types (those with isBuiltIn=true).
   *
   * @param analyzer the text analyzer to register types with
   * @param excludeBuiltInTypes if true, excludes converted built-in types (for evaluation)
   * @return empty map (no longer used for post-processing)
   */
  public Map<String, CustomSemanticType> registerSemanticTypesForEvaluation(
      TextAnalyzer analyzer, boolean excludeBuiltInTypes) {
    try {
      // Get ALL semantic types (both built-in converted types and repository custom types)
      List<CustomSemanticType> allSemanticTypes = getAllSemanticTypes();
      log.debug("DEBUG: getAllSemanticTypes() returned {} total types", allSemanticTypes.size());

      // Debug: Count built-in vs custom
      long builtInCount =
          allSemanticTypes.stream()
              .filter(t -> t.getIsBuiltIn() != null && t.getIsBuiltIn())
              .count();
      long customCount =
          allSemanticTypes.stream()
              .filter(t -> t.getIsBuiltIn() == null || !t.getIsBuiltIn())
              .count();
      log.debug("DEBUG: Built-in types: {}, Custom types: {}", builtInCount, customCount);

      Map<String, CustomSemanticType> filteredTypes = new HashMap<>();

      // Filter types based on excludeBuiltInTypes parameter
      int excludedCount = 0;
      for (CustomSemanticType customType : allSemanticTypes) {
        // For evaluation (excludeBuiltInTypes=true), skip types that are converted built-ins
        if (excludeBuiltInTypes && customType.getIsBuiltIn() != null && customType.getIsBuiltIn()) {
          excludedCount++;
          continue;
        }
        filteredTypes.put(customType.getSemanticType(), customType);
      }

      if (!filteredTypes.isEmpty()) {
        int registerCount = filteredTypes.size();
        long registerBuiltIn =
            filteredTypes.values().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsBuiltIn()))
                .count();
        long registerCustom = registerCount - registerBuiltIn;
        log.info(
            "Evaluation registration: registering={} (customOnlyMode={}). Built-in={}; Custom={}",
            registerCount,
            excludeBuiltInTypes,
            registerBuiltIn,
            registerCustom);
        if (excludeBuiltInTypes) {
          log.info(
              "Evaluation registration: built-ins excluded ({}). Only dataset/user custom types are active.",
              excludedCount);
        }
        pluginService.registerCustomTypes(analyzer, filteredTypes);
      } else {
        log.warn(
            "EVALUATION MODE: No semantic types available for registration (excludeBuiltInTypes={})",
            excludeBuiltInTypes);
      }

      return new HashMap<>();

    } catch (Exception e) {
      log.error("Failed to register semantic types for evaluation", e);
      throw new RuntimeException("Failed to register semantic types for evaluation", e);
    }
  }

  /**
   * Converts all built-in FTA semantic types to custom types, making them deletable via API. This
   * allows complete control over which semantic types are available for analysis.
   *
   * @return map containing conversion results and statistics
   * @throws IOException if there's an error loading built-in types
   */
  public Map<String, Object> convertAllBuiltInTypesToCustom() throws IOException {
    log.info("Starting conversion of all built-in FTA semantic types to custom types");

    // Get all built-in types that aren't already custom
    List<CustomSemanticType> builtInTypes = getBuiltInTypesAsCustomTypes();
    List<CustomSemanticType> existingCustomTypes = getCustomTypesOnly();

    // Create a set of existing custom type names for quick lookup
    Map<String, CustomSemanticType> existingCustomMap = new HashMap<>();
    for (CustomSemanticType existing : existingCustomTypes) {
      existingCustomMap.put(existing.getSemanticType(), existing);
    }

    int converted = 0;
    int skipped = 0;
    int updated = 0;
    List<String> convertedTypes = new ArrayList<>();
    List<String> skippedTypes = new ArrayList<>();
    List<String> updatedTypes = new ArrayList<>();

    for (CustomSemanticType builtInType : builtInTypes) {
      String typeName = builtInType.getSemanticType();

      if (existingCustomMap.containsKey(typeName)) {
        // Type already exists as custom, check if we should update it
        CustomSemanticType existing = existingCustomMap.get(typeName);

        // CRITICAL: Always update if isBuiltIn flag is wrong, regardless of other fields
        boolean needsUpdate = false;

        // Check if isBuiltIn flag needs fixing
        if (existing.getIsBuiltIn() == null || !existing.getIsBuiltIn()) {
          needsUpdate = true;
        }

        // Also update if metadata has changed
        if (!existing.getDescription().equals(builtInType.getDescription())
            || !existing.getPriority().equals(builtInType.getPriority())) {
          needsUpdate = true;
        }

        if (needsUpdate) {
          // Update with built-in metadata and ensure isBuiltIn = true
          builtInType.setIsBuiltIn(true);
          updateCustomType(typeName, builtInType);
          updated++;
          updatedTypes.add(typeName);
        } else {
          skipped++;
          skippedTypes.add(typeName);
        }
      } else {
        // Convert built-in to custom
        try {
          // CRITICAL: Mark as built-in for frontend display, but it's a custom type in FTA
          builtInType.setIsBuiltIn(true);
          // Save directly to repository - this creates a CUSTOM type in FTA
          // but we track it as originally built-in via isBuiltIn flag
          repository.save(builtInType);
          converted++;
          convertedTypes.add(typeName);
        } catch (Exception e) {
          log.warn("Failed to convert built-in type '{}': {}", typeName, e.getMessage());
          skipped++;
          skippedTypes.add(typeName);
        }
      }
    }

    log.info(
        "Conversion completed: {} converted, {} updated, {} skipped", converted, updated, skipped);

    // Return detailed results
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalBuiltInTypes", builtInTypes.size());
    result.put("converted", converted);
    result.put("updated", updated);
    result.put("skipped", skipped);
    result.put("convertedTypes", convertedTypes);
    result.put("updatedTypes", updatedTypes);
    result.put("skippedTypes", skippedTypes);
    result.put(
        "message",
        String.format(
            "Successfully processed %d built-in types: %d converted, %d updated, %d skipped",
            builtInTypes.size(), converted, updated, skipped));

    return result;
  }
}

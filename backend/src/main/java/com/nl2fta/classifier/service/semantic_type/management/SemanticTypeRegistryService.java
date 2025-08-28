package com.nl2fta.classifier.service.semantic_type.management;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.PluginDocumentationEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing and providing semantic type information. This service loads built-in
 * semantic types from plugins.json and caches them for efficient access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeRegistryService {

  private final ObjectMapper objectMapper;

  @Value("${fta.cache.enabled:true}")
  private boolean cacheEnabled;

  @Value("${fta.cache.max-size:1000}")
  private long cacheMaxSize;

  @Value("${fta.cache.expire-after-write-minutes:60}")
  private long cacheExpireMinutes;

  @Value("${fta.custom.types.plugin.resource:/reference/plugins.json}")
  private String pluginResource;

  // Cache for semantic type definitions
  private final Map<String, SemanticTypeInfo> builtInTypes = new ConcurrentHashMap<>();

  // Cache for quick description lookups
  private Cache<String, String> descriptionCache;

  @PostConstruct
  public void init() {
    initializeCache();
    loadBuiltInSemanticTypes();
  }

  private void initializeCache() {
    if (cacheEnabled) {
      descriptionCache =
          CacheBuilder.newBuilder()
              .maximumSize(cacheMaxSize)
              .expireAfterWrite(cacheExpireMinutes, TimeUnit.MINUTES)
              .build();
    }
  }

  private void loadBuiltInSemanticTypes() {
    try (InputStream is = PluginDefinition.class.getResourceAsStream(pluginResource);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

      List<PluginDefinition> plugins =
          objectMapper.readValue(reader, new TypeReference<List<PluginDefinition>>() {});

      for (PluginDefinition plugin : plugins) {
        SemanticTypeInfo info = convertToSemanticTypeInfo(plugin);
        builtInTypes.put(plugin.semanticType, info);
      }

      log.info("Loaded {} built-in semantic types from {}", builtInTypes.size(), pluginResource);

    } catch (IOException e) {
      log.error("Failed to load built-in semantic types from {}", pluginResource, e);
      throw new RuntimeException("Failed to initialize semantic type registry", e);
    }
  }

  /**
   * Get description for a semantic type
   *
   * @param semanticType The semantic type identifier
   * @return The description or the semantic type itself if not found
   */
  public String getDescription(String semanticType) {
    if (semanticType == null) {
      return null;
    }

    // Check cache first if enabled
    if (cacheEnabled && descriptionCache != null) {
      String cached = descriptionCache.getIfPresent(semanticType);
      if (cached != null) {
        return cached;
      }
    }

    // Look up in built-in types
    SemanticTypeInfo info = builtInTypes.get(semanticType);
    String description = info != null ? info.getDescription() : semanticType;

    // Cache the result if caching is enabled
    if (cacheEnabled && descriptionCache != null) {
      descriptionCache.put(semanticType, description);
    }

    return description;
  }

  /**
   * Get full information for a semantic type
   *
   * @param semanticType The semantic type identifier
   * @return The semantic type info or null if not found
   */
  public SemanticTypeInfo getSemanticTypeInfo(String semanticType) {
    return builtInTypes.get(semanticType);
  }

  /**
   * Check if a semantic type is built-in
   *
   * @param semanticType The semantic type identifier
   * @return true if it's a built-in type, false otherwise
   */
  public boolean isBuiltInType(String semanticType) {
    return builtInTypes.containsKey(semanticType);
  }

  /**
   * Get all built-in semantic types
   *
   * @return Map of all built-in semantic types
   */
  public Map<String, SemanticTypeInfo> getAllBuiltInTypes() {
    return new ConcurrentHashMap<>(builtInTypes);
  }

  private SemanticTypeInfo convertToSemanticTypeInfo(PluginDefinition plugin) {
    SemanticTypeInfo info = new SemanticTypeInfo();
    info.setSemanticType(plugin.semanticType);
    info.setDescription(plugin.description);
    info.setPluginType(plugin.pluginType);
    info.setBaseType(plugin.baseType != null ? plugin.baseType.toString() : "STRING");
    info.setThreshold(plugin.threshold);
    info.setPriority(plugin.priority);

    // Extract documentation
    if (plugin.documentation != null && plugin.documentation.length > 0) {
      for (PluginDocumentationEntry doc : plugin.documentation) {
        if ("wikipedia".equals(doc.source) || "wikidata".equals(doc.source)) {
          info.setDocumentationUrl(doc.reference);
          break;
        }
      }
    }

    return info;
  }

  public void clearCache() {
    if (descriptionCache != null) {
      descriptionCache.invalidateAll();
    }
    log.info("Cleared semantic type caches");
  }

  public void reload() {
    builtInTypes.clear();
    clearCache();
    loadBuiltInSemanticTypes();
    log.info("Reloaded semantic type registry");
  }

  /** Data class for semantic type information */
  @Data
  public static class SemanticTypeInfo {
    private String semanticType;
    private String description;
    private String pluginType;
    private String baseType;
    private Integer threshold;
    private Integer priority;
    private String documentationUrl;
  }
}

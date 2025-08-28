package com.nl2fta.classifier.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.config.ApplicationProperties;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * File-based repository for managing persistent storage of custom semantic types. Handles
 * file-based persistence with JSON format. This is the legacy implementation -
 * S3CustomSemanticTypeRepository is preferred.
 */
@Slf4j
@Repository("fileBasedCustomSemanticTypeRepository")
@RequiredArgsConstructor
public class FileBasedCustomSemanticTypeRepository implements ICustomSemanticTypeRepository {

  private final ObjectMapper objectMapper;
  private final ApplicationProperties applicationProperties;
  private final Map<String, CustomSemanticType> customTypes = new ConcurrentHashMap<>();
  private Path customTypesFile;

  @PostConstruct
  public void init() {
    String overridden = System.getProperty("FTA_CUSTOM_TYPES_FILE");
    String targetPath =
        overridden != null && !overridden.isBlank()
            ? overridden
            : applicationProperties.getCustomTypesFile();
    customTypesFile = Paths.get(targetPath);
    // Ensure parent directory exists to avoid failures on first save
    try {
      Path parent = customTypesFile.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      log.warn(
          "Failed to ensure directory for custom types file '{}': {}",
          customTypesFile,
          e.getMessage());
    }
    loadCustomTypes();
  }

  /**
   * Saves a custom semantic type to the repository.
   *
   * @param customType the custom semantic type to save
   * @return the saved custom semantic type
   */
  @Override
  public CustomSemanticType save(CustomSemanticType customType) {
    customTypes.put(customType.getSemanticType(), customType);
    saveCustomTypes();
    return customType;
  }

  /**
   * Finds a custom semantic type by its semantic type identifier.
   *
   * @param semanticType the semantic type identifier
   * @return the custom semantic type if found
   */
  @Override
  public Optional<CustomSemanticType> findBySemanticType(String semanticType) {
    if (semanticType == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(customTypes.get(semanticType));
  }

  /**
   * Checks if a custom semantic type exists.
   *
   * @param semanticType the semantic type identifier
   * @return true if the type exists, false otherwise
   */
  @Override
  public boolean existsBySemanticType(String semanticType) {
    if (semanticType == null) {
      return false;
    }
    return customTypes.containsKey(semanticType);
  }

  /**
   * Removes a custom semantic type from the repository.
   *
   * @param semanticType the semantic type identifier to remove
   * @return true if the type was removed, false if it didn't exist
   */
  @Override
  public boolean deleteBySemanticType(String semanticType) {
    if (semanticType == null) {
      return false;
    }
    CustomSemanticType removed = customTypes.remove(semanticType);
    if (removed != null) {
      saveCustomTypes();
      return true;
    }
    return false;
  }

  /**
   * Updates an existing custom semantic type.
   *
   * @param oldSemanticType the old semantic type identifier
   * @param newCustomType the new custom semantic type data
   * @return the updated custom semantic type
   */
  @Override
  public CustomSemanticType update(String oldSemanticType, CustomSemanticType newCustomType) {
    customTypes.remove(oldSemanticType);
    customTypes.put(newCustomType.getSemanticType(), newCustomType);
    saveCustomTypes();
    return newCustomType;
  }

  /**
   * Retrieves all custom semantic types.
   *
   * @return list of all custom semantic types
   */
  @Override
  public List<CustomSemanticType> findAll() {
    return new ArrayList<>(customTypes.values());
  }

  /**
   * Gets the count of custom semantic types.
   *
   * @return the number of custom semantic types
   */
  public int count() {
    return customTypes.size();
  }

  /** Clears all custom semantic types and reloads from file. */
  @Override
  public void reload() {
    customTypes.clear();
    loadCustomTypes();
  }

  /**
   * Gets a defensive copy of the internal map for advanced operations.
   *
   * @return a copy of the internal concurrent hash map
   */
  public Map<String, CustomSemanticType> getInternalMap() {
    return new ConcurrentHashMap<>(customTypes);
  }

  private void loadCustomTypes() {
    if (!Files.exists(customTypesFile)) {
      log.debug(
          "No custom types file found at {}, starting with empty repository", customTypesFile);
      return;
    }

    try {
      String json = Files.readString(customTypesFile);
      List<CustomSemanticType> types =
          objectMapper.readValue(
              json,
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(List.class, CustomSemanticType.class));

      for (CustomSemanticType type : types) {
        // Ensure all types have valid priority (fix for legacy types)
        if (type.getPriority() == null || type.getPriority() < 2000) {
          log.warn(
              "Semantic type '{}' has invalid priority {}, setting to 2000",
              type.getSemanticType(),
              type.getPriority());
          type.setPriority(2000);
        }
        customTypes.put(type.getSemanticType(), type);
      }

      log.info("Loaded {} custom semantic types from {}", customTypes.size(), customTypesFile);

    } catch (IOException e) {
      log.error("Failed to load custom types from file: {}", customTypesFile, e);
    }
  }

  private void saveCustomTypes() {
    try {
      // Ensure parent directory exists before writing
      Path parent = customTypesFile.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      List<CustomSemanticType> types = new ArrayList<>(customTypes.values());
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(types);
      Files.writeString(customTypesFile, json);

      log.debug("Saved {} custom semantic types to {}", types.size(), customTypesFile);

    } catch (IOException e) {
      log.error("Failed to save custom types to file: {}", customTypesFile, e);
    }
  }
}

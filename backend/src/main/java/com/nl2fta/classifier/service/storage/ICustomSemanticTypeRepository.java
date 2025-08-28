package com.nl2fta.classifier.service.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

/** Interface for custom semantic type repository operations. */
public interface ICustomSemanticTypeRepository {

  /**
   * Saves a custom semantic type to the repository.
   *
   * @param customType the custom semantic type to save
   * @return the saved custom semantic type
   */
  CustomSemanticType save(CustomSemanticType customType);

  /**
   * Finds a custom semantic type by its identifier.
   *
   * @param semanticType the semantic type identifier
   * @return the custom semantic type if found
   */
  Optional<CustomSemanticType> findBySemanticType(String semanticType);

  /**
   * Checks if a custom semantic type exists.
   *
   * @param semanticType the semantic type identifier
   * @return true if the type exists, false otherwise
   */
  boolean existsBySemanticType(String semanticType);

  /**
   * Deletes a custom semantic type by its identifier.
   *
   * @param semanticType the semantic type identifier
   * @return true if the type was deleted, false if not found
   */
  boolean deleteBySemanticType(String semanticType);

  /**
   * Updates an existing custom semantic type.
   *
   * @param semanticType the original semantic type identifier
   * @param updatedType the updated custom semantic type
   * @return the updated custom semantic type
   */
  CustomSemanticType update(String semanticType, CustomSemanticType updatedType);

  /**
   * Finds all custom semantic types.
   *
   * @return list of all custom semantic types
   */
  List<CustomSemanticType> findAll();

  /** Reloads custom types from persistent storage. */
  void reload();

  /**
   * Gets a defensive copy of the internal map for advanced operations.
   *
   * @return a copy of the internal map
   */
  default Map<String, CustomSemanticType> getInternalMap() {
    // Default implementation returns a map from findAll()
    Map<String, CustomSemanticType> map = new java.util.HashMap<>();
    for (CustomSemanticType type : findAll()) {
      map.put(type.getSemanticType(), type);
    }
    return map;
  }
}

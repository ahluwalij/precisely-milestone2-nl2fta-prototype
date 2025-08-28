package com.nl2fta.classifier.service.vector;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a vector embedding with associated metadata for storage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorData {

  /** Unique identifier for the vector (based on semantic type). */
  private String id;

  /** The semantic type name (e.g., "NAME.FIRST", "EMAIL"). */
  private String semanticType;

  /** Type of semantic type (built-in or custom). */
  private String type;

  /** Description of the semantic type. */
  private String description;

  /** The embedding vector as a list of floats. */
  private List<Float> embedding;

  /** Original text used to generate the embedding. */
  private String originalText;

  /** Plugin type (e.g., "regex", "list", "java"). */
  private String pluginType;

  /** Sample positive examples for this type. */
  private List<String> examples;

  /** Timestamp when this vector was created. */
  private Instant createdAt;

  /** Timestamp when this vector was last updated. */
  private Instant updatedAt;
}

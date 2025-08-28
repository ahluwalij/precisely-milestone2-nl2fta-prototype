package com.nl2fta.classifier.service.vector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for performing vector-based similarity search for semantic types. Replaces the
 * prompt-based similarity checking with efficient vector search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSimilaritySearchService {

  private final VectorEmbeddingService embeddingService;
  private final S3VectorStorageService storageService;

  @Autowired @Lazy private VectorIndexInitializationService indexInitService;

  /** Default similarity threshold for considering a match. */
  private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.35;

  /** Number of top matches to consider. */
  private static final int TOP_K_MATCHES = 5;

  /** Number of top matches to return for LLM evaluation. */
  private static final int TOP_K_FOR_LLM = 3;

  /**
   * Find similar semantic types based on vector similarity.
   *
   * @param request The semantic type generation request
   * @param threshold Similarity threshold (0-1)
   * @return List of similar semantic types sorted by similarity score
   */
  public List<SimilaritySearchResult> findSimilarTypes(
      SemanticTypeGenerationRequest request, double threshold) {
    // Generate query text from request
    String queryText = generateQueryText(request);
    log.info("Query text for vector search: {}", queryText);

    // Generate embedding for query
    List<Float> queryEmbedding = embeddingService.generateEmbedding(queryText);

    // Retrieve all vectors from storage
    List<VectorData> allVectors = storageService.getAllVectors();

    // Calculate similarities and filter by threshold (vectors are unique per type)
    List<SimilaritySearchResult> rawResults =
        allVectors.stream()
            .map(
                vectorData -> {
                  double similarity =
                      embeddingService.calculateCosineSimilarity(
                          queryEmbedding, vectorData.getEmbedding());

                  // Keep logs at debug to avoid noisy duplication in docker output
                  if (similarity > 0.7) {
                    log.debug(
                        "Similarity for {} ({}): {}",
                        vectorData.getSemanticType(),
                        vectorData.getDescription(),
                        similarity);
                  }

                  return SimilaritySearchResult.builder()
                      .semanticType(vectorData.getSemanticType())
                      .description(vectorData.getDescription())
                      .similarityScore(similarity)
                      .type(vectorData.getType())
                      .pluginType(vectorData.getPluginType())
                      .examples(vectorData.getExamples())
                      .build();
                })
            .filter(result -> result.getSimilarityScore() >= threshold)
            .collect(Collectors.toList());

    // De-duplicate by semanticType, keeping the highest similarity score
    var bestByType =
        rawResults.stream()
            .collect(
                Collectors.toMap(
                    SimilaritySearchResult::getSemanticType,
                    r -> r,
                    (a, b) -> a.getSimilarityScore() >= b.getSimilarityScore() ? a : b));

    List<SimilaritySearchResult> results =
        bestByType.values().stream()
            .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
            .limit(TOP_K_MATCHES)
            .collect(Collectors.toList());

    log.info("Found {} similar types above threshold {} for query", results.size(), threshold);
    return results;
  }

  /**
   * Find the most similar semantic type.
   *
   * @param request The semantic type generation request
   * @return The most similar type or null if none found above threshold
   */
  public SimilaritySearchResult findMostSimilarType(SemanticTypeGenerationRequest request) {
    List<SimilaritySearchResult> results = findSimilarTypes(request, DEFAULT_SIMILARITY_THRESHOLD);
    return results.isEmpty() ? null : results.get(0);
  }

  /**
   * Find the top 3 most similar semantic types for LLM evaluation. Only returns types above the
   * similarity threshold.
   *
   * @param request The semantic type generation request
   * @param threshold The similarity threshold (typically 0.85)
   * @return List of top 3 similar types above threshold (empty list if none found)
   */
  public List<SimilaritySearchResult> findTopSimilarTypesForLLM(
      SemanticTypeGenerationRequest request, double threshold) {
    // Find all types above the threshold
    List<SimilaritySearchResult> results = findSimilarTypes(request, threshold);

    // Return up to TOP_K_FOR_LLM results
    return results.stream().limit(TOP_K_FOR_LLM).collect(Collectors.toList());
  }

  /**
   * Index a semantic type by generating and storing its vector embedding.
   *
   * @param semanticType The semantic type to index
   */
  public void indexSemanticType(CustomSemanticType semanticType) {
    try {
      // Generate text representation
      String text =
          embeddingService.generateSemanticTypeText(
              semanticType.getSemanticType(),
              semanticType.getDescription(),
              extractExamples(semanticType));

      // Generate embedding
      List<Float> embedding = embeddingService.generateEmbedding(text);

      // Create vector data
      VectorData vectorData =
          VectorData.builder()
              .id(storageService.generateVectorId(semanticType.getSemanticType()))
              .semanticType(semanticType.getSemanticType())
              .type(
                  semanticType.getIsBuiltIn() != null && semanticType.getIsBuiltIn()
                      ? "built-in"
                      : "custom")
              .description(semanticType.getDescription())
              .embedding(embedding)
              .originalText(text)
              .pluginType(semanticType.getPluginType())
              .examples(extractExamples(semanticType))
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      // Store in S3
      storageService.storeVector(vectorData);
      log.info("Indexed semantic type: {}", semanticType.getSemanticType());

    } catch (Exception e) {
      log.error("Error indexing semantic type: {}", semanticType.getSemanticType(), e);
      throw new RuntimeException("Failed to index semantic type", e);
    }
  }

  /**
   * Index multiple semantic types in batch.
   *
   * @param semanticTypes List of semantic types to index
   */
  public void indexSemanticTypes(List<CustomSemanticType> semanticTypes) {
    log.info("Indexing {} semantic types", semanticTypes.size());

    for (CustomSemanticType type : semanticTypes) {
      try {
        indexSemanticType(type);
        // Update progress if indexInitService is available
        if (indexInitService != null) {
          indexInitService.incrementIndexedTypesCount();
        }
      } catch (Exception e) {
        log.error(
            "Failed to index semantic type: {}, continuing with others", type.getSemanticType(), e);
      }
    }
  }

  /**
   * Remove a semantic type from the vector index.
   *
   * @param semanticType The semantic type name to remove
   */
  public void removeFromIndex(String semanticType) {
    storageService.deleteVector(semanticType);
    log.info("Removed semantic type from index: {}", semanticType);
  }

  /** Clear all vectors from the index. */
  public void clearIndex() {
    storageService.clearAllVectors();
    log.info("Cleared all vectors from index");
  }

  /**
   * Get all stored vectors from storage.
   *
   * @return List of all stored vectors
   */
  public List<VectorData> getAllStoredVectors() {
    return storageService.getAllVectors();
  }

  /**
   * Check if any vectors exist in storage without downloading them.
   *
   * @return true if at least one vector exists
   */
  public boolean hasAnyStoredVectors() {
    return storageService.hasAnyVectors();
  }

  /**
   * Get count of vectors in storage without downloading them.
   *
   * @return count of stored vectors
   */
  public int getStoredVectorCount() {
    return storageService.getVectorCount();
  }

  /**
   * Generate query text from the semantic type generation request. Format this to be more similar
   * to the stored semantic type text format to improve embedding similarity matching.
   */
  private String generateQueryText(SemanticTypeGenerationRequest request) {
    StringBuilder text = new StringBuilder();

    // Include description (using same format as stored types)
    if (request.getDescription() != null && !request.getDescription().isEmpty()) {
      text.append("Description: ").append(request.getDescription()).append("\n");
    }

    // Include positive content examples (limit to keep prompt compact)
    if (request.getPositiveContentExamples() != null
        && !request.getPositiveContentExamples().isEmpty()) {
      text.append("Examples: ");
      text.append(
          request.getPositiveContentExamples().stream()
              .limit(10)
              .collect(Collectors.joining(", ")));
      text.append("\n");
    }

    // Include positive header examples if provided
    if (request.getPositiveHeaderExamples() != null
        && !request.getPositiveHeaderExamples().isEmpty()) {
      text.append("Header Examples: ");
      text.append(
          request.getPositiveHeaderExamples().stream().limit(5).collect(Collectors.joining(", ")));
    }

    return text.toString();
  }

  /**
   * Try to infer a semantic type name from the description. This helps improve matching with stored
   * vectors that include the semantic type.
   */
  // Intentionally no inferring from description — keep query purely from user input

  /** Extract examples from a semantic type. */
  private List<String> extractExamples(CustomSemanticType semanticType) {
    List<String> examples = new ArrayList<>();

    // Extract from content values if available
    if (semanticType.getContent() != null && semanticType.getContent().getValues() != null) {
      examples.addAll(
          semanticType.getContent().getValues().stream().limit(10).collect(Collectors.toList()));
    }

    // Extract from match entries if available
    if (semanticType.getValidLocales() != null && !semanticType.getValidLocales().isEmpty()) {
      var locale = semanticType.getValidLocales().get(0);
      if (locale.getMatchEntries() != null) {
        locale.getMatchEntries().stream()
            .limit(5)
            .forEach(
                entry -> {
                  if (entry.getDescription() != null) {
                    examples.add(entry.getDescription());
                  }
                });
      }
    }

    return examples;
  }

  /** Result of a similarity search. */
  @lombok.Builder
  @lombok.Data
  public static class SimilaritySearchResult {
    private String semanticType;
    private String description;
    private double similarityScore;
    private String type;
    private String pluginType;
    private List<String> examples;
  }
}

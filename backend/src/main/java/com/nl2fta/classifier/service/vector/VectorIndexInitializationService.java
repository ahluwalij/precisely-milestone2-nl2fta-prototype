package com.nl2fta.classifier.service.vector;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for initializing and maintaining the vector index. Only indexes semantic
 * types after AWS credentials are provided through the frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexInitializationService {

  private final VectorSimilaritySearchService vectorSearchService;
  private final CustomSemanticTypeService customSemanticTypeService;
  private final AwsCredentialsService awsCredentialsService;
  private final VectorEmbeddingService vectorEmbeddingService;

  @Value("${vector.index.enabled:true}")
  private boolean vectorIndexEnabled;

  @Value("${vector.index.rebuild-on-startup:false}")
  private boolean rebuildOnStartup;

  // Indexing status tracking
  private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
  private final AtomicInteger totalTypesToIndex = new AtomicInteger(0);
  private final AtomicInteger indexedTypesCount = new AtomicInteger(0);
  // Minimum expected number of indexed types when the bucket is already populated
  private static final int MIN_EXPECTED_INDEXED_TYPES = 132;

  /**
   * Initialize vector index after application is ready. No indexing on startup - waits for AWS
   * credentials from frontend.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (!vectorIndexEnabled) {
      log.info("Vector indexing is disabled");
      return;
    }

    log.info("Vector index service ready - waiting for AWS credentials from frontend");
  }

  /**
   * Initialize vector index when AWS credentials are provided. Called by the controller when user
   * connects AWS through frontend.
   */
  public void initializeAfterAwsConnection() {
    if (!vectorIndexEnabled) {
      log.info("Vector indexing is disabled");
      return;
    }

    if (!awsCredentialsService.areCredentialsAvailable()) {
      log.warn("Cannot initialize vector index - AWS credentials not available");
      return;
    }

    // Initialize the embedding service with AWS credentials
    vectorEmbeddingService.initializeBedrockClient();

    // Capture the username from MDC before async execution
    String username = MDC.get("username");

    // Run indexing asynchronously to not block the request
    // Add a small delay to ensure S3 services are fully initialized
    CompletableFuture.runAsync(
        () -> {
          try {
            // Restore MDC context in async thread
            if (username != null) {
              MDC.put("username", username);
            }

            // Wait a bit to ensure S3 client is initialized
            Thread.sleep(1000);
            initializeVectorIndex();
          } catch (Exception e) {
            log.error("Failed to initialize vector index", e);
          } finally {
            // Clean up MDC
            MDC.remove("username");
          }
        });
  }

  /** Initialize the vector index with all existing semantic types. */
  private void initializeVectorIndex() {
    log.info("Starting vector index initialization...");

    try {
      // Set indexing status
      indexingInProgress.set(true);
      indexedTypesCount.set(0);

      // Check if vectors already exist in S3
      log.info("Checking for existing vectors in S3...");
      boolean hasExistingVectors = vectorSearchService.hasAnyStoredVectors();

      if (hasExistingVectors && !rebuildOnStartup) {
        // Get counts to verify index integrity
        int vectorCount = vectorSearchService.getStoredVectorCount();
        log.info("Found {} vectors in S3 storage", vectorCount);

        // New rule: only re-index if bucket clearly uninitialized (< MIN_EXPECTED_INDEXED_TYPES)
        if (vectorCount >= MIN_EXPECTED_INDEXED_TYPES) {
          log.info(
              "Vector bucket appears initialized (>= {}). Skipping re-indexing.",
              MIN_EXPECTED_INDEXED_TYPES);
          totalTypesToIndex.set(vectorCount);
          indexedTypesCount.set(vectorCount);
          indexingInProgress.set(false);
          return;
        }

        // Otherwise treat as first-time/partial and proceed to index
        log.warn(
            "Vector bucket has {} entries (< {}). Proceeding with indexing to complete the set.",
            vectorCount,
            MIN_EXPECTED_INDEXED_TYPES);
      } else if (!hasExistingVectors) {
        log.info("No existing vectors found in S3 - will index all semantic types");
      }

      // Clear existing index if rebuild is requested
      if (rebuildOnStartup) {
        log.info("Clearing existing vector index for rebuild");
        vectorSearchService.clearIndex();
      }

      // Get all semantic types
      List<CustomSemanticType> allTypes = customSemanticTypeService.getAllCustomTypes();
      log.info("Found {} semantic types to index", allTypes.size());
      totalTypesToIndex.set(allTypes.size());

      // Index all types
      long startTime = System.currentTimeMillis();
      vectorSearchService.indexSemanticTypes(allTypes);
      long duration = System.currentTimeMillis() - startTime;

      log.info("Vector index initialization completed in {} ms", duration);

    } catch (Exception e) {
      log.error("Error during vector index initialization", e);
      throw new RuntimeException("Failed to initialize vector index", e);
    } finally {
      indexingInProgress.set(false);
    }
  }

  /**
   * Re-index a specific semantic type. Called when a type is created or updated.
   *
   * @param semanticType The semantic type to re-index
   */
  @Async
  public void reindexSemanticType(CustomSemanticType semanticType) {
    if (!vectorIndexEnabled) {
      return;
    }

    if (semanticType == null) {
      log.warn("Cannot re-index null semantic type");
      return;
    }

    try {
      log.debug("Re-indexing semantic type (async): {}", semanticType.getSemanticType());
      vectorSearchService.indexSemanticType(semanticType);
    } catch (Exception e) {
      log.error("Failed to re-index semantic type: {}", semanticType.getSemanticType(), e);
    }
  }

  /**
   * Remove a semantic type from the index. Called when a type is deleted.
   *
   * @param semanticTypeName The name of the semantic type to remove
   */
  @Async
  public void removeFromIndex(String semanticTypeName) {
    if (!vectorIndexEnabled) {
      return;
    }

    try {
      log.debug("Removing semantic type from index (async): {}", semanticTypeName);
      vectorSearchService.removeFromIndex(semanticTypeName);
    } catch (Exception e) {
      log.error("Failed to remove semantic type from index: {}", semanticTypeName, e);
    }
  }

  /**
   * Rebuild the entire vector index. This is useful for maintenance or when the embedding model
   * changes.
   */
  public void rebuildIndex() {
    if (!vectorIndexEnabled) {
      throw new RuntimeException("Cannot rebuild index - vector indexing is disabled");
    }

    log.info("Rebuilding vector index...");

    try {
      // Clear existing index
      vectorSearchService.clearIndex();

      // Re-initialize
      initializeVectorIndex();

    } catch (Exception e) {
      log.error("Failed to rebuild vector index", e);
      throw new RuntimeException("Failed to rebuild vector index", e);
    }
  }

  /** Get current indexing status */
  public boolean isIndexing() {
    return indexingInProgress.get();
  }

  /** Get total number of types to index */
  public int getTotalTypesToIndex() {
    return totalTypesToIndex.get();
  }

  /** Get number of types already indexed */
  public int getIndexedTypesCount() {
    return indexedTypesCount.get();
  }

  /** Update indexed types count (called from VectorSimilaritySearchService) */
  public void incrementIndexedTypesCount() {
    indexedTypesCount.incrementAndGet();
  }
}

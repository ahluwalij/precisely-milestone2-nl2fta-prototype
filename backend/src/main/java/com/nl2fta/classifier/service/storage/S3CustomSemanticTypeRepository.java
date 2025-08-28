package com.nl2fta.classifier.service.storage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3-based implementation of the custom semantic type repository. Stores each semantic type as an
 * individual JSON file in S3, with local caching for performance.
 */
@Slf4j
@Repository
@Lazy
public class S3CustomSemanticTypeRepository implements ICustomSemanticTypeRepository {

  private final AwsCredentialsService awsCredentialsService;
  private final ObjectMapper objectMapper;

  @Value("${aws.s3.semantic-types-bucket:#{null}}")
  private String configuredBucketName;

  private String bucketName;

  // Changed from single file to prefix for individual files
  private final String semanticTypesPrefix = "semantic-types/";

  private S3Client s3Client;
  private final Map<String, CustomSemanticType> cache = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduler;
  private volatile boolean initialized = false;

  @Value("${aws.s3.semantic-types.async-persist:true}")
  private boolean asyncPersist;

  public S3CustomSemanticTypeRepository(
      AwsCredentialsService awsCredentialsService, ObjectMapper objectMapper) {
    this.awsCredentialsService = awsCredentialsService;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void init() {
    // Don't initialize on startup - wait for AWS credentials from frontend
    log.info("S3CustomSemanticTypeRepository created - waiting for AWS credentials");
  }

  /**
   * Initialize S3 repository when AWS credentials are provided. Called by HybridRepository when
   * user connects AWS.
   */
  public void initializeWithCredentials() {
    log.debug("Initializing S3 custom semantic type repository with credentials");

    // Set bucket name from configuration if available
    if (configuredBucketName != null && !configuredBucketName.trim().isEmpty()) {
      bucketName = configuredBucketName;
      log.info("Using configured bucket name: {}", bucketName);
    }

    if (!awsCredentialsService.areCredentialsAvailable()) {
      throw new IllegalStateException(
          "Cannot initialize S3 repository - AWS credentials not available");
    }

    try {
      // Generate bucket name with account ID and region if not configured
      if (configuredBucketName == null || configuredBucketName.trim().isEmpty()) {
        String accountId = getAwsAccountId();
        String region = awsCredentialsService.getRegion();

        if (accountId != null) {
          // Use account and region-specific bucket name
          bucketName = "nl2fta-semantic-types-" + accountId + "-" + region;
          log.info("Generated account and region-specific bucket name: {}", bucketName);
        } else {
          // Fallback to a random suffix with region if we can't get account ID
          String randomSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
          bucketName = "nl2fta-semantic-types-" + randomSuffix + "-" + region;
          log.warn("Could not get AWS account ID, using random suffix with region: {}", bucketName);
        }
      }

      log.info("Bucket name: {}", bucketName);
      log.info("Semantic types prefix: {}", semanticTypesPrefix);

      // Initialize S3 client
      log.info("Building S3 client...");
      this.s3Client =
          S3Client.builder()
              .region(Region.of(awsCredentialsService.getRegion()))
              .credentialsProvider(awsCredentialsService.getCredentialsProvider())
              .build();
      log.info("S3 client built successfully");

      // Clean up any existing scheduler first
      if (scheduler != null && !scheduler.isShutdown()) {
        log.info("Shutting down existing scheduler before creating new one");
        scheduler.shutdownNow();
        try {
          scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }

      // Create new scheduler
      log.info("Creating new scheduler for periodic sync");
      scheduler = Executors.newSingleThreadScheduledExecutor();

      // Ensure bucket exists
      log.info("Ensuring bucket exists...");
      log.info(
          "BUCKET NAME TO BE USED: {} (Region: {})", bucketName, awsCredentialsService.getRegion());
      ensureBucketExists();

      // Load initial data immediately
      log.info("Loading initial data from S3...");
      reload();

      // Keep initial quick syncs so the cache reflects connection rapidly
      scheduler.schedule(this::syncFromS3, 5, TimeUnit.SECONDS);
      scheduler.schedule(this::syncFromS3, 15, TimeUnit.SECONDS);
      scheduler.schedule(this::syncFromS3, 30, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(this::syncFromS3, 60, 60, TimeUnit.SECONDS);
      log.info("Scheduled periodic sync every 60 seconds with early warm-ups");

      initialized = true;
      log.info(
          "S3CustomSemanticTypeRepository initialized successfully with bucket: {}", bucketName);
      log.info("=== End S3CustomSemanticTypeRepository.initializeWithCredentials() ===");

    } catch (Exception e) {
      log.error("Failed to initialize S3CustomSemanticTypeRepository: {}", e.getMessage(), e);
      initialized = false;
      // Clean up scheduler on failure
      if (scheduler != null && !scheduler.isShutdown()) {
        log.info("Shutting down scheduler due to initialization failure");
        scheduler.shutdownNow();
        scheduler = null;
      }
      if (s3Client != null) {
        s3Client.close();
        s3Client = null;
      }
      throw new RuntimeException("Failed to initialize S3 repository", e);
    }
  }

  @PreDestroy
  public void cleanup() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }
    if (s3Client != null) {
      s3Client.close();
    }
    initialized = false;
  }

  /** Check if repository is initialized. */
  public boolean isInitialized() {
    return initialized && s3Client != null;
  }

  private void ensureBucketExists() {
    try {
      log.info("Checking if S3 bucket '{}' exists...", bucketName);
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      log.info("S3 bucket '{}' exists and is accessible", bucketName);
    } catch (NoSuchBucketException e) {
      log.info("Bucket '{}' does not exist, creating it...", bucketName);
      try {
        CreateBucketRequest.Builder createRequest =
            CreateBucketRequest.builder().bucket(bucketName);

        // Only set location constraint for non us-east-1 regions
        String region = awsCredentialsService.getRegion();
        if (!"us-east-1".equals(region)) {
          createRequest.createBucketConfiguration(
              CreateBucketConfiguration.builder().locationConstraint(region).build());
        }

        s3Client.createBucket(createRequest.build());
        log.info("Successfully created S3 bucket '{}' in region '{}'", bucketName, region);
        log.info("BUCKET CREATED: {}", bucketName);
      } catch (Exception createEx) {
        log.error("Error creating S3 bucket '{}': {}", bucketName, createEx.getMessage(), createEx);
        throw new RuntimeException("Failed to create S3 bucket", createEx);
      }
    } catch (S3Exception e) {
      // Handle region mismatch (301 Moved Permanently)
      if (e.statusCode() == 301) {
        log.error(
            "CRITICAL: Bucket '{}' exists in a different region than the configured region '{}'.",
            bucketName,
            awsCredentialsService.getRegion());
        log.error(
            "This happens when switching regions without using region-specific bucket names.");
        log.error("Solution: The bucket name now includes the region to prevent this issue.");
        throw new RuntimeException(
            String.format(
                "Bucket '%s' exists in a different region. Please use a region-specific bucket name.",
                bucketName));
      }
      // For other S3 errors, fail fast to avoid undefined state
      throw new RuntimeException("Failed to ensure S3 bucket exists", e);
    } catch (Exception e) {
      log.error("Unexpected error checking S3 bucket '{}': {}", bucketName, e.getMessage(), e);
      throw new RuntimeException("Failed to ensure S3 bucket exists", e);
    }
  }

  @Override
  public CustomSemanticType save(CustomSemanticType customType) {
    // Add to cache
    cache.put(customType.getSemanticType(), customType);

    // Persist to S3
    if (asyncPersist && scheduler != null) {
      scheduler.execute(
          () -> {
            try {
              persistIndividualToS3(customType);
              log.info("Persisted semantic type '{}' to S3 (async)", customType.getSemanticType());
            } catch (Exception e) {
              log.error(
                  "Failed to persist semantic type '{}' to S3 (async)",
                  customType.getSemanticType(),
                  e);
            }
          });
    } else {
      try {
        persistIndividualToS3(customType);
        log.info("Persisted semantic type '{}' to S3", customType.getSemanticType());
      } catch (Exception e) {
        log.error("Failed to persist to S3", e);
        // Remove from cache on failure to maintain consistency in sync mode
        cache.remove(customType.getSemanticType());
        throw new RuntimeException("Failed to save semantic type", e);
      }
    }

    log.info("Saved semantic type '{}' to S3", customType.getSemanticType());
    return customType;
  }

  @Override
  public Optional<CustomSemanticType> findBySemanticType(String semanticType) {
    if (semanticType == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(cache.get(semanticType));
  }

  @Override
  public boolean existsBySemanticType(String semanticType) {
    if (semanticType == null) {
      return false;
    }
    return cache.containsKey(semanticType);
  }

  @Override
  public boolean deleteBySemanticType(String semanticType) {
    if (semanticType == null) {
      return false;
    }
    CustomSemanticType removed = cache.remove(semanticType);
    if (removed != null) {
      if (asyncPersist && scheduler != null) {
        scheduler.execute(
            () -> {
              try {
                deleteIndividualFromS3(semanticType);
                log.info("Deleted semantic type '{}' from S3 (async)", semanticType);
              } catch (Exception e) {
                log.error("Failed to delete semantic type '{}' from S3 (async)", semanticType, e);
              }
            });
      } else {
        // Delete individual file from S3 synchronously for consistency
        try {
          deleteIndividualFromS3(semanticType);
          log.info("Deleted semantic type '{}' from S3", semanticType);
        } catch (Exception e) {
          log.error("Failed to delete from S3", e);
          // Re-add to cache on failure
          cache.put(semanticType, removed);
          throw new RuntimeException("Failed to delete semantic type", e);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public CustomSemanticType update(String semanticType, CustomSemanticType updatedType) {
    // Store old value for rollback
    CustomSemanticType oldType = cache.get(semanticType);

    // If semantic type changed, we need to delete the old file
    boolean nameChanged = !semanticType.equals(updatedType.getSemanticType());

    // Preserve fields is handled by service layer
    // Update cache
    if (nameChanged) {
      cache.remove(semanticType);
    }
    cache.put(updatedType.getSemanticType(), updatedType);

    if (asyncPersist && scheduler != null) {
      scheduler.execute(
          () -> {
            try {
              persistIndividualToS3(updatedType);
              if (nameChanged) {
                deleteIndividualFromS3(semanticType);
              }
              log.info("Updated semantic type '{}' in S3 (async)", semanticType);
            } catch (Exception e) {
              log.error(
                  "Failed to persist update to S3 (async) for '{}'",
                  updatedType.getSemanticType(),
                  e);
            }
          });
    } else {
      // Persist to S3 synchronously for consistency
      try {
        // Save the new/updated file
        persistIndividualToS3(updatedType);

        // If name changed, delete the old file
        if (nameChanged) {
          deleteIndividualFromS3(semanticType);
        }

        log.info("Updated semantic type '{}' in S3", semanticType);
      } catch (Exception e) {
        log.error("Failed to persist update to S3", e);
        // Rollback on failure in sync mode
        cache.remove(updatedType.getSemanticType());
        if (oldType != null) {
          cache.put(semanticType, oldType);
        }
        throw new RuntimeException("Failed to update semantic type", e);
      }
    }

    return updatedType;
  }

  @Override
  public List<CustomSemanticType> findAll() {
    return new ArrayList<>(cache.values());
  }

  @Override
  public void reload() {
    syncFromS3();
  }

  @Override
  public Map<String, CustomSemanticType> getInternalMap() {
    return new HashMap<>(cache);
  }

  /** Persist individual semantic type to S3. */
  private void persistIndividualToS3(CustomSemanticType semanticType) {
    try {
      String key = semanticTypesPrefix + sanitizeForS3Key(semanticType.getSemanticType()) + ".json";
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(semanticType);

      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .contentType("application/json")
              .build(),
          RequestBody.fromString(json, StandardCharsets.UTF_8));

      log.debug(
          "Persisted semantic type '{}' to S3 at key '{}'", semanticType.getSemanticType(), key);

    } catch (Exception e) {
      log.error("Failed to persist semantic type '{}' to S3", semanticType.getSemanticType(), e);
      throw new RuntimeException("Failed to save to S3", e);
    }
  }

  /** Delete individual semantic type from S3. */
  private void deleteIndividualFromS3(String semanticType) {
    try {
      String key = semanticTypesPrefix + sanitizeForS3Key(semanticType) + ".json";

      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());

      log.debug("Deleted semantic type '{}' from S3 at key '{}'", semanticType, key);

    } catch (Exception e) {
      log.error("Failed to delete semantic type '{}' from S3", semanticType, e);
      throw new RuntimeException("Failed to delete from S3", e);
    }
  }

  /** Sync cache from S3. */
  private void syncFromS3() {
    try {
      // Skip if not properly initialized
      if (!initialized || s3Client == null) {
        log.debug("S3 repository not properly initialized, skipping sync");
        return;
      }

      // List all semantic type files
      ListObjectsV2Request listRequest =
          ListObjectsV2Request.builder().bucket(bucketName).prefix(semanticTypesPrefix).build();

      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

      if (listResponse.contents().isEmpty()) {
        log.info("No semantic types found in S3 - starting with empty repository");
        cache.clear();
        return;
      }

      log.debug("Found {} semantic type files in S3", listResponse.contents().size());

      // Clear cache and reload all types
      Map<String, CustomSemanticType> newCache = new ConcurrentHashMap<>();

      for (S3Object s3Object : listResponse.contents()) {
        if (s3Object.key().endsWith(".json")) {
          try {
            // Download individual file
            byte[] data =
                s3Client
                    .getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build())
                    .asByteArray();

            // Parse JSON
            CustomSemanticType type = objectMapper.readValue(data, CustomSemanticType.class);
            // createdAt consistency is ensured in service/conversion layers

            // Ensure valid priority
            if (type.getPriority() == null || type.getPriority() < 2000) {
              log.warn(
                  "Semantic type '{}' has invalid priority {}, setting to 2000",
                  type.getSemanticType(),
                  type.getPriority());
              type.setPriority(2000);
            }

            newCache.put(type.getSemanticType(), type);
          } catch (Exception e) {
            log.error(
                "Failed to load semantic type from key '{}': {}", s3Object.key(), e.getMessage());
          }
        }
      }

      // Replace cache atomically
      cache.clear();
      cache.putAll(newCache);

      log.info("Successfully loaded {} semantic types from S3", cache.size());

    } catch (Exception e) {
      log.error("Failed to sync semantic types from S3: {}", e.getMessage(), e);
      // Don't clear cache on error - keep working with what we have
    }
  }

  /**
   * Sanitize semantic type name for use as S3 key. Replaces problematic characters with safe
   * alternatives.
   */
  private String sanitizeForS3Key(String semanticType) {
    return semanticType
        .replace("/", "_SLASH_")
        .replace("\\", "_BACKSLASH_")
        .replace(":", "_COLON_")
        .replace("*", "_STAR_")
        .replace("?", "_QUESTION_")
        .replace("\"", "_QUOTE_")
        .replace("<", "_LT_")
        .replace(">", "_GT_")
        .replace("|", "_PIPE_");
  }

  private String getAwsAccountId() {
    try {
      // Try to get account ID from STS
      software.amazon.awssdk.services.sts.StsClient stsClient =
          software.amazon.awssdk.services.sts.StsClient.builder()
              .region(Region.of(awsCredentialsService.getRegion()))
              .credentialsProvider(awsCredentialsService.getCredentialsProvider())
              .build();

      String accountId = stsClient.getCallerIdentity().account();
      stsClient.close();
      return accountId;
    } catch (Exception e) {
      log.warn("Could not retrieve AWS account ID: {}", e.getMessage());
      return null;
    }
  }
}

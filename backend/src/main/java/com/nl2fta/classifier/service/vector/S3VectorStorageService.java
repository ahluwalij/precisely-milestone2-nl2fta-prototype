package com.nl2fta.classifier.service.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Service for storing and retrieving vector embeddings in AWS S3. Stores vectors organized by
 * semantic type for efficient retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3VectorStorageService {

  private final ObjectMapper objectMapper;
  private final AwsCredentialsService awsCredentialsService;

  @Value("${aws.s3.vector-bucket:}")
  private String configuredBucketName;

  private String bucketName;

  // Changed to organize vectors by semantic type
  private final String vectorPrefix = "vectors/";

  @Value("${aws.region:us-east-1}")
  private String awsRegion;

  private S3Client s3Client;

  @PostConstruct
  public void init() {
    // Wait for AWS credentials from frontend
    log.debug("S3VectorStorageService initialized - waiting for AWS credentials from frontend");
  }

  /**
   * Initialize S3 client when AWS credentials are provided. Called by HybridRepository when user
   * connects AWS.
   */
  public void initializeS3Client() {
    // Generate bucket name based on AWS account ID and region
    if (configuredBucketName == null || configuredBucketName.trim().isEmpty()) {
      // Get AWS account ID and region from credentials
      String accountId = getAwsAccountId();
      String region = awsCredentialsService.getRegion();

      if (accountId != null) {
        // Use account ID and region to create unique bucket name
        bucketName = "nl2fta-vector-storage-" + accountId + "-" + region;
        log.info("Generated account and region-specific bucket name: {}", bucketName);
      } else {
        // Fallback to timestamp with region if can't get account ID
        String timestamp = String.valueOf(System.currentTimeMillis());
        bucketName =
            "nl2fta-vector-storage-" + timestamp.substring(timestamp.length() - 8) + "-" + region;
        log.info("Generated unique bucket name with timestamp and region: {}", bucketName);
      }
    } else {
      bucketName = configuredBucketName;
      log.info("Using configured bucket name: {}", bucketName);
    }

    if (!awsCredentialsService.areCredentialsAvailable()) {
      log.warn("Cannot initialize S3 client - AWS credentials not available");
      return;
    }

    try {
      // Initialize S3 client with frontend-provided credentials
      this.s3Client =
          S3Client.builder()
              .region(Region.of(awsCredentialsService.getRegion()))
              .credentialsProvider(awsCredentialsService.getCredentialsProvider())
              .build();

      // Ensure bucket exists
      log.info(
          "BUCKET NAME TO BE USED: {} (Region: {})", bucketName, awsCredentialsService.getRegion());
      ensureBucketExists();

      log.info("S3VectorStorageService initialized successfully with bucket: {}", bucketName);
    } catch (Exception e) {
      log.error("Failed to initialize S3 client", e);
      this.s3Client = null;
    }
  }

  /** Disconnect S3 and revert to in-memory storage. */
  public void disconnectS3() {
    if (s3Client != null) {
      try {
        s3Client.close();
        s3Client = null;
        log.info("S3 client disconnected - using in-memory storage");
      } catch (Exception e) {
        log.error("Error closing S3 client", e);
      }
    }
  }

  private void ensureBucketExists() {
    if (s3Client == null) {
      return;
    }

    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      log.debug("S3 bucket {} exists", bucketName);
    } catch (NoSuchBucketException e) {
      log.info("Creating S3 bucket: {}", bucketName);
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
        log.error("Error creating S3 bucket", createEx);
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

      // If we get a 403, the bucket might not exist and we don't have permission to check
      // Try to create it anyway
      log.warn(
          "Got S3 exception checking bucket: {}. Attempting to create bucket...", e.getMessage());
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
        log.info(
            "Successfully created S3 bucket '{}' in region '{}' after error", bucketName, region);
        log.info("BUCKET CREATED: {}", bucketName);
      } catch (BucketAlreadyOwnedByYouException ex) {
        // This is fine - the bucket exists and we own it
        log.info("Bucket '{}' already exists and is owned by you", bucketName);
      } catch (Exception createEx) {
        log.error(
            "Error creating S3 bucket '{}' after error: {}",
            bucketName,
            createEx.getMessage(),
            createEx);
        throw new RuntimeException("Failed to create S3 bucket", createEx);
      }
    } catch (Exception e) {
      log.error("Error checking S3 bucket", e);
      throw new RuntimeException("Failed to ensure S3 bucket exists", e);
    }
  }

  /**
   * Store a vector embedding with metadata in S3. Organizes vectors by semantic type for efficient
   * deletion.
   *
   * @param vectorData The vector data to store
   */
  public void storeVector(VectorData vectorData) {
    // Clean design: single object per semantic type
    // Key format: vectors/{semantic_type}.json
    String sanitizedSemanticType = sanitizeForS3Key(vectorData.getSemanticType());
    String singleKey = vectorPrefix + sanitizedSemanticType + ".json";
    String legacyTypePrefix =
        vectorPrefix + sanitizedSemanticType + "/"; // legacy folder-based layout

    try {
      String json = objectMapper.writeValueAsString(vectorData);

      if (s3Client != null) {
        // Store/overwrite single object per type (no legacy cleanup)
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(singleKey)
                .contentType("application/json")
                .build(),
            RequestBody.fromString(json));
        log.info(
            "Stored vector for semantic type: {} at key: {}",
            vectorData.getSemanticType(),
            singleKey);
      } else {
        // Fallback to in-memory storage
        inMemoryStorage.put(singleKey, vectorData);
        log.debug(
            "Stored vector for semantic type: {} in memory at key: {}",
            vectorData.getSemanticType(),
            singleKey);
      }

    } catch (Exception e) {
      log.error("Error storing vector for semantic type: {}", vectorData.getSemanticType(), e);
      throw new RuntimeException("Failed to store vector", e);
    }
  }

  /**
   * Check if any vectors exist in storage without downloading them.
   *
   * @return true if at least one vector exists
   */
  public boolean hasAnyVectors() {
    try {
      if (s3Client != null) {
        // Just list one object to check if any exist
        ListObjectsV2Request listRequest =
            ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(vectorPrefix)
                .maxKeys(1)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
        return !response.contents().isEmpty();
      } else {
        // Check in-memory storage
        return !inMemoryStorage.isEmpty();
      }
    } catch (Exception e) {
      log.error("Error checking for vectors", e);
      return false;
    }
  }

  /**
   * Get count of vectors in storage without downloading them. This is much faster than
   * getAllVectors() for large vector sets.
   *
   * @return count of vectors in storage
   */
  public int getVectorCount() {
    try {
      if (s3Client != null) {
        int count = 0;
        String continuationToken = null;

        do {
          ListObjectsV2Request.Builder requestBuilder =
              ListObjectsV2Request.builder().bucket(bucketName).prefix(vectorPrefix);

          if (continuationToken != null) {
            requestBuilder.continuationToken(continuationToken);
          }

          ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

          // Count only JSON files
          count += response.contents().stream().filter(obj -> obj.key().endsWith(".json")).count();

          continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return count;
      } else {
        // Count in-memory storage
        return inMemoryStorage.size();
      }
    } catch (Exception e) {
      log.error("Error counting vectors", e);
      return 0;
    }
  }

  /**
   * Retrieve all vectors from S3.
   *
   * @return List of all stored vector data
   */
  public List<VectorData> getAllVectors() {
    List<VectorData> vectors = new ArrayList<>();

    try {
      if (s3Client != null) {
        // List all objects with the vector prefix (flat: one file per type)
        ListObjectsV2Request listRequest =
            ListObjectsV2Request.builder().bucket(bucketName).prefix(vectorPrefix).build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

        // Retrieve each vector (ignore non-json)
        for (S3Object s3Object : response.contents()) {
          if (s3Object.key().endsWith(".json")) {
            VectorData vectorData = getVector(s3Object.key());
            if (vectorData != null) {
              vectors.add(vectorData);
            }
          }
        }

        log.info("Retrieved {} vectors from S3", vectors.size());
      } else {
        // Fallback to in-memory storage (flat)
        vectors.addAll(inMemoryStorage.values());
        log.debug("Retrieved {} vectors from memory", vectors.size());
      }

      return vectors;

    } catch (Exception e) {
      log.error("Error retrieving vectors", e);
      throw new RuntimeException("Failed to retrieve vectors", e);
    }
  }

  /**
   * Retrieve a specific vector by key.
   *
   * @param key The S3 key
   * @return The vector data or null if not found
   */
  private VectorData getVector(String key) {
    try {
      if (s3Client != null) {
        // Get from S3
        GetObjectRequest getRequest =
            GetObjectRequest.builder().bucket(bucketName).key(key).build();

        byte[] data = s3Client.getObjectAsBytes(getRequest).asByteArray();
        return objectMapper.readValue(data, VectorData.class);
      } else {
        // Get from in-memory storage
        return inMemoryStorage.get(key);
      }

    } catch (NoSuchKeyException e) {
      log.debug("Vector not found at key: {}", key);
      return null;
    } catch (Exception e) {
      log.error("Error retrieving vector at key: {}", key, e);
      return null;
    }
  }

  /**
   * Delete all vectors for a semantic type. Now efficient because vectors are organized by semantic
   * type.
   *
   * @param semanticType The semantic type to delete
   */
  public void deleteVector(String semanticType) {
    try {
      if (s3Client != null) {
        // Delete single-object layout only
        String sanitized = sanitizeForS3Key(semanticType);
        String singleKey = vectorPrefix + sanitized + ".json";
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucketName).key(singleKey).build());
        log.info("Deleted vector for semantic type: {} (key: {})", semanticType, singleKey);
      } else {
        // Delete from in-memory storage - find vectors with matching semantic type
        final int[] deletedCount = {0};
        inMemoryStorage
            .entrySet()
            .removeIf(
                entry -> {
                  VectorData vectorData = entry.getValue();
                  if (semanticType.equals(vectorData.getSemanticType())) {
                    deletedCount[0]++;
                    return true;
                  }
                  return false;
                });

        if (deletedCount[0] > 0) {
          log.debug(
              "Deleted {} vectors for semantic type: {} from memory",
              deletedCount[0],
              semanticType);
        }
      }

    } catch (Exception e) {
      log.error("Error deleting vectors for semantic type: {}", semanticType, e);
      throw new RuntimeException("Failed to delete vectors", e);
    }
  }

  /** Clear all vectors from storage. */
  public void clearAllVectors() {
    try {
      if (s3Client != null) {
        // List and delete all vectors from S3
        ListObjectsV2Request listRequest =
            ListObjectsV2Request.builder().bucket(bucketName).prefix(vectorPrefix).build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
        int count = response.contents().size();

        // Delete each object
        for (S3Object s3Object : response.contents()) {
          s3Client.deleteObject(
              DeleteObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build());
        }

        log.info("Cleared {} vectors from S3 storage", count);
      } else {
        // Clear in-memory storage
        int count = inMemoryStorage.size();
        inMemoryStorage.clear();
        log.info("Cleared {} vectors from memory storage", count);
      }

    } catch (Exception e) {
      log.error("Error clearing vectors from storage", e);
      throw new RuntimeException("Failed to clear vectors", e);
    }
  }

  /** Generate a unique ID for a vector based on semantic type. */
  public String generateVectorId(String semanticType) {
    // With single-object-per-type design, id is just the sanitized semantic type
    if (semanticType == null || semanticType.trim().isEmpty()) {
      return "";
    }
    return semanticType.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  /**
   * Sanitize semantic type name for use as S3 key prefix. Replaces problematic characters with safe
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
        .replace("|", "_PIPE_")
        .replace(" ", "_");
  }

  // In-memory storage as fallback
  private final Map<String, VectorData> inMemoryStorage = new ConcurrentHashMap<>();

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

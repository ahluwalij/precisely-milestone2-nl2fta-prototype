package com.nl2fta.classifier.service.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.vector.S3VectorStorageService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid repository that switches between file-based and S3-based storage based on AWS credentials
 * availability.
 */
@Slf4j
@Repository
@Primary
public class HybridCustomSemanticTypeRepository implements ICustomSemanticTypeRepository {

  private final FileBasedCustomSemanticTypeRepository fileBasedRepository;
  private final AwsCredentialsService awsCredentialsService;
  private final ObjectMapper objectMapper;
  private final S3VectorStorageService s3VectorStorageService;
  private final S3CustomSemanticTypeRepository s3Repository; // Injected by Spring
  private boolean s3Initialized = false;

  @Autowired
  public HybridCustomSemanticTypeRepository(
      FileBasedCustomSemanticTypeRepository fileBasedRepository,
      AwsCredentialsService awsCredentialsService,
      ObjectMapper objectMapper,
      S3VectorStorageService s3VectorStorageService,
      S3CustomSemanticTypeRepository s3Repository) {
    this.fileBasedRepository = fileBasedRepository;
    this.awsCredentialsService = awsCredentialsService;
    this.objectMapper = objectMapper;
    this.s3VectorStorageService = s3VectorStorageService;
    this.s3Repository = s3Repository;

    // Don't initialize S3 on startup - wait for frontend credentials
  }

  @PostConstruct
  public void init() {
    // Start with file-based storage. Do NOT auto-initialize S3 at startup.
    // S3 should ONLY be initialized after the user supplies credentials via the frontend.
    log.debug(
        "Hybrid repository initialized with file-based storage - S3 will be available after AWS connection");
  }

  /**
   * Initialize S3 repository when AWS credentials are provided from frontend. This is called by the
   * AWS controller when user connects.
   */
  public void initializeS3Repository() {
    log.info("Attempting to initialize S3 repository...");

    boolean credentialsAvailable = awsCredentialsService.areCredentialsAvailable();
    log.info("AWS credentials available: {}", credentialsAvailable);

    if (credentialsAvailable) {
      try {
        log.info("Initializing S3 repository with credentials...");

        // Initialize with credentials
        this.s3Repository.initializeWithCredentials();
        log.info("S3 repository credentials initialized");

        // Also initialize S3 vector storage
        this.s3VectorStorageService.initializeS3Client();
        log.info("S3 vector storage initialized");

        this.s3Initialized = true;
        log.info("S3 repository initialized successfully - using S3-based storage");

        // Try to load types to verify connection works
        try {
          int typeCount = this.s3Repository.findAll().size();
          log.info("S3 repository verification: found {} custom types", typeCount);
        } catch (Exception verifyError) {
          log.warn("S3 repository verification failed", verifyError);
        }

      } catch (Exception e) {
        log.error(
            "Failed to initialize S3 repository despite credentials being available, falling back to file-based storage",
            e);
        this.s3Initialized = false;
      }
    } else {
      log.warn("Cannot initialize S3 repository - AWS credentials not available");
      this.s3Initialized = false;
    }
  }

  /**
   * Disconnect S3 and revert to file-based storage. Called when user disconnects AWS through
   * frontend. Also clears local custom types since they should only be available with AWS.
   */
  public void disconnectS3() {
    if (s3Initialized) {
      try {
        // Clean up S3 resources
        s3Repository.cleanup();

        // Also disconnect S3 vector storage
        s3VectorStorageService.disconnectS3();

        // Clear local custom types since they should only be available with AWS
        clearLocalCustomTypes();

        log.info("S3 repository disconnected - awaiting new AWS credentials");
      } catch (Exception e) {
        log.error("Error disconnecting S3 repository", e);
      } finally {
        // Always set s3Initialized to false, even if cleanup fails
        s3Initialized = false;
      }
    }
  }

  /** Get the active repository based on AWS credentials availability. */
  private ICustomSemanticTypeRepository getActiveRepository() {
    // Don't auto-initialize - only use S3 if explicitly connected through frontend
    // Return S3 repository if initialized, otherwise file-based
    return s3Initialized ? s3Repository : fileBasedRepository;
  }

  /** Check if we're currently using S3 storage. */
  public boolean isUsingS3Storage() {
    return s3Initialized && awsCredentialsService.areCredentialsAvailable();
  }

  /** Check if we're currently using file-based storage. */
  public boolean isUsingFileStorage() {
    return !isUsingS3Storage();
  }

  @Override
  public CustomSemanticType save(CustomSemanticType customType) {
    // Fallback to file-based storage when S3 is not available
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    log.debug(
        "Saving semantic type '{}' using {} repository",
        customType.getSemanticType(),
        activeRepo == s3Repository ? "S3" : "file-based");
    return activeRepo.save(customType);
  }

  @Override
  public Optional<CustomSemanticType> findBySemanticType(String semanticType) {
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    return activeRepo.findBySemanticType(semanticType);
  }

  @Override
  public boolean existsBySemanticType(String semanticType) {
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    return activeRepo.existsBySemanticType(semanticType);
  }

  @Override
  public boolean deleteBySemanticType(String semanticType) {
    // Fallback to file-based storage when S3 is not available
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    log.debug(
        "Deleting semantic type '{}' using {} repository",
        semanticType,
        activeRepo == s3Repository ? "S3" : "file-based");
    return activeRepo.deleteBySemanticType(semanticType);
  }

  @Override
  public CustomSemanticType update(String semanticType, CustomSemanticType updatedType) {
    // Only allow updates when AWS is connected
    if (!isUsingS3Storage()) {
      log.warn(
          "Cannot update semantic type '{}' - AWS not connected, using file-based repo",
          semanticType);
      return fileBasedRepository.update(semanticType, updatedType);
    }
    ICustomSemanticTypeRepository activeRepo = getActiveRepository();
    log.debug(
        "Updating semantic type '{}' using {} repository",
        semanticType,
        activeRepo == s3Repository ? "S3" : "file-based");
    return activeRepo.update(semanticType, updatedType);
  }

  @Override
  public List<CustomSemanticType> findAll() {
    log.debug(
        "HybridRepository.findAll() called - s3Initialized: {}, awsCredentialsAvailable: {}",
        s3Initialized,
        awsCredentialsService.areCredentialsAvailable());
    try {
      ICustomSemanticTypeRepository activeRepo =
          isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
      List<CustomSemanticType> types = activeRepo.findAll();
      log.info("Retrieved {} custom semantic types from S3", types.size());
      return types;
    } catch (Exception e) {
      log.error("Error retrieving custom types from S3", e);
      return List.of();
    }
  }

  @Override
  public void reload() {
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    activeRepo.reload();
    log.info(
        "Reloaded semantic types using {} repository", isUsingS3Storage() ? "S3" : "file-based");
  }

  @Override
  public Map<String, CustomSemanticType> getInternalMap() {
    ICustomSemanticTypeRepository activeRepo =
        isUsingS3Storage() ? getActiveRepository() : fileBasedRepository;
    return activeRepo.getInternalMap();
  }

  /**
   * Clear any cached custom types. Called when AWS credentials are disconnected to ensure custom
   * types are only available when AWS is connected.
   */
  private void clearLocalCustomTypes() {
    try {
      // Get all semantic types and delete them one by one
      List<String> semanticTypesToDelete =
          fileBasedRepository.findAll().stream().map(CustomSemanticType::getSemanticType).toList();

      for (String semanticType : semanticTypesToDelete) {
        fileBasedRepository.deleteBySemanticType(semanticType);
      }

      log.info("Cleared {} cached custom types", semanticTypesToDelete.size());
    } catch (Exception e) {
      log.error("Error clearing local custom types", e);
    }
  }

  /** Get storage status information for debugging/monitoring. */
  public String getStorageStatus() {
    boolean credentialsAvailable = awsCredentialsService.areCredentialsAvailable();

    if (credentialsAvailable && s3Initialized) {
      return "Using S3 storage";
    } else if (credentialsAvailable && !s3Initialized) {
      return "AWS credentials available but S3 repository not yet initialized";
    } else {
      return "Awaiting AWS credentials";
    }
  }
}

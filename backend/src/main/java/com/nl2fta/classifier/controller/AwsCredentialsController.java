package com.nl2fta.classifier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Controller for managing AWS credentials and checking storage status. */
@Slf4j
@RestController
@RequestMapping("/api/aws/credentials")
@RequiredArgsConstructor
@Tag(name = "AWS Credentials", description = "AWS credentials and storage configuration")
public class AwsCredentialsController {

  private final AwsCredentialsService awsCredentialsService;
  private final HybridCustomSemanticTypeRepository hybridRepository;
  private final VectorIndexInitializationService vectorIndexService;

  @GetMapping("/status")
  @Operation(
      summary = "Get AWS credentials and storage status",
      description = "Check if AWS credentials are available and what storage is being used",
      responses = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
      })
  public ResponseEntity<AwsStatusResponse> getStatus() {
    boolean credentialsAvailable = awsCredentialsService.areCredentialsAvailable();
    String storageStatus = hybridRepository.getStorageStatus();
    boolean usingS3 = hybridRepository.isUsingS3Storage();

    log.debug(
        "AWS Status check - Credentials: {}, Storage: {}", credentialsAvailable, storageStatus);

    AwsStatusResponse.AwsStatusResponseBuilder responseBuilder =
        AwsStatusResponse.builder()
            .credentialsAvailable(credentialsAvailable)
            .storageType(usingS3 ? "S3" : "NONE")
            .storageStatus(storageStatus)
            .canAccessS3(credentialsAvailable)
            .region(awsCredentialsService.getRegion())
            .message(
                credentialsAvailable
                    ? "AWS credentials are configured. Full functionality available."
                    : "AWS credentials not configured. Only default semantic types available.");

    // Never return raw credentials in API responses

    return ResponseEntity.ok(responseBuilder.build());
  }

  @GetMapping("/indexing-status")
  @Operation(
      summary = "Get vector indexing status",
      description = "Check if vector indexing is in progress",
      responses = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
      })
  public ResponseEntity<IndexingStatusResponse> getIndexingStatus() {
    boolean isIndexing = vectorIndexService.isIndexing();
    int totalTypes = vectorIndexService.getTotalTypesToIndex();
    int indexedTypes = vectorIndexService.getIndexedTypesCount();

    return ResponseEntity.ok(
        IndexingStatusResponse.builder()
            .indexing(isIndexing)
            .totalTypes(totalTypes)
            .indexedTypes(indexedTypes)
            .progress(totalTypes > 0 ? (double) indexedTypes / totalTypes : 0.0)
            .build());
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "AWS status response")
  public static class AwsStatusResponse {
    @Schema(description = "Whether AWS credentials are available")
    private boolean credentialsAvailable;

    @Schema(description = "Current storage type", example = "S3 or NONE")
    private String storageType;

    @Schema(description = "Detailed storage status")
    private String storageStatus;

    @Schema(description = "Whether the system can access S3")
    private boolean canAccessS3;

    @Schema(description = "Status message")
    private String message;

    @Schema(description = "Current AWS region")
    private String region;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Credentials check response")
  public static class CredentialsCheckResponse {
    @Schema(description = "Whether credentials are available")
    private boolean available;

    @Schema(description = "Check result message")
    private String message;

    @Schema(description = "Timestamp when check was performed")
    private long checkedAt;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Vector indexing status response")
  public static class IndexingStatusResponse {
    @Schema(description = "Whether indexing is currently in progress")
    private boolean indexing;

    @Schema(description = "Total number of types to index")
    private int totalTypes;

    @Schema(description = "Number of types already indexed")
    private int indexedTypes;

    @Schema(description = "Progress percentage (0.0 to 1.0)")
    private double progress;
  }
}

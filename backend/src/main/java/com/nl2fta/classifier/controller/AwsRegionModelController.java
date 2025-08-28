package com.nl2fta.classifier.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.dto.AwsCredentialsRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.AwsRegionModelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Controller for AWS region management - simplified for Claude Sonnet 4.0 only. */
@Slf4j
@RestController
@RequestMapping("/api/aws")
@RequiredArgsConstructor
@Tag(
    name = "AWS Management",
    description = "AWS credentials and region management for Claude Sonnet 4.0")
public class AwsRegionModelController {

  private final AwsRegionModelService awsRegionModelService;
  private final AwsBedrockService awsBedrockService;
  private final com.nl2fta.classifier.service.aws.AwsCredentialsService awsCredentialsService;
  private final com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository
      hybridRepository;
  private final com.nl2fta.classifier.service.vector.VectorIndexInitializationService
      vectorIndexService;
  private final com.nl2fta.classifier.service.TableClassificationService classificationService;
  private final com.nl2fta.classifier.service.storage.AnalysisStorageService analysisStorageService;

  @Value("${aws.bedrock.claude.model-id}")
  private String claudeModelId;

  @Value("${aws.bedrock.embedding.model-id}")
  private String embeddingModelId;

  private static final String CLAUDE_MODEL_NAME = "Claude Sonnet 4.0";
  private static final String TITAN_EMBEDDING_MODEL_NAME = "Titan Text Embeddings V2";

  /**
   * Validates AWS credentials and returns available regions. This endpoint ONLY validates
   * credentials, not model access.
   */
  @PostMapping("/validate-credentials")
  @Operation(
      summary = "Validate AWS credentials and get available regions",
      description =
          "Validates the provided AWS credentials and returns a list of available Bedrock regions")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Credentials validated successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid AWS credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Map<String, Object>> validateAndGetRegions(
      @Valid @RequestBody AwsCredentialsRequest request) {
    log.info("Validating AWS credentials");

    // Use a default region just for credential validation
    String validationRegion = "us-east-1";

    // Only validate basic credentials - can they access AWS Bedrock at all?
    boolean isValid =
        awsRegionModelService.validateCredentials(
            request.getAccessKeyId(), request.getSecretAccessKey(), validationRegion);

    if (!isValid) {
      Map<String, Object> response = new HashMap<>();
      response.put("valid", false);
      response.put("message", "Invalid AWS credentials");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Get available regions for the user to choose from
    List<AwsRegionModelService.RegionInfo> regions =
        awsRegionModelService.getAvailableBedrockRegions(
            request.getAccessKeyId(), request.getSecretAccessKey());

    Map<String, Object> response = new HashMap<>();
    response.put("valid", true);
    response.put("regions", regions);
    response.put(
        "message", "Credentials validated successfully. Please select a region to continue.");
    response.put("modelId", claudeModelId);
    response.put("modelName", CLAUDE_MODEL_NAME);
    response.put("embeddingModelId", embeddingModelId);
    response.put("embeddingModelName", TITAN_EMBEDDING_MODEL_NAME);

    return ResponseEntity.ok(response);
  }

  /** Gets Claude Sonnet 4.0 model info for any region. */
  @PostMapping("/models/{region}")
  @Operation(
      summary = "Get Claude Sonnet 4.0 model for a region",
      description = "Returns Claude Sonnet 4.0 model information for the specified AWS region")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Model information retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Invalid AWS credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Map<String, Object>> getModelsForRegion(
      @Valid @RequestBody AwsCredentialsRequest request,
      @Parameter(description = "AWS region ID", required = true) @PathVariable String region) {

    log.info("Getting Claude Sonnet 4.0 model for region: {}", region);

    // First validate credentials
    boolean isValid =
        awsRegionModelService.validateCredentials(
            request.getAccessKeyId(), request.getSecretAccessKey());

    if (!isValid) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "Invalid AWS credentials");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    List<AwsRegionModelService.ModelInfo> models =
        awsRegionModelService.getAvailableModels(
            request.getAccessKeyId(), request.getSecretAccessKey(), region);

    Map<String, Object> response = new HashMap<>();
    response.put("region", region);
    response.put("models", models);
    response.put("count", models.size());

    return ResponseEntity.ok(response);
  }

  /** Configures the AWS Bedrock client for Claude Sonnet 4.0. */
  @PostMapping("/configure")
  @Operation(
      summary = "Configure AWS Bedrock client for Claude Sonnet 4.0",
      description =
          "Configures the AWS Bedrock client with the provided credentials for Claude Sonnet 4.0")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Client configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Invalid AWS credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Map<String, Object>> configureClient(
      @Valid @RequestBody AwsCredentialsRequest request) {
    log.info(
        "Configuring AWS Bedrock client for Claude Sonnet 4.0 in region: {}",
        request.getRegion() != null ? request.getRegion() : "us-east-1");

    try {
      String targetRegion = request.getRegion() != null ? request.getRegion() : "us-east-1";

      // Validate credentials first
      boolean isValid =
          awsRegionModelService.validateCredentials(
              request.getAccessKeyId(), request.getSecretAccessKey(), targetRegion);

      if (!isValid) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Invalid AWS credentials for region " + targetRegion);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
      }

      // Validate Claude model access
      boolean hasClaudeAccess =
          awsRegionModelService.validateModelAccess(
              request.getAccessKeyId(), request.getSecretAccessKey(), targetRegion, claudeModelId);

      if (!hasClaudeAccess) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put(
            "message",
            "No access to Claude Sonnet 4.0 in region "
                + targetRegion
                + ". Please ensure you have requested model access in this region.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
      }

      // Validate Titan embedding model access
      boolean hasEmbeddingAccess =
          awsRegionModelService.validateModelAccess(
              request.getAccessKeyId(),
              request.getSecretAccessKey(),
              targetRegion,
              embeddingModelId);

      if (!hasEmbeddingAccess) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put(
            "message",
            "No access to Titan Text Embeddings V2 in region "
                + targetRegion
                + ". Please ensure you have requested model access for both Claude Sonnet 4.0 and Titan Text Embeddings V2.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
      }

      // Configure the Bedrock client (no model ID override - always Claude Sonnet 4.0)
      awsBedrockService.initializeClient(
          request.getAccessKeyId(), request.getSecretAccessKey(), request.getRegion());

      // Store credentials in AwsCredentialsService for other services to use
      boolean credentialsSet =
          awsCredentialsService.setCredentials(
              request.getAccessKeyId(), request.getSecretAccessKey(), targetRegion);

      if (credentialsSet) {
        // Initialize S3 repository (migration and sync handled inside repository)
        hybridRepository.initializeS3Repository();

        // Start vector indexing (which also initializes VectorEmbeddingService)
        vectorIndexService.initializeAfterAwsConnection();

        log.info("AWS services initialized after successful configuration");

        // Reanalyze all stored analyses using combined mode (built-ins + available customs)
        try {
          int updated = reanalyzeAllStoredAnalyses(true);
          log.info("Reanalyzed {} stored analyses after AWS connection", updated);
        } catch (Exception reEx) {
          log.warn(
              "Failed to reanalyze stored analyses after AWS connection: {}", reEx.getMessage());
        }
      }

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put(
          "message",
          "AWS Bedrock client configured successfully with access to both Claude Sonnet 4.0 and Titan Text Embeddings V2");
      response.put("region", request.getRegion() != null ? request.getRegion() : "us-east-1");
      response.put("modelId", claudeModelId);
      response.put("modelName", CLAUDE_MODEL_NAME);
      response.put("embeddingModelId", embeddingModelId);
      response.put("embeddingModelName", TITAN_EMBEDDING_MODEL_NAME);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("Failed to configure AWS Bedrock client", e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", "Failed to configure client: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
  }

  /** Clears the current AWS credentials configuration. */
  @DeleteMapping("/credentials")
  @Operation(
      summary = "Clear AWS credentials",
      description = "Clears the currently configured AWS credentials from the Bedrock client")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Credentials cleared successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Map<String, Object>> clearCredentials() {
    log.info("Clearing AWS credentials");

    try {
      // Clear Bedrock service
      awsBedrockService.clearCredentials();

      // Clear AWS credentials service
      awsCredentialsService.clearCredentials();

      // Disconnect S3 storage - revert to built-in types only
      hybridRepository.disconnectS3();

      // Reanalyze all stored analyses using built-ins only (no custom repository)
      try {
        int updated = reanalyzeAllStoredAnalyses(false);
        log.info("Reanalyzed {} stored analyses after AWS logout", updated);
      } catch (Exception reEx) {
        log.warn("Failed to reanalyze stored analyses after AWS logout: {}", reEx.getMessage());
      }

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "AWS credentials cleared successfully");

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Failed to clear AWS credentials", e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", "Failed to clear credentials: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
  }

  /** Gets the current AWS configuration status. */
  @GetMapping("/status")
  @Operation(
      summary = "Get AWS configuration status",
      description =
          "Returns the current AWS Bedrock client configuration status for Claude Sonnet 4.0")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Status retrieved successfully")})
  public ResponseEntity<Map<String, Object>> getConfigurationStatus() {
    Map<String, Object> response = new HashMap<>();
    boolean isConfigured = awsBedrockService.isInitialized();
    response.put("configured", isConfigured);

    if (isConfigured) {
      response.put("region", awsBedrockService.getCurrentRegion());
      response.put("modelId", awsBedrockService.getCurrentModelId());
      response.put("modelName", CLAUDE_MODEL_NAME);
    } else {
      response.put("modelId", claudeModelId);
      response.put("modelName", CLAUDE_MODEL_NAME);
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Reanalyzes all stored analyses using the current semantic type set. Invoked on AWS
   * connect/disconnect so frontend state stays in sync.
   */
  private int reanalyzeAllStoredAnalyses(boolean useAllSemanticTypes) {
    var analyses = analysisStorageService.getAllAnalyses();
    int updated = 0;
    if (analyses == null || analyses.isEmpty()) {
      return 0;
    }

    for (var stored : analyses) {
      try {
        var req = new com.nl2fta.classifier.dto.analysis.TableClassificationRequest();
        req.setTableName(stored.getFileName());
        req.setColumns(stored.getColumns());
        req.setData(stored.getData());
        req.setIncludeStatistics(true);
        String locale = stored.getLocale();
        if (locale == null || locale.trim().isEmpty()) {
          locale = "en-US";
        }
        req.setLocale(locale);
        // Toggle mode based on AWS state
        req.setUseAllSemanticTypes(useAllSemanticTypes);
        req.setCustomOnly(false);

        var resp = classificationService.classifyTable(req);
        resp.setAnalysisId(stored.getAnalysisId());
        analysisStorageService.updateAnalysis(stored.getAnalysisId(), resp);
        updated++;
      } catch (Exception e) {
        log.warn(
            "Failed to reanalyze stored analysis {}: {}", stored.getAnalysisId(), e.getMessage());
      }
    }
    return updated;
  }

  /** Validates access to Claude Sonnet 4.0 model. */
  @PostMapping("/validate-model/{region}")
  @Operation(
      summary = "Validate access to Claude Sonnet 4.0",
      description =
          "Checks if the user has access to invoke Claude Sonnet 4.0 in the specified region")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Validation completed"),
        @ApiResponse(responseCode = "401", description = "Invalid AWS credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Map<String, Object>> validateModelAccess(
      @Valid @RequestBody AwsCredentialsRequest request,
      @Parameter(description = "AWS region ID", required = true) @PathVariable String region) {

    log.info("Validating access to Claude Sonnet 4.0 in region: {}", region);

    // First validate credentials
    boolean credentialsValid =
        awsRegionModelService.validateCredentials(
            request.getAccessKeyId(), request.getSecretAccessKey());

    if (!credentialsValid) {
      Map<String, Object> response = new HashMap<>();
      response.put("accessible", false);
      response.put("message", "Invalid AWS credentials");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Actually validate model access
    boolean hasAccess =
        awsRegionModelService.validateModelAccess(
            request.getAccessKeyId(), request.getSecretAccessKey(), region, claudeModelId);

    Map<String, Object> response = new HashMap<>();
    response.put("accessible", hasAccess);
    response.put("modelId", claudeModelId);
    response.put("modelName", CLAUDE_MODEL_NAME);
    response.put("region", region);

    if (hasAccess) {
      response.put("message", "Claude Sonnet 4.0 is accessible in " + region);
    } else {
      response.put(
          "message",
          "No access to Claude Sonnet 4.0 in "
              + region
              + ". Please ensure you have requested model access in this region.");
    }

    return ResponseEntity.ok(response);
  }
}

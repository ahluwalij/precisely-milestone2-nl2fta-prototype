package com.nl2fta.classifier.service.aws;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;

/** Simplified service for AWS Bedrock management - only supports Claude Sonnet 4.0. */
@Slf4j
@Service
public class AwsRegionModelService {

  @Value("${aws.bedrock.claude.model-id}")
  private String claudeModelId;

  @Value("${aws.region:us-east-1}")
  private String defaultRegion;

  // US-only Bedrock regions for Claude Sonnet 4.0
  private static final Set<String> BEDROCK_REGIONS =
      Set.of(
          "us-east-1", // N. Virginia
          "us-east-2", // Ohio
          "us-west-1", // N. California
          "us-west-2" // Oregon
          );

  /** Validates AWS credentials by attempting to list Bedrock models. */
  public boolean validateCredentials(String accessKeyId, String secretAccessKey) {
    return validateCredentials(accessKeyId, secretAccessKey, defaultRegion);
  }

  /** Validates AWS credentials by attempting to list Bedrock models in a specific region. */
  public boolean validateCredentials(String accessKeyId, String secretAccessKey, String region) {
    try {
      AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
      BedrockClient bedrockClient =
          BedrockClient.builder()
              .region(Region.of(region))
              .credentialsProvider(StaticCredentialsProvider.create(credentials))
              .build();

      ListFoundationModelsRequest request = ListFoundationModelsRequest.builder().build();
      bedrockClient.listFoundationModels(request);
      return true;
    } catch (Exception e) {
      log.error("Failed to validate AWS credentials in region {}: {}", region, e.getMessage());
      return false;
    }
  }

  /** Returns available Bedrock regions. */
  public List<RegionInfo> getAvailableBedrockRegions(String accessKeyId, String secretAccessKey) {
    List<RegionInfo> availableRegions = new ArrayList<>();

    for (String regionId : BEDROCK_REGIONS) {
      availableRegions.add(
          RegionInfo.builder()
              .regionId(regionId)
              .displayName(getRegionDisplayName(regionId))
              .build());
    }

    return availableRegions;
  }

  /** Returns Claude Sonnet 4.0 model info for any region. */
  public List<ModelInfo> getAvailableModels(
      String accessKeyId, String secretAccessKey, String region) {
    // Always return Claude Sonnet 4.0
    ModelInfo claudeModel =
        ModelInfo.builder()
            .modelId(claudeModelId)
            .modelName("Claude Sonnet 4.0")
            .provider("Anthropic")
            .inputModalities(List.of("TEXT"))
            .outputModalities(List.of("TEXT"))
            .requiresInferenceProfile(true)
            .build();

    log.info("Returning Claude Sonnet 4.0 model for region {}", region);
    return List.of(claudeModel);
  }

  /**
   * Validates access to a model. For cross-region inference profiles like Claude 4 Sonnet, we need
   * to actually try invoking the model since they don't appear in ListFoundationModels.
   */
  public boolean validateModelAccess(
      String accessKeyId, String secretAccessKey, String region, String modelId) {
    try {
      AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

      // For Claude 4 Sonnet (cross-region inference), we must validate by invocation
      if (modelId.contains("claude-sonnet-4") || modelId.contains("anthropic.claude-sonnet-4")) {
        var bedrockRuntimeClient =
            software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        // Minimal test payload for Claude
        String testPrompt =
            "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";

        var invokeRequest =
            software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .body(software.amazon.awssdk.core.SdkBytes.fromUtf8String(testPrompt))
                .build();

        bedrockRuntimeClient.invokeModel(invokeRequest);
        log.info(
            "Successfully validated access to Claude 4 Sonnet {} in region {}", modelId, region);
        return true;
      }

      // For Titan embeddings, check the foundation models list
      BedrockClient bedrockClient =
          BedrockClient.builder()
              .region(Region.of(region))
              .credentialsProvider(StaticCredentialsProvider.create(credentials))
              .build();

      var listModelsRequest =
          ListFoundationModelsRequest.builder().byInferenceType("ON_DEMAND").build();

      var response = bedrockClient.listFoundationModels(listModelsRequest);

      // For Titan embeddings, just check if it's in the list
      boolean modelFound =
          response.modelSummaries().stream().anyMatch(model -> model.modelId().equals(modelId));

      if (modelFound) {
        log.info("Model {} is available in region {}", modelId, region);
        return true;
      } else {
        log.warn("Model {} is not available in region {}", modelId, region);
        return false;
      }
    } catch (software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException e) {
      log.error("Access denied to model {} in region {}: {}", modelId, region, e.getMessage());
      return false;
    } catch (Exception e) {
      log.error(
          "Failed to validate model access for {} in region {}: {}",
          modelId,
          region,
          e.getMessage());
      return false;
    }
  }

  private String getRegionDisplayName(String regionId) {
    return switch (regionId) {
      case "us-east-1" -> "us-east-1 - US East (N. Virginia)";
      case "us-east-2" -> "us-east-2 - US East (Ohio)";
      case "us-west-1" -> "us-west-1 - US West (N. California)";
      case "us-west-2" -> "us-west-2 - US West (Oregon)";
      default -> regionId;
    };
  }

  /** Region information DTO. */
  @lombok.Data
  @lombok.Builder
  public static class RegionInfo {
    private String regionId;
    private String displayName;
  }

  /** Model information DTO. */
  @lombok.Data
  @lombok.Builder
  public static class ModelInfo {
    private String modelId;
    private String modelName;
    private String provider;
    private List<String> inputModalities;
    private List<String> outputModalities;
    private boolean requiresInferenceProfile;
  }
}

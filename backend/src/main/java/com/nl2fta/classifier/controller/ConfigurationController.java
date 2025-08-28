package com.nl2fta.classifier.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Provides runtime configuration to the frontend application. All values are read from environment
 * variables defined in docker-compose.
 */
@RestController
@RequestMapping("/api/config")
@Tag(name = "Configuration", description = "Runtime configuration for frontend application")
public class ConfigurationController {

  @Value("${app.upload.max-file-size:10485760}")
  private Long maxFileSize;

  @Value("${app.upload.max-rows:1000}")
  private Integer maxRows;

  @Value("${api.version:v1}")
  private String apiVersion;

  @Value("${app.environment:production}")
  private String environment;

  /**
   * Returns frontend configuration values. These values are defined in docker-compose files and
   * passed as environment variables.
   */
  @GetMapping
  @Operation(
      summary = "Get frontend configuration",
      description = "Returns runtime configuration values for the frontend application")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully")
      })
  public Map<String, Object> getFrontendConfiguration() {
    Map<String, Object> config = new java.util.HashMap<>();
    config.put("maxFileSize", maxFileSize);
    config.put("maxRows", maxRows);
    config.put("apiUrl", "/api");

    // Do not expose credentials or secrets via configuration endpoint in production
    String awsModelId = System.getenv("AWS_BEDROCK_MODEL_ID");
    if (awsModelId != null && !awsModelId.isEmpty()) {
      config.put("awsModelId", awsModelId);
    }

    // In non-production environments, allow autofill for developer convenience
    if (!"production".equalsIgnoreCase(environment)) {
      String devAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
      String devSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
      String devRegion = System.getenv("AWS_REGION");
      if (devAccessKeyId != null && !devAccessKeyId.isEmpty()) {
        config.put("awsAccessKeyId", devAccessKeyId);
      }
      if (devSecretAccessKey != null && !devSecretAccessKey.isEmpty()) {
        config.put("awsSecretAccessKey", devSecretAccessKey);
      }
      if (devRegion != null && !devRegion.isEmpty()) {
        config.put("awsRegion", devRegion);
      }
    }

    return config;
  }
}

package com.nl2fta.classifier.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.config.RsaCryptoService;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.aws.AwsRegionModelService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aws/crypto")
@RequiredArgsConstructor
@Tag(name = "AWS Crypto", description = "Public key and encrypted credential endpoints")
public class AwsCryptoController {

  private final RsaCryptoService rsaCryptoService;
  private final AwsRegionModelService awsRegionModelService;
  private final AwsBedrockService awsBedrockService;
  private final AwsCredentialsService awsCredentialsService;
  private final HybridCustomSemanticTypeRepository hybridRepository;
  private final VectorIndexInitializationService vectorIndexService;
  private final CustomSemanticTypeService customSemanticTypeService;

  @GetMapping("/public-key")
  @Operation(summary = "Get RSA public key (PEM)")
  public ResponseEntity<Map<String, String>> getPublicKey() {
    return ResponseEntity.ok(Map.of("publicKey", rsaCryptoService.getPublicKeyPem()));
  }

  @PostMapping("/validate-credentials")
  @Operation(summary = "Validate encrypted AWS credentials and return regions")
  public ResponseEntity<Map<String, Object>> validateEncrypted(
      @Valid @RequestBody EncryptedRequest request) {
    String accessKeyId = rsaCryptoService.decryptBase64(request.getAccessKeyId());
    String secretKey = rsaCryptoService.decryptBase64(request.getSecretAccessKey());
    String region = request.getRegion() != null ? request.getRegion() : "us-east-1";

    boolean valid = awsRegionModelService.validateCredentials(accessKeyId, secretKey, region);
    if (!valid) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("valid", false, "message", "Invalid AWS credentials"));
    }

    List<com.nl2fta.classifier.service.aws.AwsRegionModelService.RegionInfo> regions =
        awsRegionModelService.getAvailableBedrockRegions(accessKeyId, secretKey);
    return ResponseEntity.ok(
        Map.of("valid", true, "regions", regions, "message", "Credentials validated successfully"));
  }

  @PostMapping("/models/{region}")
  @Operation(summary = "Get models for region with encrypted credentials")
  public ResponseEntity<Map<String, Object>> modelsEncrypted(
      @Valid @RequestBody EncryptedRequest request, @PathVariable String region) {
    String accessKeyId = rsaCryptoService.decryptBase64(request.getAccessKeyId());
    String secretKey = rsaCryptoService.decryptBase64(request.getSecretAccessKey());

    boolean valid = awsRegionModelService.validateCredentials(accessKeyId, secretKey);
    if (!valid) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Invalid AWS credentials"));
    }

    List<com.nl2fta.classifier.service.aws.AwsRegionModelService.ModelInfo> models =
        awsRegionModelService.getAvailableModels(accessKeyId, secretKey, region);

    return ResponseEntity.ok(Map.of("region", region, "models", models, "count", models.size()));
  }

  @PostMapping("/validate-model/{region}")
  @Operation(summary = "Validate access to model in region with encrypted credentials")
  public ResponseEntity<Map<String, Object>> validateModelEncrypted(
      @Valid @RequestBody EncryptedRequest request, @PathVariable String region) {
    String accessKeyId = rsaCryptoService.decryptBase64(request.getAccessKeyId());
    String secretKey = rsaCryptoService.decryptBase64(request.getSecretAccessKey());
    String modelId = request.getModelId();

    boolean credentialsValid = awsRegionModelService.validateCredentials(accessKeyId, secretKey);
    if (!credentialsValid) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("accessible", false, "message", "Invalid AWS credentials"));
    }

    if (modelId == null || modelId.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("accessible", false, "message", "Model ID is required"));
    }

    // Validate specific model access in the region
    boolean modelAccessible =
        awsRegionModelService.validateModelAccess(accessKeyId, secretKey, region, modelId);

    return ResponseEntity.ok(
        Map.of(
            "accessible",
            modelAccessible,
            "modelId",
            modelId,
            "region",
            region,
            "message",
            modelAccessible ? "Model access validated successfully" : "Model access denied"));
  }

  @PostMapping("/configure")
  @Operation(summary = "Configure AWS with encrypted credentials")
  public ResponseEntity<Map<String, Object>> configureEncrypted(
      @Valid @RequestBody EncryptedRequest request) {
    String accessKeyId = rsaCryptoService.decryptBase64(request.getAccessKeyId());
    String secretKey = rsaCryptoService.decryptBase64(request.getSecretAccessKey());
    String region = request.getRegion();

    boolean valid =
        awsRegionModelService.validateCredentials(
            accessKeyId, secretKey, region != null ? region : "us-east-1");
    if (!valid) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("success", false, "message", "Invalid AWS credentials"));
    }

    // Configure downstream services
    awsBedrockService.initializeClient(accessKeyId, secretKey, region);
    awsCredentialsService.setCredentials(accessKeyId, secretKey, region);

    // Initialize S3 repositories and vector indexing so similarity search works
    try {
      // Initialize S3 storage backends
      hybridRepository.initializeS3Repository();

      // Kick off vector index initialization (async)
      vectorIndexService.initializeAfterAwsConnection();

      // Allow services to fully initialize before converting built-ins
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }

      // Convert built-in types to custom so the bucket contains the baseline 132 types
      try {
        customSemanticTypeService.convertAllBuiltInTypesToCustom();
      } catch (Exception conversionError) {
        // Do not fail configuration if conversion has any issues
      }
    } catch (Exception initError) {
      // Do not fail configuration; frontend experience should still proceed
    }

    return ResponseEntity.ok(
        Map.of("success", true, "region", region != null ? region : "us-east-1"));
  }

  public static class EncryptedRequest {
    private String accessKeyId;
    private String secretAccessKey;
    private String region;
    private String modelId;

    public String getAccessKeyId() {
      return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
      return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getModelId() {
      return modelId;
    }

    public void setModelId(String modelId) {
      this.modelId = modelId;
    }
  }
}

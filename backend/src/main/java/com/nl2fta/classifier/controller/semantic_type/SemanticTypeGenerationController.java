package com.nl2fta.classifier.controller.semantic_type;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cobber.fta.TextAnalysisResult;
import com.cobber.fta.TextAnalyzer;
import com.nl2fta.classifier.dto.AwsCredentialsRequest;
import com.nl2fta.classifier.dto.semantic_type.GenerateValidatedExamplesRequest;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedValidatedExamplesResponse;
import com.nl2fta.classifier.dto.semantic_type.HeaderPattern;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.semantic_type.generation.SemanticTypeGenerationService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/semantic-types")
@RequiredArgsConstructor
@Tag(
    name = "Semantic Type Generation",
    description = "AI-powered semantic type generation using AWS Bedrock")
public class SemanticTypeGenerationController {

  private final SemanticTypeGenerationService generationService;
  private final AwsBedrockService awsBedrockService;
  private final AwsCredentialsService awsCredentialsService;
  private final CustomSemanticTypeService customSemanticTypeService;
  private final HybridCustomSemanticTypeRepository hybridRepository;

  // Counter for generating unique priorities (starts at 2500, increments by 10)
  // This ensures generated types have priority above converted built-in types (which are original +
  // 2000)
  private static final AtomicInteger PRIORITY_COUNTER = new AtomicInteger(2500);

  /**
   * Enriches the generation request with FTA pre-analysis to determine the actual baseType that FTA
   * would detect for the given examples.
   */
  private void enrichRequestWithFTAAnalysis(SemanticTypeGenerationRequest request) {
    if (request.getPositiveContentExamples() == null
        || request.getPositiveContentExamples().isEmpty()) {
      return;
    }

    try {
      // Analyze samples with FTA to determine base type
      TextAnalyzer analyzer = new TextAnalyzer("pre-analysis");
      for (String example : request.getPositiveContentExamples()) {
        analyzer.train(example);
      }

      TextAnalysisResult result = analyzer.getResult();

      // Add FTA's detected type to the request context
      String detectedBaseType = result.getType().toString(); // LONG, STRING, etc.
      String detectedPattern = result.getRegExp();

      // Store in request for use by generation service
      request.setDetectedBaseType(detectedBaseType);
      request.setDetectedPattern(detectedPattern);

      log.info("FTA pre-analysis: baseType={}, pattern={}", detectedBaseType, detectedPattern);
    } catch (Exception e) {
      log.warn("FTA pre-analysis failed, continuing without enrichment", e);
    }
  }

  @PostMapping("/generate")
  @Operation(
      summary = "Generate semantic type using AI",
      description =
          "Generates a semantic type configuration based on natural language input and examples")
  public ResponseEntity<?> generateSemanticType(
      @Valid @RequestBody SemanticTypeGenerationRequest request) {

    log.info(
        "Generating semantic type - Description: '{}', Positive data examples: {}, Negative data examples: {}, Positive header examples: {}, Negative header examples: {}",
        request.getDescription(),
        request.getPositiveContentExamples() != null ? request.getPositiveContentExamples() : "[]",
        request.getNegativeContentExamples() != null ? request.getNegativeContentExamples() : "[]",
        request.getPositiveHeaderExamples() != null ? request.getPositiveHeaderExamples() : "[]",
        request.getNegativeHeaderExamples() != null ? request.getNegativeHeaderExamples() : "[]");

    try {
      // Check if AWS Bedrock is initialized
      if (!awsBedrockService.isInitialized()) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(
                Map.of(
                    "error", "No LLM provider configured",
                    "message", "Configure AWS Bedrock to use semantic type generation"));
      }

      // Add FTA pre-analysis to enrich the request
      enrichRequestWithFTAAnalysis(request);

      GeneratedSemanticType result = generationService.generateSemanticType(request);

      // ALWAYS use FTA's detected baseType (never trust LLM for this)
      if (request.getDetectedBaseType() != null) {
        result.setBaseType(request.getDetectedBaseType());
        log.info(
            "Using FTA detected baseType: {} for {}",
            request.getDetectedBaseType(),
            result.getSemanticType());
      } else {
        // Default to STRING if no examples were provided for detection
        result.setBaseType("STRING");
        log.info(
            "No FTA detection available, defaulting to STRING baseType for {}",
            result.getSemanticType());
      }

      // Apply FTA-specific enhancements for list types
      result = enhanceForFTARequirements(result);

      // Log appropriate message based on what actually happened
      if ("existing".equals(result.getResultType())) {
        log.info(
            "Found similar existing semantic type: {} ({})",
            result.getSemanticType(),
            result.getExistingTypeMatch());
      } else if ("generated".equals(result.getResultType())) {
        log.info("Successfully generated semantic type: {}", result.getSemanticType());
      } else if ("error".equals(result.getResultType())) {
        log.warn(
            "Semantic type generation completed with errors for: {}", result.getSemanticType());
      } else {
        log.info("Semantic type operation completed: {}", result.getSemanticType());
      }

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("Failed to generate semantic type", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Generation failed", "message", e.getMessage()));
    }
  }

  @PostMapping("/aws/configure")
  @Operation(
      summary = "Configure AWS credentials",
      description = "Configure AWS credentials for Bedrock access")
  public ResponseEntity<?> configureAwsCredentials(
      @Valid @RequestBody AwsCredentialsRequest request) {

    log.info("Configuring AWS credentials for region: {}", request.getRegion());

    try {
      // Initialize both AWS services with the same credentials
      awsBedrockService.initializeClient(
          request.getAccessKeyId(), request.getSecretAccessKey(), request.getRegion());

      // Set credentials in the credentials service for S3 operations
      awsCredentialsService.setCredentials(
          request.getAccessKeyId(), request.getSecretAccessKey(), request.getRegion());

      // Initialize S3 repository now that AWS credentials are available
      log.info("Initializing S3 repository for custom semantic types...");
      hybridRepository.initializeS3Repository();

      // Now that AWS is connected, convert all built-in semantic types to custom types
      // This ensures built-in types have correct priority (original + 2000) and can be overridden
      log.info("AWS credentials configured successfully. Converting built-in semantic types...");

      // Add a small delay to allow AWS services to fully initialize
      try {
        Thread.sleep(2000); // 2 second delay
        log.debug("AWS services initialization delay completed");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Conversion delay interrupted", e);
      }

      try {
        Map<String, Object> conversionResult =
            customSemanticTypeService.convertAllBuiltInTypesToCustom();
        int converted = (Integer) conversionResult.getOrDefault("converted", 0);
        int updated = (Integer) conversionResult.getOrDefault("updated", 0);
        log.info(
            "Built-in type conversion completed: {} converted, {} updated", converted, updated);
      } catch (Exception conversionError) {
        log.warn(
            "Built-in type conversion failed, but AWS credentials are still configured",
            conversionError);
      }

      return ResponseEntity.ok(
          Map.of(
              "status", "success",
              "message", "AWS credentials configured successfully for Claude Sonnet 4.0"));
    } catch (Exception e) {
      log.error("Failed to configure AWS credentials", e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "Configuration failed", "message", e.getMessage()));
    }
  }

  @GetMapping("/aws/status")
  @Operation(
      summary = "Check AWS configuration status",
      description = "Check if AWS credentials are configured")
  public ResponseEntity<Map<String, Object>> checkAwsStatus() {
    boolean isConfigured = awsBedrockService.isInitialized();

    return ResponseEntity.ok(
        Map.of(
            "configured",
            isConfigured,
            "message",
            isConfigured ? "AWS credentials are configured" : "AWS credentials not configured"));
  }

  @PostMapping("/aws/logout")
  @Operation(
      summary = "Logout and clear AWS credentials",
      description = "Clears the configured AWS credentials from the session")
  public ResponseEntity<Map<String, Object>> logout() {
    log.info("Logging out and clearing AWS credentials");

    try {
      awsBedrockService.clearCredentials();
      awsCredentialsService.clearCredentials();

      // Disconnect S3 repository when logging out
      log.info("Disconnecting S3 repository...");
      hybridRepository.disconnectS3();

      return ResponseEntity.ok(
          Map.of(
              "status", "success",
              "message", "Successfully logged out"));
    } catch (Exception e) {
      log.error("Error during logout", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Logout failed", "message", e.getMessage()));
    }
  }

  @PostMapping("/generate-validated-examples")
  public ResponseEntity<?> generateValidatedExamples(
      @RequestBody GenerateValidatedExamplesRequest request) {
    log.info("Generating validated examples for pattern: {}", request.getRegexPattern());

    try {
      // Validate the regex pattern first
      Pattern.compile(request.getRegexPattern());
    } catch (PatternSyntaxException e) {
      log.error("Invalid regex pattern: {}", e.getMessage());
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid regex pattern: " + e.getMessage()));
    }

    try {
      GeneratedValidatedExamplesResponse response =
          generationService.generateValidatedExamples(request);

      if (response.getError() != null) {
        return ResponseEntity.badRequest().body(response);
      }

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error generating validated examples", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Error generating validated examples: " + e.getMessage()));
    }
  }

  /**
   * Enhances generated semantic types to meet FTA requirements for minimal sample detection. Based
   * on comprehensive testing documented in FTA_LIST_TYPE_REQUIREMENTS.md
   */
  private GeneratedSemanticType enhanceForFTARequirements(GeneratedSemanticType result) {
    if (result == null) {
      return result;
    }

    log.debug(
        "Enhancing {} type '{}' for FTA requirements",
        result.getPluginType(),
        result.getSemanticType());

    // 1. For list types: Ensure list values are UPPERCASE (FTA requirement)
    if ("list".equals(result.getPluginType())) {
      if (result.getListValues() != null && !result.getListValues().isEmpty()) {
        List<String> uppercasedValues =
            result.getListValues().stream().map(String::toUpperCase).collect(Collectors.toList());
        result.setListValues(uppercasedValues);
        log.debug("Uppercased {} list values for FTA compatibility", uppercasedValues.size());
      }
    }

    // 2. Ensure all header patterns have confidence=99 for minimal sample detection
    List<HeaderPattern> headerPatterns = result.getHeaderPatterns();
    if (headerPatterns != null && !headerPatterns.isEmpty()) {
      // Set ALL patterns to confidence=99 (not mandatory, so any match works)
      for (int i = 0; i < headerPatterns.size(); i++) {
        HeaderPattern pattern = headerPatterns.get(i);
        if (pattern.getConfidence() < 99) {
          headerPatterns.set(
              i,
              HeaderPattern.builder()
                  .regExp(pattern.getRegExp())
                  .confidence(99)
                  .mandatory(false) // Not mandatory - any match is good
                  .positiveExamples(pattern.getPositiveExamples())
                  .negativeExamples(pattern.getNegativeExamples())
                  .rationale(pattern.getRationale())
                  .build());
        }
      }
      log.debug(
          "Set all {} header patterns to confidence=99 with mandatory=false",
          headerPatterns.size());
    }

    // 3. Generate unique priority to avoid conflicts (FOR ALL TYPES - list AND regex)
    // Priority starts at 2500 and increments by 10 for each new type
    // This ensures all generated types have unique priorities above 2000 (FTA minimum)
    // Different priorities prevent classification conflicts when patterns overlap
    int priority = PRIORITY_COUNTER.getAndAdd(10);
    result.setPriority(priority);
    log.debug(
        "Assigned unique priority {} to semantic type {}", priority, result.getSemanticType());

    // Note: Backout pattern is handled by the LLM in semantic-type-generation.txt
    // which already instructs not to use generic ".*" patterns for list types

    return result;
  }
}

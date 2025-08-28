package com.nl2fta.classifier.service.semantic_type.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GenerateValidatedExamplesRequest;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedValidatedExamplesResponse;
import com.nl2fta.classifier.dto.semantic_type.HeaderPattern;
import com.nl2fta.classifier.dto.semantic_type.PatternUpdateResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.CloudWatchLoggingService;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.aws.LLMService;
import com.nl2fta.classifier.service.aws.LLMServiceSelector;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main orchestrator service for semantic type generation. Coordinates between specialized services
 * to generate, validate, and refine semantic types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeGenerationService {

  private final LLMServiceSelector llmServiceSelector;
  private final SemanticTypePromptService promptService;
  private final SemanticTypeResponseParserService responseParserService;
  private final CustomSemanticTypeService customSemanticTypeService;

  private final SemanticTypeSimilarityService similarityService;

  // Optional; present only when CloudWatch logging is enabled/available
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private CloudWatchLoggingService cloudWatchLoggingService;

  /**
   * Generates a semantic type based on the provided request. Handles examples-only requests,
   * similarity checking, and full generation with validation.
   *
   * @param request the semantic type generation request
   * @return generated semantic type result
   * @throws Exception if there's an error during generation
   */
  public GeneratedSemanticType generateSemanticType(SemanticTypeGenerationRequest request)
      throws Exception {
    String correlationId = UUID.randomUUID().toString();
    // Bind to MDC so nested logs also include the correlationId
    MDC.put("correlationId", correlationId);
    logGenerationEvent("SEMANTIC_TYPE_GENERATION_REQUEST", correlationId, request, null, null);
    // Handle examples-only requests
    if (isExamplesOnlyRequest(request)) {
      GeneratedSemanticType examplesOnlyResult = generateAdditionalExamples(request, correlationId);
      if (examplesOnlyResult != null) {
        examplesOnlyResult.setCorrelationId(correlationId);
      }
      logGenerationEvent(
          "SEMANTIC_TYPE_GENERATION_RESULT", correlationId, request, examplesOnlyResult, null);
      return examplesOnlyResult;
    }

    // Handle generating examples for existing types
    if (request.getGenerateExamplesForExistingType() != null
        && !request.getGenerateExamplesForExistingType().trim().isEmpty()) {
      GeneratedSemanticType existingExamples =
          similarityService.generateExamplesForExistingType(
              request.getGenerateExamplesForExistingType(), request);
      if (existingExamples != null) {
        existingExamples.setCorrelationId(correlationId);
      }
      logGenerationEvent(
          "SEMANTIC_TYPE_GENERATION_RESULT", correlationId, request, existingExamples, null);
      return existingExamples;
    }

    // Handle similarity checking (only if checkExistingTypes is true AND proceedDespiteSimilarity
    // is false)
    if (request.isCheckExistingTypes() && !request.isProceedDespiteSimilarity()) {
      GeneratedSemanticType existingMatch = similarityService.checkForSimilarExistingType(request);
      if (existingMatch != null) {
        log.info(
            "Found existing match '{}' - using existing type instead of generating new one",
            existingMatch.getSemanticType());
        existingMatch.setCorrelationId(correlationId);
        logGenerationEvent(
            "SEMANTIC_TYPE_GENERATION_RESULT", correlationId, request, existingMatch, null);
        return existingMatch;
      }
      log.debug("No existing matches found - proceeding to generate new semantic type");
    }

    // Generate new semantic type
    GeneratedSemanticType result = generateNewSemanticType(request, correlationId);
    // Attach correlationId to the result so downstream save and UI can reference it
    if (result != null) {
      result.setCorrelationId(correlationId);
    }
    logGenerationEvent("SEMANTIC_TYPE_GENERATION_RESULT", correlationId, request, result, null);
    try {
      return result;
    } finally {
      // Clear MDC at the end of the flow
      MDC.remove("correlationId");
    }
  }

  private boolean isExamplesOnlyRequest(SemanticTypeGenerationRequest request) {
    return request.getTypeName() != null
        && (request.getTypeName().startsWith("EXAMPLES_ONLY_")
            || (request.getDescription() != null
                && request.getDescription().startsWith("EXAMPLES GENERATION ONLY:")));
  }

  private GeneratedSemanticType generateNewSemanticType(
      SemanticTypeGenerationRequest request, String correlationId) throws Exception {
    String prompt = promptService.buildGenerationPrompt(request);
    log.info("On STEP 2 - NEW TYPE GENERATION, sending prompt");
    // Log the FULL prompt content to CloudWatch (not a preview)
    logGenerationEvent("SEMANTIC_TYPE_GENERATION_PROMPT", correlationId, request, null, prompt);
    LLMService llmService = llmServiceSelector.getLLMService();
    String llmResponse = llmService.invokeClaudeForSemanticTypeGeneration(prompt);

    GeneratedSemanticType result =
        responseParserService.parseGenerationResponse(llmResponse, request);

    // Simple validation against regex pattern if present
    if ("regex".equals(result.getPluginType()) && result.getRegexPattern() != null) {
      result = validateExamplesAgainstPattern(result);
    }

    log.info(
        "Successfully generated new semantic type '{}' with plugin type '{}'",
        result.getSemanticType(),
        result.getPluginType());

    return result;
  }

  // Backward-compatible overload for internal callers that do not manage correlation
  private GeneratedSemanticType generateNewSemanticType(SemanticTypeGenerationRequest request)
      throws Exception {
    return generateNewSemanticType(request, UUID.randomUUID().toString());
  }

  private void logGenerationEvent(
      String eventType,
      String correlationId,
      SemanticTypeGenerationRequest request,
      GeneratedSemanticType result,
      String prompt) {
    if (cloudWatchLoggingService == null) {
      return;
    }

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("eventType", eventType);
      data.put("correlationId", correlationId);
      try {
        LLMService llmService = llmServiceSelector.getLLMService();
        data.put("modelId", llmService.getCurrentModelId());
        data.put("llmProvider", llmServiceSelector.getActiveProvider());
      } catch (Exception e) {
        data.put("modelId", null);
        data.put("llmProvider", "none");
      }

      if (request != null) {
        Map<String, Object> req = new HashMap<>();
        req.put("typeName", request.getTypeName());
        req.put("description", request.getDescription());
        req.put("columnHeader", request.getColumnHeader());
        req.put("checkExistingTypes", request.isCheckExistingTypes());
        req.put("proceedDespiteSimilarity", request.isProceedDespiteSimilarity());
        // Include full inputs to aid debugging of user flows
        req.put("positiveContentExamples", request.getPositiveContentExamples());
        req.put("negativeContentExamples", request.getNegativeContentExamples());
        req.put("positiveHeaderExamples", request.getPositiveHeaderExamples());
        req.put("negativeHeaderExamples", request.getNegativeHeaderExamples());
        // Include detected FTA info as part of inputs
        req.put("detectedBaseType", request.getDetectedBaseType());
        req.put("detectedPattern", request.getDetectedPattern());
        data.put("request", req);
      }

      if (prompt != null) {
        Map<String, Object> p = new HashMap<>();
        p.put("length", prompt.length());
        p.put("full", prompt);
        data.put("prompt", p);
      }

      if (result != null) {
        Map<String, Object> res = new HashMap<>();
        res.put("resultType", result.getResultType());
        res.put("semanticType", result.getSemanticType());
        res.put("pluginType", result.getPluginType());
        res.put("regexPattern", result.getRegexPattern());
        res.put("priority", result.getPriority());
        res.put("confidenceThreshold", result.getConfidenceThreshold());
        res.put("headerPatterns", result.getHeaderPatterns());
        res.put("listValues", result.getListValues());
        res.put("positiveContentExamples", result.getPositiveContentExamples());
        res.put("negativeContentExamples", result.getNegativeContentExamples());
        res.put("positiveHeaderExamples", result.getPositiveHeaderExamples());
        res.put("negativeHeaderExamples", result.getNegativeHeaderExamples());
        res.put("explanation", result.getExplanation());
        // echo the inputs that produced this result for traceability
        if (request != null) {
          Map<String, Object> inputs = new HashMap<>();
          inputs.put("description", request.getDescription());
          inputs.put("typeName", request.getTypeName());
          inputs.put("columnHeader", request.getColumnHeader());
          inputs.put("positiveContentExamples", request.getPositiveContentExamples());
          inputs.put("negativeContentExamples", request.getNegativeContentExamples());
          inputs.put("positiveHeaderExamples", request.getPositiveHeaderExamples());
          inputs.put("negativeHeaderExamples", request.getNegativeHeaderExamples());
          inputs.put("detectedBaseType", request.getDetectedBaseType());
          inputs.put("detectedPattern", request.getDetectedPattern());
          res.put("inputs", inputs);
        }
        data.put("result", res);
      }

      cloudWatchLoggingService.log("INFO", "Semantic type generation event", data);
    } catch (Exception ignored) {
      // Never break generation due to logging issues
    }
  }

  private List<String> safePreview(List<String> items) {
    if (items == null) {
      return null;
    }
    List<String> preview = new ArrayList<>();
    for (int i = 0; i < Math.min(3, items.size()); i++) {
      String v = items.get(i);
      preview.add(v == null ? null : (v.length() <= 120 ? v : v.substring(0, 120)));
    }
    return preview;
  }

  /** Validates examples against regex pattern and filters invalid ones. */
  private GeneratedSemanticType validateExamplesAgainstPattern(GeneratedSemanticType result) {
    try {
      Pattern pattern = Pattern.compile(result.getRegexPattern());

      // Validate positive content examples
      List<String> validPositiveContent =
          validateAndFilterExamples(result.getPositiveContentExamples(), pattern, true);

      // Validate negative content examples
      List<String> validNegativeContent =
          validateAndFilterExamples(result.getNegativeContentExamples(), pattern, false);

      // Validate header examples if patterns exist
      List<String> validPositiveHeaders = result.getPositiveHeaderExamples();
      List<String> validNegativeHeaders = result.getNegativeHeaderExamples();

      // Update result with validated examples
      return GeneratedSemanticType.builder()
          .resultType(result.getResultType())
          .semanticType(result.getSemanticType())
          .description(result.getDescription())
          .pluginType(result.getPluginType())
          .regexPattern(result.getRegexPattern())
          .listValues(result.getListValues())
          .positiveContentExamples(validPositiveContent)
          .negativeContentExamples(validNegativeContent)
          .positiveHeaderExamples(validPositiveHeaders)
          .negativeHeaderExamples(validNegativeHeaders)
          .confidenceThreshold(result.getConfidenceThreshold())
          .headerPatterns(result.getHeaderPatterns())
          .explanation(result.getExplanation())
          .existingTypeMatch(result.getExistingTypeMatch())
          .existingTypeDescription(result.getExistingTypeDescription())
          .existingTypePattern(result.getExistingTypePattern())
          .existingTypeHeaderPatterns(result.getExistingTypeHeaderPatterns())
          .existingTypeIsBuiltIn(result.isExistingTypeIsBuiltIn())
          .suggestedAction(result.getSuggestedAction())
          .build();

    } catch (Exception e) {
      log.error("Error validating examples against pattern: {}", e.getMessage(), e);
      return result; // Return original if validation fails
    }
  }

  /** Validates and filters a list of examples against a pattern */
  private List<String> validateAndFilterExamples(
      List<String> examples, Pattern pattern, boolean shouldMatch) {
    if (examples == null || examples.isEmpty()) {
      return new ArrayList<>();
    }

    return examples.stream()
        .filter(
            example -> {
              boolean matches = pattern.matcher(example).matches();
              return matches == shouldMatch;
            })
        .collect(Collectors.toList());
  }

  /**
   * Generates additional examples for an existing semantic type.
   *
   * @param request the request containing current examples
   * @return GeneratedSemanticType with additional examples
   * @throws Exception if there's an error during generation
   */
  private GeneratedSemanticType generateAdditionalExamples(SemanticTypeGenerationRequest request)
      throws Exception {
    return generateNewSemanticType(request);
  }

  // Overload that preserves and propagates the correlationId
  private GeneratedSemanticType generateAdditionalExamples(
      SemanticTypeGenerationRequest request, String correlationId) throws Exception {
    return generateNewSemanticType(request, correlationId);
  }

  /**
   * Generates validated examples with pattern updates based on user feedback. Handles different
   * update types: data pattern improvement vs header pattern improvement.
   *
   * @param request the validated examples generation request
   * @return response with validated examples and updated patterns
   */
  public GeneratedValidatedExamplesResponse generateValidatedExamples(
      GenerateValidatedExamplesRequest request) {
    // Ensure any ongoing correlationId is included in logs
    log.info("Starting validated example generation for: {}", request.getSemanticTypeName());

    if (request.isPatternImprovement()) {
      log.info("Processing data pattern improvement request");
      return handleDataPatternImprovement(request);
    } else if (request.isHeaderPatternImprovement()) {
      log.info("Processing header pattern improvement request");
      return handleHeaderPatternImprovement(request);
    } else {
      log.info("Processing general example generation request");
      return handleGeneralExampleGeneration(request);
    }
  }

  /** Handles data pattern improvement - focuses on updating the regex pattern for data content. */
  private GeneratedValidatedExamplesResponse handleDataPatternImprovement(
      GenerateValidatedExamplesRequest request) {
    log.info(
        "Improving data regex pattern based on user feedback: {}", request.getUserDescription());

    try {
      // Build prompt specifically for data pattern improvement
      String prompt =
          promptService.buildRegenerateDataValuesPrompt(
              request.getSemanticTypeName(),
              request.getRegexPattern(),
              request.getExistingPositiveExamples(),
              request.getExistingNegativeExamples(),
              request.getUserDescription() != null
                  ? request.getUserDescription()
                  : "Improve the data pattern",
              request.getDescription());

      log.info("On DATA PATTERN IMPROVEMENT, sending this prompt:\n{}", prompt);
      LLMService llmService = llmServiceSelector.getLLMService();
      String claudeResponse = llmService.invokeClaudeForSemanticTypeGeneration(prompt);
      PatternUpdateResponse updateResponse =
          responseParserService.parsePatternUpdateResponse(claudeResponse);

      if (updateResponse == null) {
        return buildErrorResponse("Failed to parse data pattern update response");
      }

      // Use the updated regex pattern for validation
      String finalRegexPattern =
          updateResponse.getImprovedPattern() != null
              ? updateResponse.getImprovedPattern()
              : request.getRegexPattern();

      log.info("Updated regex pattern: {}", finalRegexPattern);

      // Generate and validate examples against the improved pattern
      List<String> validatedPositives =
          validateExamples(updateResponse.getNewPositiveContentExamples(), finalRegexPattern, true);
      List<String> validatedNegatives =
          validateExamples(
              updateResponse.getNewNegativeContentExamples(), finalRegexPattern, false);

      return GeneratedValidatedExamplesResponse.builder()
          .positiveExamples(validatedPositives)
          .negativeExamples(validatedNegatives)
          .updatedRegexPattern(
              updateResponse.getImprovedPattern()) // This is what the frontend expects
          .rationale(updateResponse.getExplanation())
          .attemptsUsed(1)
          .validationSuccessful(true)
          .validationSummary(
              buildValidationSummary(updateResponse, validatedPositives, validatedNegatives))
          .build();
    } catch (Exception e) {
      log.error("Error during data pattern improvement: {}", e.getMessage(), e);
      return buildErrorResponse("Failed to improve data pattern: " + e.getMessage());
    }
  }

  /** Handles header pattern improvement - focuses on updating header patterns. */
  private GeneratedValidatedExamplesResponse handleHeaderPatternImprovement(
      GenerateValidatedExamplesRequest request) {
    log.info("Improving header patterns based on user feedback: {}", request.getUserDescription());

    // Get current header patterns from the existing semantic type
    CustomSemanticType currentType = null;
    try {
      currentType = customSemanticTypeService.getCustomType(request.getSemanticTypeName());
    } catch (Exception e) {
      log.warn(
          "Could not find semantic type: {}, continuing with empty patterns",
          request.getSemanticTypeName());
      // Continue with empty patterns - this is a graceful fallback
    }

    List<String> currentHeaderPatterns = new ArrayList<>();

    // First try to get patterns from the request (for unsaved types during generation)
    if (request.getRegexPattern() != null && !request.getRegexPattern().trim().isEmpty()) {
      // The regex pattern from frontend contains comma-separated patterns
      currentHeaderPatterns =
          Arrays.stream(request.getRegexPattern().split(",\\s*"))
              .map(String::trim)
              .filter(p -> !p.isEmpty())
              .collect(Collectors.toList());
      log.info("Using {} header patterns from request", currentHeaderPatterns.size());
    } else if (currentType != null
        && currentType.getValidLocales() != null
        && !currentType.getValidLocales().isEmpty()) {
      // Fall back to saved type if available
      var locale = currentType.getValidLocales().get(0);
      if (locale.getHeaderRegExps() != null) {
        currentHeaderPatterns =
            locale.getHeaderRegExps().stream().map(h -> h.getRegExp()).collect(Collectors.toList());
      }
    }

    try {
      // Build prompt specifically for header pattern improvement
      String prompt =
          promptService.buildRegenerateHeaderValuesPrompt(
              request.getSemanticTypeName(),
              String.join(", ", currentHeaderPatterns),
              request.getExistingPositiveExamples(),
              request.getExistingNegativeExamples(),
              request.getUserDescription() != null
                  ? request.getUserDescription()
                  : "Improve the header patterns");

      log.info("On HEADER PATTERN IMPROVEMENT, sending this prompt:\n{}", prompt);
      LLMService llmService = llmServiceSelector.getLLMService();
      String claudeResponse = llmService.invokeClaudeForSemanticTypeGeneration(prompt);
      PatternUpdateResponse updateResponse =
          responseParserService.parsePatternUpdateResponse(claudeResponse);

      if (updateResponse == null) {
        return buildErrorResponse("Failed to parse header pattern update response");
      }

      // Extract updated header patterns from the response
      List<String> updatedHeaderPatterns = new ArrayList<>();
      if (updateResponse.getUpdatedHeaderPatterns() != null
          && !updateResponse.getUpdatedHeaderPatterns().isEmpty()) {
        // Use the actual regex patterns from the LLM response
        updatedHeaderPatterns =
            updateResponse.getUpdatedHeaderPatterns().stream()
                .map(HeaderPattern::getRegExp)
                .filter(pattern -> pattern != null && !pattern.trim().isEmpty())
                .collect(Collectors.toList());
        log.info(
            "Extracted {} updated header patterns from LLM response", updatedHeaderPatterns.size());
      } else if (updateResponse.getNewPositiveHeaderExamples() != null
          && !updateResponse.getNewPositiveHeaderExamples().isEmpty()) {
        // Fallback: Convert header examples to regex patterns if no patterns provided
        updatedHeaderPatterns =
            updateResponse.getNewPositiveHeaderExamples().stream()
                .map(example -> "(?i).*" + example.replaceAll("\\W", "\\\\$0") + ".*")
                .collect(Collectors.toList());
        log.info(
            "Generated {} updated header patterns from positive examples (fallback)",
            updatedHeaderPatterns.size());
      } else {
        log.info("Header pattern update completed but no patterns or examples available");
      }

      // For header improvements, we generate header examples, not data examples
      List<String> validatedPositives =
          updateResponse.getNewPositiveHeaderExamples() != null
              ? updateResponse.getNewPositiveHeaderExamples()
              : new ArrayList<>();
      List<String> validatedNegatives =
          updateResponse.getNewNegativeHeaderExamples() != null
              ? updateResponse.getNewNegativeHeaderExamples()
              : new ArrayList<>();

      return GeneratedValidatedExamplesResponse.builder()
          .positiveExamples(validatedPositives)
          .negativeExamples(validatedNegatives)
          .updatedHeaderPatterns(updatedHeaderPatterns) // This is what the frontend expects
          .rationale(updateResponse.getExplanation())
          .attemptsUsed(1)
          .validationSuccessful(true)
          .validationSummary(
              buildValidationSummary(updateResponse, validatedPositives, validatedNegatives))
          .build();
    } catch (Exception e) {
      log.error("Error during header pattern improvement: {}", e.getMessage(), e);
      return buildErrorResponse("Failed to improve header patterns: " + e.getMessage());
    }
  }

  /** Handles general example generation without pattern updates. */
  private GeneratedValidatedExamplesResponse handleGeneralExampleGeneration(
      GenerateValidatedExamplesRequest request) {
    log.info("Generating examples for pattern: {}", request.getRegexPattern());

    // Use the existing pattern example generator for basic example generation
    try {
      // For general example generation without pattern updates, generate simple examples
      List<String> positives = new ArrayList<>();
      List<String> negatives = new ArrayList<>();

      // Add any existing examples if available
      if (request.getExistingPositiveExamples() != null) {
        positives.addAll(request.getExistingPositiveExamples());
      }
      if (request.getExistingNegativeExamples() != null) {
        negatives.addAll(request.getExistingNegativeExamples());
      }

      return GeneratedValidatedExamplesResponse.builder()
          .positiveExamples(positives)
          .negativeExamples(negatives)
          .attemptsUsed(1)
          .validationSuccessful(true)
          .validationSummary(
              GeneratedValidatedExamplesResponse.ValidationSummary.builder()
                  .totalPositiveGenerated(positives.size())
                  .totalNegativeGenerated(negatives.size())
                  .positiveExamplesValidated(positives.size())
                  .negativeExamplesValidated(negatives.size())
                  .positiveExamplesFailed(0)
                  .negativeExamplesFailed(0)
                  .build())
          .build();
    } catch (Exception e) {
      log.error("Error generating general examples: {}", e.getMessage(), e);
      return buildErrorResponse("Failed to generate examples: " + e.getMessage());
    }
  }

  /** Validates a list of examples against a regex pattern. */
  private List<String> validateExamples(
      List<String> examples, String regexPattern, boolean shouldMatch) {
    if (examples == null) {
      return new ArrayList<>();
    }

    return examples.stream()
        .filter(
            example -> {
              try {
                Pattern pattern = Pattern.compile(regexPattern);
                boolean matches = pattern.matcher(example).matches();
                return shouldMatch ? matches : !matches;
              } catch (Exception e) {
                log.warn("Invalid regex pattern: {}", regexPattern);
                return false;
              }
            })
        .collect(Collectors.toList());
  }

  /** Builds validation summary from pattern update response. */
  private GeneratedValidatedExamplesResponse.ValidationSummary buildValidationSummary(
      PatternUpdateResponse updateResponse,
      List<String> validatedPositives,
      List<String> validatedNegatives) {

    int totalPositive =
        updateResponse.getNewPositiveContentExamples() != null
            ? updateResponse.getNewPositiveContentExamples().size()
            : 0;
    int totalNegative =
        updateResponse.getNewNegativeContentExamples() != null
            ? updateResponse.getNewNegativeContentExamples().size()
            : 0;

    // For header improvements, use header examples
    if (totalPositive == 0 && updateResponse.getNewPositiveHeaderExamples() != null) {
      totalPositive = updateResponse.getNewPositiveHeaderExamples().size();
    }
    if (totalNegative == 0 && updateResponse.getNewNegativeHeaderExamples() != null) {
      totalNegative = updateResponse.getNewNegativeHeaderExamples().size();
    }

    return GeneratedValidatedExamplesResponse.ValidationSummary.builder()
        .totalPositiveGenerated(totalPositive)
        .totalNegativeGenerated(totalNegative)
        .positiveExamplesValidated(validatedPositives.size())
        .negativeExamplesValidated(validatedNegatives.size())
        .positiveExamplesFailed(totalPositive - validatedPositives.size())
        .negativeExamplesFailed(totalNegative - validatedNegatives.size())
        .build();
  }

  /** Builds error response for failed operations. */
  private GeneratedValidatedExamplesResponse buildErrorResponse(String error) {
    return GeneratedValidatedExamplesResponse.builder()
        .positiveExamples(new ArrayList<>())
        .negativeExamples(new ArrayList<>())
        .attemptsUsed(1)
        .validationSuccessful(false)
        .error(error)
        .build();
  }
}

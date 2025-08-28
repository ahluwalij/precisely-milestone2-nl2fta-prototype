package com.nl2fta.classifier.service.semantic_type.generation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeComparison;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.semantic_type.SemanticTypeComparisonService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for checking similarity between requested semantic types and existing ones.
 * Uses vector-based similarity search exclusively for efficient and accurate matching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeSimilarityService {

  private final AwsBedrockService awsBedrockService;
  private final CustomSemanticTypeService customSemanticTypeService;
  private final SemanticTypePromptService promptService;
  private final SemanticTypeResponseParserService responseParserService;
  private final VectorSimilaritySearchService vectorSearchService;
  private final SemanticTypeComparisonService comparisonService;

  /**
   * Checks if there's an existing semantic type that matches the user's requirements. Uses
   * vector-based similarity search for efficient matching.
   *
   * @param request the semantic type generation request
   * @return GeneratedSemanticType with existing match info or null if no match found
   * @throws IOException if there's an error accessing existing types
   */
  public GeneratedSemanticType checkForSimilarExistingType(SemanticTypeGenerationRequest request)
      throws IOException {
    List<CustomSemanticType> existingTypes = customSemanticTypeService.getAllCustomTypes();
    List<CustomSemanticType> customTypesOnly = customSemanticTypeService.getCustomTypesOnly();

    log.debug("Using vector search to find matches among {} existing types", existingTypes.size());

    try {
      // Use vector-based similarity search to get top 3 matches above threshold 0.35
      List<VectorSimilaritySearchService.SimilaritySearchResult> topMatches =
          vectorSearchService.findTopSimilarTypesForLLM(request, 0.35);

      if (topMatches.isEmpty()) {
        log.info("VECTOR SEARCH: No matches found above similarity threshold (0.35)");
        return null;
      }

      log.info("VECTOR SEARCH: Found {} potential matches above threshold", topMatches.size());

      // Fast-path: if there is a single high-confidence match, return it directly without LLM
      if (topMatches.size() == 1) {
        return buildVectorSearchMatchInfo(
            topMatches.get(0), existingTypes, customTypesOnly, request);
      }

      // Otherwise, use LLM to evaluate the top candidates
      log.info(
          "VECTOR SEARCH: Found {} candidate matches, using LLM to evaluate:", topMatches.size());
      for (VectorSimilaritySearchService.SimilaritySearchResult match : topMatches) {
        log.info("  - {} (score: {})", match.getSemanticType(), match.getSimilarityScore());
      }

      return evaluateMultipleMatchesWithLLM(topMatches, existingTypes, customTypesOnly, request);

    } catch (Exception e) {
      log.error("Vector search failed", e);
      return null;
    }
  }

  /** Builds match information from vector search result. */
  private GeneratedSemanticType buildVectorSearchMatchInfo(
      VectorSimilaritySearchService.SimilaritySearchResult match,
      List<CustomSemanticType> allTypes,
      List<CustomSemanticType> customTypesOnly,
      SemanticTypeGenerationRequest request) {

    CustomSemanticType matched =
        allTypes.stream()
            .filter(t -> t.getSemanticType().equals(match.getSemanticType()))
            .findFirst()
            .orElse(null);

    if (matched == null) {
      return null;
    }

    boolean isBuiltIn =
        customTypesOnly.stream()
            .noneMatch(t -> t.getSemanticType().equals(matched.getSemanticType()));

    String explanation =
        String.format(
            "Found existing semantic type '%s' with %.2f%% similarity based on vector analysis. %s",
            match.getSemanticType(), match.getSimilarityScore() * 100, matched.getDescription());

    // Generate detailed comparison
    SemanticTypeComparison comparison = comparisonService.compareSemanticTypes(request, match);

    GeneratedSemanticType response =
        buildMatchResponse(
            matched, isBuiltIn, explanation, "Use the existing semantic type", request);
    response.setComparison(comparison);

    return response;
  }

  /** Evaluates multiple high-scoring matches using LLM to choose the best one. */
  private GeneratedSemanticType evaluateMultipleMatchesWithLLM(
      List<VectorSimilaritySearchService.SimilaritySearchResult> topMatches,
      List<CustomSemanticType> allTypes,
      List<CustomSemanticType> customTypesOnly,
      SemanticTypeGenerationRequest request)
      throws Exception {

    // Get the CustomSemanticType objects for the top matches
    List<CustomSemanticType> matchedTypes = new ArrayList<>();
    for (VectorSimilaritySearchService.SimilaritySearchResult match : topMatches) {
      CustomSemanticType type =
          allTypes.stream()
              .filter(t -> t.getSemanticType().equals(match.getSemanticType()))
              .findFirst()
              .orElse(null);
      if (type != null) {
        matchedTypes.add(type);
      }
    }

    if (matchedTypes.isEmpty()) {
      return null;
    }

    // Build prompt with all top matches for LLM evaluation
    String prompt =
        promptService.buildMultipleMatchEvaluationPrompt(request, matchedTypes, topMatches);
    log.info("VECTOR SEARCH: Sending LLM evaluation prompt:\n{}", prompt);

    String llmResponse = awsBedrockService.invokeClaudeForSemanticTypeGeneration(prompt);
    Map<String, Object> parsed = responseParserService.parseSimilarityCheckXmlResponse(llmResponse);

    if (parsed != null && Boolean.TRUE.equals(parsed.get("foundMatch"))) {
      String selectedType = (String) parsed.get("matchedType");

      // Find the selected match from our top matches
      VectorSimilaritySearchService.SimilaritySearchResult selectedMatch =
          topMatches.stream()
              .filter(m -> m.getSemanticType().equals(selectedType))
              .findFirst()
              .orElse(null); // Return null if LLM selected non-existent type

      if (selectedMatch == null) {
        log.warn(
            "LLM selected non-existent type '{}' not found in candidates. Returning null.",
            selectedType);
        return null;
      }

      CustomSemanticType selectedSemanticType =
          matchedTypes.stream()
              .filter(t -> t.getSemanticType().equals(selectedMatch.getSemanticType()))
              .findFirst()
              .orElse(null);

      if (selectedSemanticType == null) {
        return null;
      }

      boolean isBuiltIn =
          customTypesOnly.stream()
              .noneMatch(t -> t.getSemanticType().equals(selectedSemanticType.getSemanticType()));

      // Generate detailed comparison
      SemanticTypeComparison comparison =
          comparisonService.compareSemanticTypes(request, selectedMatch);

      GeneratedSemanticType response =
          buildMatchResponse(
              selectedSemanticType,
              isBuiltIn,
              (String) parsed.get("explanation"),
              (String) parsed.get("suggestedAction"),
              request);
      response.setComparison(comparison);

      return response;
    }

    return null;
  }

  /** Common method to build match response. */
  private GeneratedSemanticType buildMatchResponse(
      CustomSemanticType matched,
      boolean isBuiltIn,
      String explanation,
      String suggestedAction,
      SemanticTypeGenerationRequest request) {

    GeneratedSemanticType.GeneratedSemanticTypeBuilder builder =
        GeneratedSemanticType.builder()
            .resultType("existing")
            .existingTypeMatch(matched.getSemanticType())
            .existingTypeDescription(matched.getDescription())
            .existingTypeIsBuiltIn(isBuiltIn)
            .suggestedAction(suggestedAction)
            .explanation(explanation)
            .semanticType(matched.getSemanticType())
            .description(matched.getDescription())
            .pluginType(matched.getPluginType())
            // Use the original request examples - no AI generation during similarity check
            .positiveContentExamples(request.getPositiveContentExamples())
            .negativeContentExamples(request.getNegativeContentExamples())
            .positiveHeaderExamples(request.getPositiveHeaderExamples())
            .negativeHeaderExamples(request.getNegativeHeaderExamples());

    if ("regex".equals(matched.getPluginType()) || "list".equals(matched.getPluginType())) {
      builder.existingTypePattern(extractPatternFromType(matched));
    }

    List<String> headerPatterns = extractHeaderPatternsFromType(matched);
    // Always set header patterns; use empty list when none
    builder.existingTypeHeaderPatterns(
        headerPatterns != null ? headerPatterns : java.util.Collections.emptyList());

    return builder.build();
  }

  /**
   * Generates examples for an existing semantic type after the user has confirmed they want to
   * proceed. This method should be called separately from the similarity check.
   */
  public GeneratedSemanticType generateExamplesForExistingType(
      String existingTypeName, SemanticTypeGenerationRequest originalRequest) throws Exception {
    List<CustomSemanticType> existingTypes = customSemanticTypeService.getAllCustomTypes();

    CustomSemanticType existingType =
        existingTypes.stream()
            .filter(t -> t.getSemanticType().equals(existingTypeName))
            .findFirst()
            .orElse(null);

    if (existingType == null) {
      throw new IllegalArgumentException("Existing type not found: " + existingTypeName);
    }

    // In the simplified workflow, just return the existing type information
    // without complex example generation
    return GeneratedSemanticType.builder()
        .resultType("existing")
        .semanticType(existingType.getSemanticType())
        .description(existingType.getDescription())
        .pluginType(existingType.getPluginType())
        .positiveContentExamples(originalRequest.getPositiveContentExamples())
        .negativeContentExamples(originalRequest.getNegativeContentExamples())
        .positiveHeaderExamples(originalRequest.getPositiveHeaderExamples())
        .negativeHeaderExamples(originalRequest.getNegativeHeaderExamples())
        .explanation("Using existing semantic type: " + existingType.getSemanticType())
        .build();
  }

  private String extractPatternFromType(CustomSemanticType type) {
    if ("regex".equals(type.getPluginType())
        && type.getValidLocales() != null
        && !type.getValidLocales().isEmpty()) {
      var locale = type.getValidLocales().get(0);
      if (locale.getMatchEntries() != null && !locale.getMatchEntries().isEmpty()) {
        return locale.getMatchEntries().get(0).getRegExpReturned();
      }
    } else if ("list".equals(type.getPluginType())
        && type.getContent() != null
        && type.getContent().getValues() != null) {
      int valueCount = type.getContent().getValues().size();
      if (valueCount <= 5) {
        return String.join(", ", type.getContent().getValues());
      } else {
        List<String> firstFive = type.getContent().getValues().subList(0, 5);
        return String.join(", ", firstFive) + " ... (" + valueCount + " values total)";
      }
    }
    // Return empty string to avoid nulls in simplified flow
    return "";
  }

  private List<String> extractHeaderPatternsFromType(CustomSemanticType type) {
    List<String> patterns = new ArrayList<>();
    if (type.getValidLocales() != null && !type.getValidLocales().isEmpty()) {
      var locale = type.getValidLocales().get(0);
      if (locale.getHeaderRegExps() != null) {
        for (var headerRegExp : locale.getHeaderRegExps()) {
          patterns.add(headerRegExp.getRegExp());
        }
      }
    }
    // Return empty list instead of null to simplify callers
    return patterns;
  }
}

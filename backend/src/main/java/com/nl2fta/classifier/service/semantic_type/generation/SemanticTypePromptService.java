package com.nl2fta.classifier.service.semantic_type.generation;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.PromptService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for building prompts for semantic type generation using Claude AI. Handles
 * different types of prompts including generation and refinement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypePromptService {

  private final CustomSemanticTypeService customSemanticTypeService;
  private final PromptService promptService;

  /**
   * Builds the main prompt for semantic type generation.
   *
   * @param request the semantic type generation request
   * @return formatted prompt string for Claude
   * @throws IOException if there's an error accessing custom types
   */
  public String buildGenerationPrompt(SemanticTypeGenerationRequest request) throws IOException {
    // Include ALL available types (built-in converted + user custom)
    List<CustomSemanticType> allTypes = customSemanticTypeService.getAllSemanticTypes();

    List<String> allTypeDescriptions =
        allTypes.stream().map(CustomSemanticType::getSemanticType).collect(Collectors.toList());

    log.info(
        "STEP 2 - NEW TYPE GENERATION: Found {} total types (built-in + custom) for context",
        allTypes.size());

    PromptService.SemanticTypeGenerationParams params =
        PromptService.SemanticTypeGenerationParams.builder()
            .typeName(request.getTypeName())
            .description(request.getDescription())
            .positiveContentExamples(request.getPositiveContentExamples())
            .negativeContentExamples(request.getNegativeContentExamples())
            .positiveHeaderExamples(request.getPositiveHeaderExamples())
            .negativeHeaderExamples(request.getNegativeHeaderExamples())
            .existingTypes(allTypeDescriptions)
            .columnHeader(request.getColumnHeader())
            .build();

    return promptService.buildSemanticTypeGenerationPrompt(params);
  }

  /** Builds prompt for regenerating data values based on example changes */
  public String buildRegenerateDataValuesPrompt(
      String semanticTypeName,
      String currentRegexPattern,
      List<String> positiveExamples,
      List<String> negativeExamples,
      String userDescription,
      String description)
      throws IOException {

    return promptService.buildRegenerateDataValuesPrompt(
        semanticTypeName,
        currentRegexPattern,
        positiveExamples,
        negativeExamples,
        userDescription,
        description);
  }

  /** Builds prompt for regenerating header patterns based on example changes */
  public String buildRegenerateHeaderValuesPrompt(
      String semanticTypeName,
      String currentHeaderPatterns,
      List<String> positiveExamples,
      List<String> negativeExamples,
      String userDescription)
      throws IOException {

    return promptService.buildRegenerateHeaderValuesPrompt(
        semanticTypeName,
        currentHeaderPatterns,
        positiveExamples,
        negativeExamples,
        userDescription);
  }

  /**
   * Builds a prompt for evaluating multiple similar semantic types to choose the best match.
   *
   * @param request the semantic type generation request
   * @param candidateTypes list of candidate semantic types to evaluate
   * @param similarityScores corresponding similarity scores for each candidate
   * @return formatted prompt for LLM to evaluate multiple matches
   */
  public String buildMultipleMatchEvaluationPrompt(
      SemanticTypeGenerationRequest request,
      List<CustomSemanticType> candidateTypes,
      List<VectorSimilaritySearchService.SimilaritySearchResult> similarityScores)
      throws IOException {

    StringBuilder prompt = new StringBuilder();
    prompt.append(
        "You are helping determine which existing semantic type best matches the user's requirements.\n\n");

    // User's request
    prompt.append("USER'S REQUEST:\n");
    prompt.append("Description: ").append(request.getDescription()).append("\n");

    if (request.getPositiveContentExamples() != null
        && !request.getPositiveContentExamples().isEmpty()) {
      prompt
          .append("Positive Content Examples: ")
          .append(String.join(", ", request.getPositiveContentExamples()))
          .append("\n");
    }

    if (request.getNegativeContentExamples() != null
        && !request.getNegativeContentExamples().isEmpty()) {
      prompt
          .append("Negative Content Examples: ")
          .append(String.join(", ", request.getNegativeContentExamples()))
          .append("\n");
    }

    if (request.getPositiveHeaderExamples() != null
        && !request.getPositiveHeaderExamples().isEmpty()) {
      prompt
          .append("Positive Header Examples: ")
          .append(String.join(", ", request.getPositiveHeaderExamples()))
          .append("\n");
    }

    prompt.append("\nCANDIDATE MATCHES (with similarity scores):\n");

    // Add each candidate with its similarity score
    for (int i = 0; i < candidateTypes.size(); i++) {
      CustomSemanticType type = candidateTypes.get(i);
      VectorSimilaritySearchService.SimilaritySearchResult score = similarityScores.get(i);

      prompt
          .append("\n")
          .append(i + 1)
          .append(". ")
          .append(type.getSemanticType())
          .append(" (Similarity: ")
          .append(String.format("%.1f%%", score.getSimilarityScore() * 100))
          .append(")\n");
      prompt.append("   Description: ").append(type.getDescription()).append("\n");

      if ("list".equals(type.getPluginType())
          && type.getContent() != null
          && type.getContent().getValues() != null) {
        List<String> values = type.getContent().getValues();
        if (values.size() <= 5) {
          prompt.append("   Values: ").append(String.join(", ", values)).append("\n");
        } else {
          prompt
              .append("   Sample values: ")
              .append(String.join(", ", values.subList(0, 5)))
              .append("...\n");
        }
      }
    }

    prompt.append("\nPlease analyze these candidates and determine:\n");
    prompt.append("1. Which candidate (if any) best matches the user's requirements\n");
    prompt.append("2. Whether the match is close enough to use the existing type\n");
    prompt.append(
        "3. Consider not just the similarity score, but also the semantic meaning and practical usage\n");
    prompt.append(
        "\nIMPORTANT: If none of the candidates are a good match, indicate that a new type should be created.\n");

    prompt.append("\nProvide your response in this XML format:\n");
    prompt.append("<similarity_check>\n");
    prompt.append("  <found_match>true/false</found_match>\n");
    prompt.append("  <matched_type>TYPE_NAME or null</matched_type>\n");
    prompt.append(
        "  <explanation>Brief explanation of why this is the best match or why none match</explanation>\n");
    prompt.append("  <suggested_action>use_existing/create_different</suggested_action>\n");
    prompt.append("</similarity_check>");

    return prompt.toString();
  }
}

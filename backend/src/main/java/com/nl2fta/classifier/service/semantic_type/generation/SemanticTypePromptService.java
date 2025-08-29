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
  private final com.nl2fta.classifier.service.generation.GenerationKnowledgeService knowledgeService;

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

    // Lightweight RAG: retrieve top-k banking snippets for augmentation when description hints banking
    String desc = request.getDescription() == null ? "" : request.getDescription().toLowerCase();
    String header = request.getColumnHeader() == null ? "" : request.getColumnHeader().toLowerCase();
    boolean looksBanking = desc.contains("bank");
    boolean looksTransactions =
        !looksBanking
            && (header.contains("transaction")
                || header.contains("txn")
                || header.contains("merchant")
                || header.contains("device")
                || header.contains("ip address")
                || header.contains("channel"));
    boolean looksExtension =
        !looksBanking
            && !looksTransactions
            && (
                header.contains("iban")
                    || header.contains("duns")
                    || header.contains("bsn")
                    || header.contains("sin")
                    || header.contains("ein")
                    || header.contains("npi")
                    || header.contains("cusip")
                    || header.contains("isin")
                    || header.contains("ean")
                    || header.contains("isbn")
                    || header.contains("upc")
                    || header.contains("imei")
                    || header.contains("guid")
                    || header.contains("uuid")
                    || header.contains("vin")
                    || header.contains("mac")
                    || header.contains("airport")
                    || header.contains("iata")
                    || header.contains("currency")
                    || header.contains("language")
                    || header.contains("continent")
                    || header.contains("nationality")
                    || header.contains("timezone")
                    || header.contains("postal")
                    || header.contains("zip")
                    || header.contains("color")
                    || header.contains("hex")
                    || header.contains("naics")
                    || header.contains("industry")
                    || header.contains("marital")
                    || header.contains("race")
                    || header.contains("age range")
                    || header.contains("age_group")
                    || header.contains("longitude")
                    || header.contains("latitude")
                    || header.contains("easting")
                    || header.contains("northing")
                    || header.contains("street")
                    || header.contains("address")
                    || header.contains("geojson")
                    || header.contains("wkt")
                    || header.contains("uri")
                    || header.contains("url")
                    || header.contains("email")
                    || header.contains("telephone")
                    || header.contains("phone")
                    || header.contains("book_number")
                    || header.contains("publishing_number")
                    || header.contains("european_article_num")
                    || header.contains("universal_product_numbers")
              );

    boolean looksInsurance =
        !looksBanking
            && !looksTransactions
            && (header.contains("policy")
                || header.contains("premium")
                || header.contains("claims")
                || header.contains("lapse")
                || header.contains("renewal")
                || header.contains("distribution_channel")
                || header.contains("seniority")
                || header.contains("date_birth")
                || header.contains("driving_licence")
                || header.contains("type_risk")
                || header.contains("year_matriculation")
                || header.contains("cylinder_capacity")
                || header.contains("value_vehicle")
                || header.contains("type_fuel")
                || header.contains("n_doors")
                || header.contains("length")
                || header.contains("weight"));

    boolean looksTelco =
        !looksBanking
            && !looksTransactions
            && !looksExtension
            && !looksInsurance
            && (header.contains("rsrp")
                || header.contains("rsrq")
                || header.contains("sinr")
                || header.contains("cqi")
                || header.contains("throughput")
                || header.contains("uplink")
                || header.contains("downlink")
                || header.contains("latency")
                || header.contains("jitter")
                || header.contains("packet_loss")
                || header.contains("prb")
                || header.contains("handover")
                || header.contains("rrc")
                || header.contains("cell_id")
                || header.contains("pci")
                || header.contains("tac")
                || header.contains("lac")
                || header.contains("mcc")
                || header.contains("mnc")
                || header.contains("nrarfcn")
                || header.contains("earfcn")
                || header.contains("band")
                || header.contains("timestamp")
                || header.contains("datetime"));

    boolean looksTelcoChurn =
        !looksBanking
            && !looksTransactions
            && !looksExtension
            && !looksInsurance
            && !looksTelco
            && (header.contains("customerid")
                || header.contains("customer_id")
                || header.contains("churn")
                || header.contains("tenure")
                || header.contains("monthlycharges")
                || header.contains("totalcharges")
                || header.contains("contract")
                || header.contains("paymentmethod")
                || header.contains("paperlessbilling")
                || header.contains("internetservice")
                || header.contains("phoneservice")
                || header.contains("streaming")
                || header.contains("onlinesecurity")
                || header.contains("onlinebackup")
                || header.contains("deviceprotection")
                || header.contains("techsupport")
                || header.contains("multiplelines")
                || header.contains("partner")
                || header.contains("dependents")
                || header.contains("seniorcitizen")
                || header.contains("gender"));

    // Build a richer query using both description and header for better retrieval
    String retrievalQuery = (request.getDescription() == null ? "" : request.getDescription())
        + "\nHEADER:" + (request.getColumnHeader() == null ? "" : request.getColumnHeader());

    if (looksBanking) {
      try {
        // Initialize once (idempotent) and retrieve
        knowledgeService.initializeBankingKnowledge();
        var results = knowledgeService.retrieveBanking(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          // Prepend augmentation into description to bias the LLM
          String augmented = "[BANKING_KNOWLEDGE]\n- " + String.join("\n- ", aug) + "\n[/BANKING_KNOWLEDGE]\n" + request.getDescription();
          // Rebuild a new request with augmented description (no toBuilder on this DTO)
          request = com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
              .typeName(request.getTypeName())
              .description(augmented)
              .positiveContentExamples(request.getPositiveContentExamples())
              .negativeContentExamples(request.getNegativeContentExamples())
              .positiveHeaderExamples(request.getPositiveHeaderExamples())
              .negativeHeaderExamples(request.getNegativeHeaderExamples())
              .checkExistingTypes(request.isCheckExistingTypes())
              .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
              .generateExamplesForExistingType(request.getGenerateExamplesForExistingType())
              .columnHeader(request.getColumnHeader())
              .detectedBaseType(request.getDetectedBaseType())
              .detectedPattern(request.getDetectedPattern())
              .build();
        }
      } catch (Exception ignored) {}
    } else if (looksTransactions) {
      try {
        knowledgeService.initializeTransactionsKnowledge();
        var results = knowledgeService.retrieveTransactions(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          String augmented =
              "[TRANSACTIONS_KNOWLEDGE]\n- "
                  + String.join("\n- ", aug)
                  + "\n[/TRANSACTIONS_KNOWLEDGE]\n"
                  + request.getDescription();
          request =
              com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
                  .typeName(request.getTypeName())
                  .description(augmented)
                  .positiveContentExamples(request.getPositiveContentExamples())
                  .negativeContentExamples(request.getNegativeContentExamples())
                  .positiveHeaderExamples(request.getPositiveHeaderExamples())
                  .negativeHeaderExamples(request.getNegativeHeaderExamples())
                  .checkExistingTypes(request.isCheckExistingTypes())
                  .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
                  .generateExamplesForExistingType(
                      request.getGenerateExamplesForExistingType())
                  .columnHeader(request.getColumnHeader())
                  .detectedBaseType(request.getDetectedBaseType())
                  .detectedPattern(request.getDetectedPattern())
                  .build();
        }
      } catch (Exception ignored) {}
    } else if (looksExtension) {
      try {
        knowledgeService.initializeExtensionKnowledge();
        var results = knowledgeService.retrieveExtension(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          String augmented =
              "[EXTENSION_KNOWLEDGE]\n- " + String.join("\n- ", aug) + "\n[/EXTENSION_KNOWLEDGE]\n" + request.getDescription();
          request =
              com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
                  .typeName(request.getTypeName())
                  .description(augmented)
                  .positiveContentExamples(request.getPositiveContentExamples())
                  .negativeContentExamples(request.getNegativeContentExamples())
                  .positiveHeaderExamples(request.getPositiveHeaderExamples())
                  .negativeHeaderExamples(request.getNegativeHeaderExamples())
                  .checkExistingTypes(request.isCheckExistingTypes())
                  .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
                  .generateExamplesForExistingType(
                      request.getGenerateExamplesForExistingType())
                  .columnHeader(request.getColumnHeader())
                  .detectedBaseType(request.getDetectedBaseType())
                  .detectedPattern(request.getDetectedPattern())
                  .build();
        }
      } catch (Exception ignored) {}
    } else if (looksInsurance) {
      try {
        knowledgeService.initializeInsuranceKnowledge();
        var results = knowledgeService.retrieveInsurance(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          String augmented =
              "[INSURANCE_KNOWLEDGE]\n- " + String.join("\n- ", aug) + "\n[/INSURANCE_KNOWLEDGE]\n" + request.getDescription();
          request =
              com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
                  .typeName(request.getTypeName())
                  .description(augmented)
                  .positiveContentExamples(request.getPositiveContentExamples())
                  .negativeContentExamples(request.getNegativeContentExamples())
                  .positiveHeaderExamples(request.getPositiveHeaderExamples())
                  .negativeHeaderExamples(request.getNegativeHeaderExamples())
                  .checkExistingTypes(request.isCheckExistingTypes())
                  .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
                  .generateExamplesForExistingType(
                      request.getGenerateExamplesForExistingType())
                  .columnHeader(request.getColumnHeader())
                  .detectedBaseType(request.getDetectedBaseType())
                  .detectedPattern(request.getDetectedPattern())
                  .build();
        }
      } catch (Exception ignored) {}
    } else if (looksTelco) {
      try {
        knowledgeService.initializeTelcoKnowledge();
        var results = knowledgeService.retrieveTelco(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          String augmented =
              "[TELCO_KNOWLEDGE]\n- " + String.join("\n- ", aug) + "\n[/TELCO_KNOWLEDGE]\n" + request.getDescription();
          request =
              com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
                  .typeName(request.getTypeName())
                  .description(augmented)
                  .positiveContentExamples(request.getPositiveContentExamples())
                  .negativeContentExamples(request.getNegativeContentExamples())
                  .positiveHeaderExamples(request.getPositiveHeaderExamples())
                  .negativeHeaderExamples(request.getNegativeHeaderExamples())
                  .checkExistingTypes(request.isCheckExistingTypes())
                  .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
                  .generateExamplesForExistingType(
                      request.getGenerateExamplesForExistingType())
                  .columnHeader(request.getColumnHeader())
                  .detectedBaseType(request.getDetectedBaseType())
                  .detectedPattern(request.getDetectedPattern())
                  .build();
        }
      } catch (Exception ignored) {}
    } else if (looksTelcoChurn) {
      try {
        knowledgeService.initializeTelcoChurnKnowledge();
        var results = knowledgeService.retrieveTelcoChurn(retrievalQuery, 256);
        if (!results.isEmpty()) {
          List<String> aug = results.stream().map(r -> r.text).collect(Collectors.toList());
          String augmented =
              "[TELCO_CHURN_KNOWLEDGE]\n- " + String.join("\n- ", aug) + "\n[/TELCO_CHURN_KNOWLEDGE]\n" + request.getDescription();
          request =
              com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest.builder()
                  .typeName(request.getTypeName())
                  .description(augmented)
                  .positiveContentExamples(request.getPositiveContentExamples())
                  .negativeContentExamples(request.getNegativeContentExamples())
                  .positiveHeaderExamples(request.getPositiveHeaderExamples())
                  .negativeHeaderExamples(request.getNegativeHeaderExamples())
                  .checkExistingTypes(request.isCheckExistingTypes())
                  .proceedDespiteSimilarity(request.isProceedDespiteSimilarity())
                  .generateExamplesForExistingType(
                      request.getGenerateExamplesForExistingType())
                  .columnHeader(request.getColumnHeader())
                  .detectedBaseType(request.getDetectedBaseType())
                  .detectedPattern(request.getDetectedPattern())
                  .build();
        }
      } catch (Exception ignored) {}
    }

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

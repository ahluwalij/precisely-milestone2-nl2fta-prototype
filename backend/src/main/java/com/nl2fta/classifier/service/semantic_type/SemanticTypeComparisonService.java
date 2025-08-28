package com.nl2fta.classifier.service.semantic_type;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.nl2fta.classifier.dto.semantic_type.SemanticTypeComparison;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.PromptService;
import com.nl2fta.classifier.service.aws.AwsBedrockService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeRegistryService;
import com.nl2fta.classifier.service.vector.VectorSimilaritySearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Service for comparing semantic types and generating detailed comparison analysis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeComparisonService {

  private final AwsBedrockService awsBedrockService;
  private final PromptService promptService;
  private final SemanticTypeRegistryService registryService;

  /** Generate a detailed comparison between a user's semantic type request and an existing type. */
  public SemanticTypeComparison compareSemanticTypes(
      SemanticTypeGenerationRequest userRequest,
      VectorSimilaritySearchService.SimilaritySearchResult existingType) {
    return compareSemanticTypes(userRequest, existingType, null);
  }

  /**
   * Generate a detailed comparison between a user's semantic type request and an existing type,
   * with optional context about other candidate types.
   */
  public SemanticTypeComparison compareSemanticTypes(
      SemanticTypeGenerationRequest userRequest,
      VectorSimilaritySearchService.SimilaritySearchResult existingType,
      List<VectorSimilaritySearchService.SimilaritySearchResult> allCandidates) {

    try {
      // Build the prompt parameters
      Map<String, String> parameters = new HashMap<>();
      parameters.put(
          "USER_TYPE_NAME", userRequest.getTypeName() != null ? userRequest.getTypeName() : "");
      parameters.put(
          "USER_DESCRIPTION",
          userRequest.getDescription() != null ? userRequest.getDescription() : "");
      parameters.put("EXISTING_TYPE_NAME", existingType.getSemanticType());

      // Get the ACTUAL built-in type description instead of using potentially corrupted vector data
      String actualDescription = registryService.getDescription(existingType.getSemanticType());
      parameters.put("EXISTING_DESCRIPTION", actualDescription);
      parameters.put("EXISTING_PLUGIN_TYPE", existingType.getPluginType());
      parameters.put(
          "SIMILARITY_PERCENTAGE", String.format("%.1f", existingType.getSimilarityScore() * 100));

      // Add examples if available
      if (userRequest.getPositiveContentExamples() != null
          && !userRequest.getPositiveContentExamples().isEmpty()) {
        parameters.put(
            "USER_EXAMPLES", String.join(", ", userRequest.getPositiveContentExamples()));
      }

      if (userRequest.getPositiveHeaderExamples() != null
          && !userRequest.getPositiveHeaderExamples().isEmpty()) {
        parameters.put(
            "USER_HEADER_EXAMPLES", String.join(", ", userRequest.getPositiveHeaderExamples()));
      }

      // Add other candidates context if available
      if (allCandidates != null && allCandidates.size() > 1) {
        StringBuilder otherCandidates = new StringBuilder();
        otherCandidates
            .append("\n\nNote: This type was selected as the best match from ")
            .append(allCandidates.size())
            .append(" candidates:\n");
        for (VectorSimilaritySearchService.SimilaritySearchResult candidate : allCandidates) {
          // Get the actual built-in type description for candidates too
          String candidateDescription = registryService.getDescription(candidate.getSemanticType());
          otherCandidates
              .append("- ")
              .append(candidate.getSemanticType())
              .append(" (Similarity: ")
              .append(String.format("%.1f%%", candidate.getSimilarityScore() * 100))
              .append("): ")
              .append(candidateDescription)
              .append("\n");
        }
        parameters.put("OTHER_CANDIDATES", otherCandidates.toString());
      }

      // Load and fill the prompt template
      String promptTemplate = promptService.loadPromptTemplate("semantic-type-comparison");

      // Replace all parameters in the template, guarding against null replacements
      for (Map.Entry<String, String> entry : parameters.entrySet()) {
        String replacement = entry.getValue() != null ? entry.getValue() : "";
        promptTemplate = promptTemplate.replace("{{" + entry.getKey() + "}}", replacement);
      }

      // Handle conditional sections
      if (parameters.containsKey("USER_EXAMPLES")) {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#USER_EXAMPLES\\}\\}(.*?)\\{\\{/USER_EXAMPLES\\}\\}", "$1");
      } else {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#USER_EXAMPLES\\}\\}.*?\\{\\{/USER_EXAMPLES\\}\\}", "");
      }

      if (parameters.containsKey("USER_HEADER_EXAMPLES")) {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#USER_HEADER_EXAMPLES\\}\\}(.*?)\\{\\{/USER_HEADER_EXAMPLES\\}\\}", "$1");
      } else {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#USER_HEADER_EXAMPLES\\}\\}.*?\\{\\{/USER_HEADER_EXAMPLES\\}\\}", "");
      }

      if (parameters.containsKey("EXISTING_PLUGIN_TYPE")) {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#EXISTING_PLUGIN_TYPE\\}\\}(.*?)\\{\\{/EXISTING_PLUGIN_TYPE\\}\\}", "$1");
      } else {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#EXISTING_PLUGIN_TYPE\\}\\}.*?\\{\\{/EXISTING_PLUGIN_TYPE\\}\\}", "");
      }

      if (parameters.containsKey("OTHER_CANDIDATES")) {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#OTHER_CANDIDATES\\}\\}(.*?)\\{\\{/OTHER_CANDIDATES\\}\\}", "$1");
      } else {
        promptTemplate =
            promptTemplate.replaceAll(
                "\\{\\{#OTHER_CANDIDATES\\}\\}.*?\\{\\{/OTHER_CANDIDATES\\}\\}", "");
      }

      // Get comparison from LLM
      log.debug("Performing semantic type comparison against existing types");
      String response;
      try {
        response = awsBedrockService.invokeClaudeForSemanticTypeGeneration(promptTemplate);
      } catch (Exception ex) {
        // In simplified flow, fall back to basic comparison if LLM invocation fails
        log.warn("LLM comparison skipped or failed, returning basic comparison");
        return buildBasicComparison(userRequest, existingType);
      }

      // Parse the response
      return parseComparisonResponse(response, existingType);

    } catch (Exception e) {
      log.error("Error generating semantic type comparison", e);
      // Return a basic comparison on error
      return buildBasicComparison(userRequest, existingType);
    }
  }

  /** Parse the XML response from the LLM into a SemanticTypeComparison object. */
  private SemanticTypeComparison parseComparisonResponse(
      String xmlResponse, VectorSimilaritySearchService.SimilaritySearchResult existingType) {
    try {
      // Extract XML content
      String xml = extractXmlContent(xmlResponse, "comparison");
      if (xml == null) {
        return buildBasicComparison(null, existingType);
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

      Element root = doc.getDocumentElement();

      SemanticTypeComparison comparison = new SemanticTypeComparison();
      comparison.setExistingTypeName(existingType.getSemanticType());
      comparison.setExistingTypeDescription(existingType.getDescription());
      comparison.setSimilarityScore(existingType.getSimilarityScore());

      // Parse summary (now contains both similarities and differences in 2 sentences)
      String summary = getTextContent(root, "summary");
      comparison.setSummary(summary);

      // Split summary into similarities and differences
      if (summary != null && !summary.isEmpty()) {
        String[] sentences = summary.split("\\. ");
        if (sentences.length >= 1) {
          comparison.setSimilarities(List.of(sentences[0].trim()));
        }
        if (sentences.length >= 2) {
          SemanticTypeComparison.Difference diff = new SemanticTypeComparison.Difference();
          diff.setAspect("Key Difference");
          diff.setDescription(sentences[1].trim().replaceAll("\\.$", ""));
          comparison.setDifferences(List.of(diff));
        } else {
          comparison.setDifferences(new ArrayList<>());
        }
      } else {
        comparison.setSimilarities(new ArrayList<>());
        comparison.setDifferences(new ArrayList<>());
      }

      // Parse recommendation
      Element recommendationEl = (Element) root.getElementsByTagName("recommendation").item(0);
      if (recommendationEl != null) {
        String useExisting = getTextContent(recommendationEl, "useExisting");
        comparison.setRecommendUseExisting("true".equalsIgnoreCase(useExisting));
        comparison.setRecommendationReason(
            comparison.isRecommendUseExisting()
                ? "The existing type matches your requirements well enough to use directly."
                : "The types are different enough that you should create a new semantic type.");
      }

      return comparison;

    } catch (Exception e) {
      log.error("Error parsing comparison response", e);
      return buildBasicComparison(null, existingType);
    }
  }

  /** Parse differences from the XML response. */
  private List<SemanticTypeComparison.Difference> parseDifferences(Element root) {
    List<SemanticTypeComparison.Difference> differences = new ArrayList<>();

    NodeList diffNodes = root.getElementsByTagName("difference");
    for (int i = 0; i < diffNodes.getLength(); i++) {
      Element diffEl = (Element) diffNodes.item(i);

      SemanticTypeComparison.Difference diff = new SemanticTypeComparison.Difference();
      diff.setAspect(getTextContent(diffEl, "aspect"));
      diff.setDescription(getTextContent(diffEl, "description"));

      differences.add(diff);
    }

    return differences;
  }

  /** Build a basic comparison when LLM is unavailable. */
  private SemanticTypeComparison buildBasicComparison(
      SemanticTypeGenerationRequest userRequest,
      VectorSimilaritySearchService.SimilaritySearchResult existingType) {

    SemanticTypeComparison comparison = new SemanticTypeComparison();
    comparison.setExistingTypeName(existingType.getSemanticType());
    comparison.setExistingTypeDescription(existingType.getDescription());
    comparison.setSimilarityScore(existingType.getSimilarityScore());

    // Basic similarities
    List<String> similarities = new ArrayList<>();
    similarities.add("Both types have similar semantic meaning based on vector analysis");
    similarities.add(
        String.format("Vector similarity score: %.1f%%", existingType.getSimilarityScore() * 100));
    comparison.setSimilarities(similarities);

    // Basic differences
    List<SemanticTypeComparison.Difference> differences = new ArrayList<>();
    if (existingType.getSimilarityScore() < 1.0) {
      SemanticTypeComparison.Difference diff = new SemanticTypeComparison.Difference();
      diff.setAspect("Similarity");
      diff.setDescription(
          "Types are not 100% identical, there may be subtle differences in scope or usage");
      differences.add(diff);
    }
    comparison.setDifferences(differences);

    // Recommendation
    comparison.setRecommendUseExisting(existingType.getSimilarityScore() >= 0.90);
    comparison.setRecommendationReason(
        existingType.getSimilarityScore() >= 0.90
            ? "High similarity suggests the existing type meets your needs"
            : "Moderate similarity - review if existing type fully meets your requirements");

    // Summary
    comparison.setSummary(
        String.format(
            "Found existing type '%s' with %.1f%% similarity",
            existingType.getSemanticType(), existingType.getSimilarityScore() * 100));

    return comparison;
  }

  /** Parse a list of text elements from XML. */
  private List<String> parseTextList(Element parent, String containerTag, String itemTag) {
    List<String> items = new ArrayList<>();

    NodeList containerNodes = parent.getElementsByTagName(containerTag);
    if (containerNodes.getLength() > 0) {
      Element container = (Element) containerNodes.item(0);
      NodeList itemNodes = container.getElementsByTagName(itemTag);

      for (int i = 0; i < itemNodes.getLength(); i++) {
        String text = itemNodes.item(i).getTextContent().trim();
        if (!text.isEmpty()) {
          items.add(text);
        }
      }
    }

    return items;
  }

  /** Get text content of a child element. */
  private String getTextContent(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() > 0) {
      return nodes.item(0).getTextContent().trim();
    }
    return "";
  }

  /** Extract XML content from the response. */
  private String extractXmlContent(String response, String rootTag) {
    String startTag = "<" + rootTag + ">";
    String endTag = "</" + rootTag + ">";

    int startIndex = response.indexOf(startTag);
    int endIndex = response.lastIndexOf(endTag);

    if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
      return response.substring(startIndex, endIndex + endTag.length());
    }

    return null;
  }
}

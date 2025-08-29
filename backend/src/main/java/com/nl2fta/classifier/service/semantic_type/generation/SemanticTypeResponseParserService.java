package com.nl2fta.classifier.service.semantic_type.generation;

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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.HeaderPattern;
import com.nl2fta.classifier.dto.semantic_type.PatternUpdateResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for parsing Claude AI responses for semantic type generation. Now supports
 * both XML and JSON response formats with fallback handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTypeResponseParserService {

  private final ObjectMapper objectMapper;

  /**
   * Parses the main Claude response for semantic type generation. Supports both XML and JSON
   * formats with automatic detection.
   */
  public GeneratedSemanticType parseGenerationResponse(
      String claudeResponse, SemanticTypeGenerationRequest request) {
    log.debug("Raw Claude response for parsing: {}", claudeResponse);
    log.debug(
        "parseGenerationResponse: descriptionPreview='{}'",
        request != null && request.getDescription() != null
            ? truncateForLog(request.getDescription(), 160)
            : "null");
    try {
      // Try XML format first
      return parseXmlResponse(claudeResponse, request);
    } catch (Exception xmlException) {
      log.debug("XML parsing failed, trying JSON fallback", xmlException);
      try {
        // Fallback to JSON if XML parsing fails
        return parseJsonResponse(claudeResponse, request);
      } catch (Exception jsonException) {
        log.error("Both XML and JSON parsing failed for Claude response", jsonException);
        log.error("Raw Claude response that failed parsing: {}", claudeResponse);
        return buildErrorResponse("Failed to parse Claude response - no valid JSON or XML found");
      }
    }
  }

  private GeneratedSemanticType parseXmlResponse(
      String claudeResponse, SemanticTypeGenerationRequest request) {
    try {
      String cleanXml = extractXmlFromResponse(claudeResponse);
      if (cleanXml == null || cleanXml.trim().isEmpty()) {
        log.warn("extractXmlFromResponse returned null/empty for response. Falling through.");
        throw new RuntimeException("No XML root element found in response");
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc =
          builder.parse(new ByteArrayInputStream(cleanXml.getBytes(StandardCharsets.UTF_8)));

      Element root = doc.getDocumentElement();

      // Parse basic info
      Element basicInfo = (Element) root.getElementsByTagName("basicInfo").item(0);
      String semanticType = getElementText(basicInfo, "semanticType");
      String description = getElementText(basicInfo, "description");
      String pluginType = getElementText(basicInfo, "pluginType");
      String regexPattern = getElementText(basicInfo, "regexPattern");
      List<String> listValues = parseListValues(basicInfo);
      String backout = getElementText(basicInfo, "backout");
      log.debug("Parsed backout pattern: '{}'", backout);

      // Generate fallback backout pattern for list types if Claude didn't provide one
      if ("list".equals(pluginType)
          && (backout == null || backout.trim().isEmpty())
          && listValues != null
          && !listValues.isEmpty()) {
        backout = generateFallbackBackoutPattern(listValues);
        log.info("Generated fallback backout pattern for list type: '{}'", backout);
      }
      int confidenceThreshold = parseIntValue(getElementText(basicInfo, "confidenceThreshold"), 95);
      int priority = 2000; // Always use 2000 as required by FTA

      // Parse header patterns
      List<HeaderPattern> headerPatterns = parseXmlHeaderPatterns(root);

      // Parse examples
      Element examples = (Element) root.getElementsByTagName("examples").item(0);
      List<String> positiveContentExamples = parseExamplesList(examples, "positiveContentExamples");
      List<String> negativeContentExamples = parseExamplesList(examples, "negativeContentExamples");
      List<String> positiveHeaderExamples = parseExamplesList(examples, "positiveHeaderExamples");
      List<String> negativeHeaderExamples = parseExamplesList(examples, "negativeHeaderExamples");

      String explanation = getElementText(root, "explanation");

      log.info("Successfully parsed XML response with {} header patterns", headerPatterns.size());

      return GeneratedSemanticType.builder()
          .resultType("generated")
          .semanticType(semanticType)
          .description(description)
          .pluginType(pluginType)
          .regexPattern(regexPattern)
          .listValues(listValues)
          .backout(backout)
          .headerPatterns(headerPatterns)
          .confidenceThreshold(confidenceThreshold)
          .priority(priority)
          .positiveContentExamples(cleanExamplesList(positiveContentExamples))
          .negativeContentExamples(cleanExamplesList(negativeContentExamples))
          .positiveHeaderExamples(cleanExamplesList(positiveHeaderExamples))
          .negativeHeaderExamples(cleanExamplesList(negativeHeaderExamples))
          .explanation(explanation)
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse XML response: {}", e.getMessage());
      throw new RuntimeException("Failed to parse XML response", e);
    }
  }

  private GeneratedSemanticType parseJsonResponse(
      String claudeResponse, SemanticTypeGenerationRequest request) {
    try {
      String jsonStr = extractJsonFromResponse(claudeResponse);
      if (jsonStr == null) {
        return buildErrorResponse("Failed to parse Claude response - no valid JSON or XML found");
      }

      Map<String, Object> parsed;
      try {
        parsed = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
      } catch (Exception e) {
        log.error("Failed to parse JSON response", e);
        log.error("Raw Claude response that failed JSON parsing: {}", claudeResponse);
        return buildErrorResponse("Failed to parse Claude JSON response: " + e.getMessage());
      }

      String pluginType = (String) parsed.get("pluginType");
      List<String> listValues = castToStringList(parsed.get("listValues"));
      String backout = (String) parsed.get("backout");

      // Generate fallback backout pattern for list types if Claude didn't provide one
      if ("list".equals(pluginType)
          && (backout == null || backout.trim().isEmpty())
          && listValues != null
          && !listValues.isEmpty()) {
        backout = generateFallbackBackoutPattern(listValues);
        log.info("Generated fallback backout pattern for list type: '{}'", backout);
      }

      return GeneratedSemanticType.builder()
          .resultType("generated")
          .semanticType((String) parsed.get("semanticType"))
          .description((String) parsed.get("description"))
          .pluginType(pluginType)
          .regexPattern((String) parsed.get("regexPattern"))
          .listValues(listValues)
          .backout(backout)
          .headerPatterns(parseHeaderPatterns(castToMapList(parsed.get("headerPatterns"))))
          .confidenceThreshold(parseConfidenceThreshold(parsed.get("confidenceThreshold")))
          .priority(2000) // Always use 2000 as required by FTA
          .positiveContentExamples(
              cleanExamplesList(castToStringList(parsed.get("positiveContentExamples"))))
          .negativeContentExamples(
              cleanExamplesList(castToStringList(parsed.get("negativeContentExamples"))))
          .positiveHeaderExamples(
              cleanExamplesList(castToStringList(parsed.get("positiveHeaderExamples"))))
          .negativeHeaderExamples(
              cleanExamplesList(castToStringList(parsed.get("negativeHeaderExamples"))))
          .explanation((String) parsed.get("explanation"))
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON response", e);
    }
  }

  private String truncateForLog(String s, int max) {
    if (s == null) return null;
    if (s.length() <= max) return s;
    return s.substring(0, Math.max(0, max)) + "…";
  }

  /** Extracts XML content from Claude response, handling any markdown formatting. */
  private String extractXmlFromResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      throw new IllegalArgumentException("Empty response");
    }

    String trimmed = response.trim();

    // Remove markdown code blocks if present
    if (trimmed.startsWith("```xml") || trimmed.startsWith("```")) {
      int startIndex = trimmed.indexOf('\n') + 1;
      int endIndex = trimmed.lastIndexOf("```");
      if (endIndex > startIndex) {
        trimmed = trimmed.substring(startIndex, endIndex).trim();
      }
    }

    // Find the XML content
    int xmlStart = trimmed.indexOf("<semanticType");
    int xmlEnd = trimmed.lastIndexOf("</semanticType>") + "</semanticType>".length();

    if (xmlStart >= 0 && xmlEnd > xmlStart) {
      String xmlContent = trimmed.substring(xmlStart, xmlEnd);
      // Sanitize XML entities to prevent parsing errors
      xmlContent = sanitizeXmlEntities(xmlContent);
      return xmlContent;
    }

    return trimmed;
  }

  /** Sanitizes XML content by escaping invalid entities and characters */
  private String sanitizeXmlEntities(String xml) {
    if (xml == null) {
      return null;
    }

    // First, handle common problematic characters that break XML parsing
    // but preserve existing valid XML entities
    xml = xml.replaceAll("&(?!(amp|lt|gt|quot|apos);)", "&amp;");

    return xml;
  }

  /** Parse header patterns from XML. */
  private List<HeaderPattern> parseXmlHeaderPatterns(Element root) {
    List<HeaderPattern> headerPatterns = new ArrayList<>();

    NodeList headerPatternsNode = root.getElementsByTagName("headerPatterns");
    if (headerPatternsNode.getLength() == 0) {
      return headerPatterns;
    }

    Element headerPatternsElement = (Element) headerPatternsNode.item(0);
    NodeList patternNodes = headerPatternsElement.getElementsByTagName("pattern");

    for (int i = 0; i < patternNodes.getLength(); i++) {
      Element patternElement = (Element) patternNodes.item(i);

      try {
        String regExp = getElementText(patternElement, "regExp");
        int confidence = parseIntValue(getElementText(patternElement, "confidence"), 95);
        boolean mandatory = parseBooleanValue(getElementText(patternElement, "mandatory"), true);
        String rationale = getElementText(patternElement, "rationale");

        List<String> positiveExamples = parseXmlExamplesList(patternElement, "positiveExamples");
        List<String> negativeExamples = parseXmlExamplesList(patternElement, "negativeExamples");

        if (regExp != null && !regExp.trim().isEmpty()) {
          HeaderPattern headerPattern =
              HeaderPattern.builder()
                  .regExp(regExp)
                  .confidence(confidence)
                  .mandatory(mandatory)
                  .positiveExamples(positiveExamples)
                  .negativeExamples(negativeExamples)
                  .rationale(rationale)
                  .build();

          headerPatterns.add(headerPattern);
        }
      } catch (Exception e) {
        log.warn("Failed to parse XML header pattern at index {}: {}", i, e.getMessage());
      }
    }

    log.info("Parsed {} header patterns from XML response", headerPatterns.size());
    return headerPatterns;
  }

  /** Helper method to get text content from an XML element. */
  private String getElementText(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() > 0) {
      Node node = nodes.item(0);
      return node.getTextContent() != null ? node.getTextContent().trim() : null;
    }
    return null;
  }

  /** Parse list values from XML. */
  private List<String> parseListValues(Element basicInfo) {
    List<String> values = new ArrayList<>();
    NodeList listValuesNode = basicInfo.getElementsByTagName("listValues");

    if (listValuesNode.getLength() > 0) {
      Element listValuesElement = (Element) listValuesNode.item(0);
      NodeList valueNodes = listValuesElement.getElementsByTagName("value");

      for (int i = 0; i < valueNodes.getLength(); i++) {
        String value = valueNodes.item(i).getTextContent();
        if (value != null && !value.trim().isEmpty()) {
          values.add(value.trim());
        }
      }
    }

    return values;
  }

  /** Parse examples list from XML. */
  private List<String> parseExamplesList(Element examples, String sectionName) {
    List<String> examplesList = new ArrayList<>();

    if (examples == null) {
      return examplesList;
    }

    NodeList sectionNodes = examples.getElementsByTagName(sectionName);
    if (sectionNodes.getLength() > 0) {
      Element sectionElement = (Element) sectionNodes.item(0);
      NodeList exampleNodes = sectionElement.getElementsByTagName("example");

      for (int i = 0; i < exampleNodes.getLength(); i++) {
        String example = exampleNodes.item(i).getTextContent();
        if (example != null && !example.trim().isEmpty()) {
          examplesList.add(example.trim());
        }
      }
    }

    return examplesList;
  }

  /** Parse examples list from XML for header patterns. */
  private List<String> parseXmlExamplesList(Element patternElement, String sectionName) {
    List<String> examplesList = new ArrayList<>();

    NodeList sectionNodes = patternElement.getElementsByTagName(sectionName);
    if (sectionNodes.getLength() > 0) {
      Element sectionElement = (Element) sectionNodes.item(0);
      NodeList exampleNodes = sectionElement.getElementsByTagName("example");

      for (int i = 0; i < exampleNodes.getLength(); i++) {
        String example = exampleNodes.item(i).getTextContent();
        if (example != null && !example.trim().isEmpty()) {
          examplesList.add(example.trim());
        }
      }
    }

    return examplesList;
  }

  /** Parses Claude response for similarity checking. */
  public Map<String, Object> parseSimilarityCheckResponse(String claudeResponse) {
    try {
      String jsonStr = extractJsonFromResponse(claudeResponse);
      if (jsonStr != null) {
        return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
      }
    } catch (Exception e) {
      log.error("Failed to parse similarity check response", e);
    }
    return null;
  }

  /** Helper method to safely cast Object to List<String>. */
  @SuppressWarnings("unchecked")
  private List<String> castToStringList(Object obj) {
    if (obj instanceof List) {
      return (List<String>) obj;
    }
    return new ArrayList<>();
  }

  /** Helper method to safely cast Object to List<Map<String, Object>>. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> castToMapList(Object obj) {
    if (obj instanceof List) {
      return (List<Map<String, Object>>) obj;
    }
    return new ArrayList<>();
  }

  /** Helper method to safely cast Object to Map<String, Object>. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> castToMap(Object obj) {
    if (obj instanceof Map) {
      return (Map<String, Object>) obj;
    }
    return new HashMap<>();
  }

  /**
   * Parses Claude XML response for similarity checking. Expected format: <similarity_check>
   * <found_match>true/false</found_match> <matched_type>TYPE_NAME</matched_type>
   * <confidence>0-100</confidence> <suggested_action>use_existing</suggested_action>
   * <explanation>explanation text</explanation> </similarity_check>
   */
  public Map<String, Object> parseSimilarityCheckXmlResponse(String claudeResponse) {
    try {
      // Try both formats for backward compatibility
      String xmlContent = extractXmlFromResponse(claudeResponse, "similarity_check");
      if (xmlContent == null) {
        xmlContent = extractXmlFromResponse(claudeResponse, "similarityCheck");
      }
      if (xmlContent == null) {
        log.warn("No similarity_check XML found in response: {}", claudeResponse);
        return null;
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc =
          builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

      Element root = doc.getDocumentElement();

      Map<String, Object> result = new HashMap<>();

      // Parse foundMatch - try both formats
      String foundMatchStr = getElementText(root, "found_match");
      if (foundMatchStr == null) {
        foundMatchStr = getElementText(root, "foundMatch");
      }
      result.put("foundMatch", "true".equalsIgnoreCase(foundMatchStr));

      // Parse matched type - try both formats
      String matchedType = getElementText(root, "matched_type");
      if (matchedType == null) {
        matchedType = getElementText(root, "matchedType");
      }
      result.put("matchedType", matchedType);

      // Parse suggested action - try both formats
      String suggestedAction = getElementText(root, "suggested_action");
      if (suggestedAction == null) {
        suggestedAction = getElementText(root, "suggestedAction");
      }
      result.put("suggestedAction", suggestedAction);

      // Parse explanation
      result.put("explanation", getElementText(root, "explanation"));

      // Parse confidence as integer
      String confidenceStr = getElementText(root, "confidence");
      if (confidenceStr != null && !confidenceStr.isEmpty()) {
        try {
          result.put("confidence", Integer.parseInt(confidenceStr));
        } catch (NumberFormatException e) {
          result.put("confidence", 95); // default confidence
        }
      }

      return result;

    } catch (Exception e) {
      log.error("Failed to parse similarity check XML response", e);
      return null;
    }
  }

  /** Extracts XML content from Claude's response for a specific root element. */
  private String extractXmlFromResponse(String response, String rootElement) {
    if (response == null || response.trim().isEmpty()) {
      return null;
    }

    String startTag = "<" + rootElement + ">";
    String endTag = "</" + rootElement + ">";

    int startIndex = response.indexOf(startTag);
    int endIndex = response.indexOf(endTag);

    if (startIndex >= 0 && endIndex > startIndex) {
      return response.substring(startIndex, endIndex + endTag.length());
    }

    return null;
  }

  /** Extracts JSON from Claude's response. */
  public String extractJsonFromResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      return null;
    }

    int jsonStart = response.indexOf("{");
    int jsonEnd = response.lastIndexOf("}") + 1;

    if (jsonStart >= 0 && jsonEnd > jsonStart) {
      String jsonStr = response.substring(jsonStart, jsonEnd);
      try {
        // Test if it's valid JSON by parsing it
        objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        return jsonStr;
      } catch (Exception e) {
        log.warn("Failed to parse extracted JSON: {}", jsonStr);
      }
    }

    return null;
  }

  private double parseConfidenceThreshold(Object confidenceObj) {
    if (confidenceObj instanceof Number) {
      double threshold = ((Number) confidenceObj).doubleValue();
      return threshold <= 1.0 ? threshold * 100 : threshold;
    }
    return 95.0;
  }

  private List<HeaderPattern> parseHeaderPatterns(List<Map<String, Object>> headerPatternsData) {
    if (headerPatternsData == null || headerPatternsData.isEmpty()) {
      return new ArrayList<>();
    }

    List<HeaderPattern> headerPatterns = new ArrayList<>();
    for (Map<String, Object> patternData : headerPatternsData) {
      try {
        HeaderPattern headerPattern =
            HeaderPattern.builder()
                .regExp((String) patternData.get("regExp"))
                .confidence(parseIntValue(patternData.get("confidence"), 95))
                .mandatory(parseBooleanValue(patternData.get("mandatory"), true))
                .positiveExamples(
                    cleanExamplesList(castToStringList(patternData.get("positiveExamples"))))
                .negativeExamples(
                    cleanExamplesList(castToStringList(patternData.get("negativeExamples"))))
                .rationale((String) patternData.get("rationale"))
                .build();

        if (headerPattern.getRegExp() != null && !headerPattern.getRegExp().trim().isEmpty()) {
          headerPatterns.add(headerPattern);
        }
      } catch (Exception e) {
        log.warn("Failed to parse header pattern: {}", patternData, e);
      }
    }

    log.info("Parsed {} header patterns from Claude response", headerPatterns.size());
    return headerPatterns;
  }

  private int parseIntValue(Object value, int defaultValue) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        log.warn("Failed to parse int value: {}", value);
      }
    }
    return defaultValue;
  }

  private boolean parseBooleanValue(Object value, boolean defaultValue) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof String) {
      String strValue = (String) value;
      if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
        return "true".equalsIgnoreCase(strValue);
      }
      // If string is not a valid boolean, return default value
      return defaultValue;
    }
    return defaultValue;
  }

  private List<String> cleanExamplesList(List<String> examples) {
    if (examples == null) {
      return new ArrayList<>();
    }

    List<String> cleanedExamples = new ArrayList<>();
    for (String example : examples) {
      String cleaned = cleanExample(example);
      if (cleaned != null) {
        cleanedExamples.add(cleaned);
      }
    }
    return cleanedExamples;
  }

  private String cleanExample(String example) {
    if (example == null || example.trim().isEmpty()) {
      return null;
    }

    String cleaned = example.trim();

    // Remove common invalid placeholders
    if (cleaned.equalsIgnoreCase("null")
        || cleaned.equalsIgnoreCase("undefined")
        || cleaned.startsWith("e.g.")
        || cleaned.equals("...")) {
      return null;
    }

    // Remove parenthetical descriptions
    if (cleaned.contains("(") && cleaned.contains(")")) {
      int openParen = cleaned.indexOf("(");
      String beforeParen = cleaned.substring(0, openParen).trim();
      if (!beforeParen.isEmpty()) {
        cleaned = beforeParen;
      }
    }

    // Remove wrapping quotes
    if (cleaned.length() >= 2
        && ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
            || (cleaned.startsWith("'") && cleaned.endsWith("'")))) {
      cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
    }

    return cleaned.isEmpty() ? null : cleaned;
  }

  private List<String> parseListFromElement(Element parent, String elementName, String itemName) {
    List<String> result = new ArrayList<>();
    NodeList elements = parent.getElementsByTagName(elementName);
    if (elements.getLength() > 0) {
      Element element = (Element) elements.item(0);
      NodeList items = element.getElementsByTagName(itemName);
      for (int i = 0; i < items.getLength(); i++) {
        String value = items.item(i).getTextContent().trim();
        if (!value.isEmpty()) {
          result.add(value);
        }
      }
    }
    return result;
  }

  /**
   * Parses Claude response for pattern updates. Supports multiple XML formats: -
   * <semanticTypeUpdate> for data pattern updates - <headerPatternUpdate> for header pattern
   * updates - <patternUpdate> for backward compatibility
   */
  public PatternUpdateResponse parsePatternUpdateResponse(String claudeResponse) {
    try {
      // Try different XML root tags to support various prompt formats
      String xmlContent = extractXmlFromResponse(claudeResponse, "semanticTypeUpdate");
      if (xmlContent == null) {
        xmlContent = extractXmlFromResponse(claudeResponse, "headerPatternUpdate");
      }
      if (xmlContent == null) {
        xmlContent = extractXmlFromResponse(claudeResponse, "patternUpdate");
      }

      if (xmlContent == null) {
        log.warn(
            "No pattern update XML found in response. Tried: semanticTypeUpdate, headerPatternUpdate, patternUpdate");
        return null;
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc =
          builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

      Element root = doc.getDocumentElement();

      // Parse based on the root element type
      String rootTagName = root.getTagName();

      if ("semanticTypeUpdate".equals(rootTagName)) {
        // Handle data pattern update format
        return parseSemanticTypeUpdate(root);
      } else if ("headerPatternUpdate".equals(rootTagName)) {
        // Handle header pattern update format
        return parseHeaderPatternUpdate(root);
      } else {
        // Handle generic pattern update format (backward compatibility)
        return parseGenericPatternUpdate(root);
      }

    } catch (Exception e) {
      log.error("Failed to parse pattern update response", e);
      return null;
    }
  }

  /** Parse semantic type update format (for data pattern improvements) */
  private PatternUpdateResponse parseSemanticTypeUpdate(Element root) {
    String updatedRegexPattern = getElementText(root, "newRegexPattern");

    // Parse positive examples
    List<String> positiveExamples = new ArrayList<>();
    NodeList positiveExamplesNode = root.getElementsByTagName("positiveExamples");
    if (positiveExamplesNode.getLength() > 0) {
      Element positiveExamplesElement = (Element) positiveExamplesNode.item(0);
      positiveExamples = parseDirectExamplesList(positiveExamplesElement);
    }

    // Parse negative examples
    List<String> negativeExamples = new ArrayList<>();
    NodeList negativeExamplesNode = root.getElementsByTagName("negativeExamples");
    if (negativeExamplesNode.getLength() > 0) {
      Element negativeExamplesElement = (Element) negativeExamplesNode.item(0);
      negativeExamples = parseDirectExamplesList(negativeExamplesElement);
    }

    String rationale = getElementText(root, "rationale");

    return PatternUpdateResponse.builder()
        .improvedPattern(updatedRegexPattern)
        .newPositiveContentExamples(positiveExamples)
        .newNegativeContentExamples(negativeExamples)
        .explanation(rationale)
        .build();
  }

  /** Parse header pattern update format (for header pattern improvements) */
  private PatternUpdateResponse parseHeaderPatternUpdate(Element root) {
    List<HeaderPattern> updatedHeaderPatterns = new ArrayList<>();
    List<String> positiveHeaderExamples = new ArrayList<>();
    List<String> negativeHeaderExamples = new ArrayList<>();

    NodeList headerPatternsNode = root.getElementsByTagName("headerPatterns");
    if (headerPatternsNode.getLength() > 0) {
      Element headerPatternsElement = (Element) headerPatternsNode.item(0);
      NodeList patternNodes = headerPatternsElement.getElementsByTagName("pattern");

      for (int i = 0; i < patternNodes.getLength(); i++) {
        Element patternElement = (Element) patternNodes.item(i);

        String regExp = getElementText(patternElement, "regExp");
        int confidence = parseIntValue(getElementText(patternElement, "confidence"), 95);
        boolean mandatory = parseBooleanValue(getElementText(patternElement, "mandatory"), true);
        String rationale = getElementText(patternElement, "rationale");

        // Collect individual examples from each pattern
        String positiveExample = getElementText(patternElement, "positiveExample");
        String negativeExample = getElementText(patternElement, "negativeExample");

        if (positiveExample != null && !positiveExample.trim().isEmpty()) {
          positiveHeaderExamples.add(positiveExample.trim());
        }
        if (negativeExample != null && !negativeExample.trim().isEmpty()) {
          negativeHeaderExamples.add(negativeExample.trim());
        }

        if (regExp != null && !regExp.trim().isEmpty()) {
          HeaderPattern headerPattern =
              HeaderPattern.builder()
                  .regExp(regExp)
                  .confidence(confidence)
                  .mandatory(mandatory)
                  .rationale(rationale)
                  .build();
          updatedHeaderPatterns.add(headerPattern);
        }
      }
    }

    String overallRationale = getElementText(root, "overallRationale");

    return PatternUpdateResponse.builder()
        .updatedHeaderPatterns(updatedHeaderPatterns)
        .newPositiveHeaderExamples(positiveHeaderExamples)
        .newNegativeHeaderExamples(negativeHeaderExamples)
        .explanation(overallRationale)
        .build();
  }

  /** Parse generic pattern update format (backward compatibility) */
  private PatternUpdateResponse parseGenericPatternUpdate(Element root) {
    // Parse updated regex pattern
    String updatedRegexPattern = getElementText(root, "updatedRegexPattern");

    // Parse updated header patterns
    List<HeaderPattern> updatedHeaderPatterns = parseUpdatedHeaderPatterns(root);

    // Parse new examples
    Element newExamples = (Element) root.getElementsByTagName("newExamples").item(0);
    List<String> newPositiveContentExamples =
        parseExamplesList(newExamples, "positiveContentExamples");
    List<String> newNegativeContentExamples =
        parseExamplesList(newExamples, "negativeContentExamples");
    List<String> newPositiveHeaderExamples =
        parseExamplesList(newExamples, "positiveHeaderExamples");
    List<String> newNegativeHeaderExamples =
        parseExamplesList(newExamples, "negativeHeaderExamples");

    String updateSummary = getElementText(root, "updateSummary");

    return PatternUpdateResponse.builder()
        .improvedPattern(updatedRegexPattern)
        .newPositiveContentExamples(newPositiveContentExamples)
        .newNegativeContentExamples(newNegativeContentExamples)
        .newPositiveHeaderExamples(newPositiveHeaderExamples)
        .newNegativeHeaderExamples(newNegativeHeaderExamples)
        .explanation(updateSummary)
        .build();
  }

  /** Parse examples directly under an element (for the new format) */
  private List<String> parseDirectExamplesList(Element parentElement) {
    List<String> examples = new ArrayList<>();
    NodeList exampleNodes = parentElement.getElementsByTagName("example");

    for (int i = 0; i < exampleNodes.getLength(); i++) {
      String example = exampleNodes.item(i).getTextContent();
      if (example != null && !example.trim().isEmpty()) {
        examples.add(example.trim());
      }
    }

    return examples;
  }

  /** Parse updated header patterns from XML. */
  private List<HeaderPattern> parseUpdatedHeaderPatterns(Element root) {
    List<HeaderPattern> headerPatterns = new ArrayList<>();

    NodeList updatedHeaderPatternsNode = root.getElementsByTagName("updatedHeaderPatterns");
    if (updatedHeaderPatternsNode.getLength() == 0) {
      return headerPatterns;
    }

    Element headerPatternsElement = (Element) updatedHeaderPatternsNode.item(0);
    NodeList patternNodes = headerPatternsElement.getElementsByTagName("pattern");

    for (int i = 0; i < patternNodes.getLength(); i++) {
      Element patternElement = (Element) patternNodes.item(i);

      try {
        String regExp = getElementText(patternElement, "regExp");
        int confidence = parseIntValue(getElementText(patternElement, "confidence"), 95);
        String rationale = getElementText(patternElement, "rationale");

        List<String> positiveExamples = parseXmlExamplesList(patternElement, "positiveExamples");
        List<String> negativeExamples = parseXmlExamplesList(patternElement, "negativeExamples");

        if (regExp != null && !regExp.trim().isEmpty()) {
          HeaderPattern headerPattern =
              HeaderPattern.builder()
                  .regExp(regExp)
                  .confidence(confidence)
                  .mandatory(true) // Default to true for updated patterns
                  .positiveExamples(positiveExamples)
                  .negativeExamples(negativeExamples)
                  .rationale(rationale)
                  .build();

          headerPatterns.add(headerPattern);
        }
      } catch (Exception e) {
        log.warn("Failed to parse updated header pattern at index {}: {}", i, e.getMessage());
      }
    }

    log.info("Parsed {} updated header patterns", headerPatterns.size());
    return headerPatterns;
  }

  private GeneratedSemanticType buildErrorResponse(String explanation) {
    return GeneratedSemanticType.builder().resultType("error").explanation(explanation).build();
  }

  /**
   * Generates a fallback backout pattern for list types when Claude doesn't provide one. This
   * ensures all list types have a usable backout pattern for FTA compatibility.
   */
  private String generateFallbackBackoutPattern(List<String> listValues) {
    // Test: Use generic pattern to see if it actually works
    log.info("Using generic .* backout pattern for testing");
    return ".*";
  }
}

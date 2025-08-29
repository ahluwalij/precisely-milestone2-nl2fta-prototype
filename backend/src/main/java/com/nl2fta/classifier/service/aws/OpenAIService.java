package com.nl2fta.classifier.service.aws;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with OpenAI API as an alternative to AWS Bedrock.
 * Supports GPT-4 models for semantic type generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService implements LLMService {

  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  @Value("${openai.api-key:}")
  private String openaiApiKey;

  @Value("${openai.model:gpt-4o}")
  private String model;

  @Value("${openai.max-tokens:4096}")
  private int maxTokens;

  @Value("${openai.temperature:0.7}")
  private double temperature;

  @Value("${openai.retry.max-attempts:3}")
  private int maxRetryAttempts;

  private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

  public boolean isConfigured() {
    return openaiApiKey != null && !openaiApiKey.trim().isEmpty();
  }

  public String invokeClaudeForSemanticTypeGeneration(String prompt) throws Exception {
    if (!isConfigured()) {
      throw new IllegalStateException("OpenAI API key not configured. Please set OPENAI_API_KEY environment variable.");
    }

    log.info("OpenAI request model={}, maxTokens={}, temperature={}", model, maxTokens, temperature);
    log.debug("OpenAI prompt (first 500 chars): {}", prompt != null && prompt.length() > 500 ? prompt.substring(0, 500) + "…" : prompt);

    // Create OpenAI API request
    var requestBody = objectMapper.createObjectNode();
    requestBody.put("model", model);
    requestBody.put("max_tokens", maxTokens);
    requestBody.put("temperature", temperature);

    var messages = objectMapper.createArrayNode();
    var message = objectMapper.createObjectNode();
    message.put("role", "user");
    message.put("content", prompt);
    messages.add(message);

    requestBody.set("messages", messages);

    // Set headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(openaiApiKey);

    HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

    // Implement retry logic
    int attempt = 0;
    while (attempt < maxRetryAttempts) {
      try {
        ResponseEntity<String> response = restTemplate.exchange(
            OPENAI_API_URL, HttpMethod.POST, entity, String.class);

        if (response.getBody() != null) {
          JsonNode responseJson = objectMapper.readTree(response.getBody());
          log.debug("OpenAI raw response (first 800 chars): {}",
              response.getBody().length() > 800 ? response.getBody().substring(0, 800) + "…" : response.getBody());
          JsonNode choices = responseJson.get("choices");

          if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.get("message");

            if (messageNode != null && messageNode.has("content")) {
              String content = messageNode.get("content").asText();
              log.info("OpenAI response content length={} chars", content.length());
              return content;
            }
          }
        }

        log.error("Invalid response format from OpenAI API: status={}, bodyPresent={}",
            response.getStatusCode(), response.getBody() != null);
        throw new RuntimeException("Invalid response format from OpenAI API");

      } catch (Exception e) {
        attempt++;
        log.warn("OpenAI API call attempt {} failed: {}", attempt, e.getMessage(), e);

        if (attempt >= maxRetryAttempts) {
          throw new RuntimeException("OpenAI API call failed after " + maxRetryAttempts + " attempts", e);
        }

        // Simple exponential backoff
        try {
          Thread.sleep(1000 * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during retry", ie);
        }
      }
    }

    throw new RuntimeException("Failed to get response from OpenAI API");
  }

  public String getCurrentModelId() {
    return model;
  }
}

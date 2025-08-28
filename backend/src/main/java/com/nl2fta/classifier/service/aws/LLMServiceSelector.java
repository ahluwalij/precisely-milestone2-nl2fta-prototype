package com.nl2fta.classifier.service.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service selector that chooses between AWS Bedrock and OpenAI based on configuration.
 * Prioritizes OpenAI if configured, falls back to AWS Bedrock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceSelector {

  private final AwsBedrockService awsBedrockService;
  private final OpenAIService openAIService;

  @Value("${llm.provider:openai}")
  private String preferredProvider;

  /**
   * Gets the appropriate LLM service based on configuration and availability.
   * @return The configured LLM service
   * @throws IllegalStateException if no service is configured
   */
  public LLMService getLLMService() {
    // Check preferred provider first
    if ("openai".equalsIgnoreCase(preferredProvider) && openAIService.isConfigured()) {
      log.debug("Using OpenAI service for LLM calls");
      return openAIService;
    }

    if ("bedrock".equalsIgnoreCase(preferredProvider) && awsBedrockService.isConfigured()) {
      log.debug("Using AWS Bedrock service for LLM calls");
      return awsBedrockService;
    }

    // Fallback: try any available service
    if (openAIService.isConfigured()) {
      log.debug("Preferred provider not available, falling back to OpenAI");
      return openAIService;
    }

    if (awsBedrockService.isConfigured()) {
      log.debug("Preferred provider not available, falling back to AWS Bedrock");
      return awsBedrockService;
    }

    throw new IllegalStateException(
        "No LLM service is configured. Please configure either OpenAI API key or AWS Bedrock credentials.");
  }

  /**
   * Gets the currently active provider name
   * @return The provider name ("openai" or "bedrock")
   */
  public String getActiveProvider() {
    LLMService service = getLLMService();
    if (service instanceof OpenAIService) {
      return "openai";
    } else if (service instanceof AwsBedrockService) {
      return "bedrock";
    }
    return "unknown";
  }
}

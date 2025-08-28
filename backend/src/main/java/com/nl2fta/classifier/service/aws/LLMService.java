package com.nl2fta.classifier.service.aws;

/**
 * Common interface for LLM services (AWS Bedrock, OpenAI, etc.)
 */
public interface LLMService {

  /**
   * Invokes the LLM for semantic type generation
   * @param prompt The prompt to send to the LLM
   * @return The LLM response
   * @throws Exception if the call fails
   */
  String invokeClaudeForSemanticTypeGeneration(String prompt) throws Exception;

  /**
   * Gets the current model ID being used
   * @return The model identifier
   */
  String getCurrentModelId();

  /**
   * Checks if the service is properly configured
   * @return true if configured, false otherwise
   */
  boolean isConfigured();
}

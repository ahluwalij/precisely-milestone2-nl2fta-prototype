package com.nl2fta.classifier.service.aws;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

/**
 * Service for interacting with AWS Bedrock using Claude Sonnet 4.0. Simplified to only support
 * Claude Sonnet 4.0 model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwsBedrockService {

  private final ObjectMapper objectMapper;
  private BedrockRuntimeClient bedrockRuntimeClient;

  @Value("${aws.bedrock.claude.model-id}")
  private String modelId;

  @Value("${aws.region:us-east-1}")
  private String defaultAwsRegion;

  @Value("${aws.bedrock.retry.max-attempts:5}")
  private int maxRetryAttempts;

  @Value("${aws.bedrock.retry.initial-delay-ms:1000}")
  private long initialRetryDelayMs;

  @Value("${aws.bedrock.retry.max-delay-ms:60000}")
  private long maxRetryDelayMs;

  @Value("${aws.bedrock.retry.jitter-ms:1000}")
  private long retryJitterMs;

  @Value("${aws.bedrock.max-tokens:4096}")
  private int maxTokens;

  @Value("${aws.bedrock.temperature:0}")
  private double temperature;

  private String currentRegion;

  public void initializeClient(String accessKeyId, String secretAccessKey, String region) {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

    this.currentRegion = region != null ? region : defaultAwsRegion;
    this.bedrockRuntimeClient =
        BedrockRuntimeClient.builder()
            .region(Region.of(currentRegion))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();

    log.info(
        "AWS Bedrock client initialized for region: {} with Claude Sonnet 4.0 model: {}",
        currentRegion,
        modelId);
  }

  public boolean isInitialized() {
    return bedrockRuntimeClient != null;
  }

  public String getCurrentRegion() {
    return currentRegion;
  }

  public String getCurrentModelId() {
    return modelId;
  }

  public void clearCredentials() {
    if (bedrockRuntimeClient != null) {
      // Close the client properly in SDK v2
      bedrockRuntimeClient.close();
      bedrockRuntimeClient = null;
      log.info("AWS Bedrock client credentials cleared");
    }
  }

  /**
   * Invokes the model using the unified Converse API. This method works with all Bedrock models
   * without requiring model-specific formats.
   *
   * @param prompt The prompt to send to the model
   * @return The model's response text
   * @throws Exception if the invocation fails
   */
  public String invokeClaudeForSemanticTypeGeneration(String prompt) throws Exception {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "AWS Bedrock client not initialized. Please configure AWS credentials first.");
    }

    if (modelId == null || modelId.trim().isEmpty()) {
      throw new IllegalStateException(
          "No model selected. Please select a model from the available options in your region.");
    }

    log.debug("Sending prompt to AWS Bedrock model {}:\n{}", modelId, prompt);

    // Create the message content
    ContentBlock contentBlock = ContentBlock.builder().text(prompt).build();

    // Create the user message
    Message userMessage =
        Message.builder().role(ConversationRole.USER).content(contentBlock).build();

    // Configure inference parameters
    InferenceConfiguration inferenceConfig =
        InferenceConfiguration.builder()
            .maxTokens(maxTokens)
            .temperature((float) temperature)
            .build();

    // Create the Converse request
    ConverseRequest converseRequest =
        ConverseRequest.builder()
            .modelId(modelId)
            .messages(List.of(userMessage))
            .inferenceConfig(inferenceConfig)
            .build();

    // Implement retry logic with exponential backoff
    int attempt = 0;
    long retryDelay = initialRetryDelayMs;

    while (attempt < maxRetryAttempts) {
      try {
        // Invoke the model using the Converse API
        ConverseResponse response = bedrockRuntimeClient.converse(converseRequest);

        // Extract the response text
        Message responseMessage = response.output().message();
        if (responseMessage != null && !responseMessage.content().isEmpty()) {
          ContentBlock responseContent = responseMessage.content().get(0);
          if (responseContent.text() != null) {
            return responseContent.text();
          }
        }

        throw new RuntimeException("No content in model response");

      } catch (ThrottlingException e) {
        attempt++;
        if (attempt >= maxRetryAttempts) {
          log.error("Max retry attempts ({}) reached for AWS Bedrock throttling", maxRetryAttempts);
          throw new RuntimeException(
              String.format(
                  "AWS Bedrock throttling error after %d retry attempts. "
                      + "The service is experiencing high load. Please try again later.",
                  maxRetryAttempts),
              e);
        }

        log.warn(
            "AWS Bedrock throttling detected. Retrying in {} ms (attempt {}/{})",
            retryDelay,
            attempt,
            maxRetryAttempts);

        try {
          Thread.sleep(retryDelay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Retry interrupted", ie);
        }

        // Exponential backoff with jitter
        retryDelay =
            Math.min(retryDelay * 2 + (long) (Math.random() * retryJitterMs), maxRetryDelayMs);

      } catch (AccessDeniedException e) {
        log.error("Access denied to AWS Bedrock model: {}", modelId, e);
        throw new RuntimeException(
            String.format(
                "You don't have access to the model '%s'. "
                    + "Please ensure: 1) Your AWS account has access to this model in AWS Bedrock, "
                    + "2) The model is available in your region (%s)",
                modelId, currentRegion),
            e);
      } catch (ValidationException e) {
        log.error("Validation error for model: {}", modelId, e);
        throw new RuntimeException(
            String.format(
                "Model '%s' validation error. This model may not be available in your region (%s) "
                    + "or may require different configuration. Please select a different model.",
                modelId, currentRegion),
            e);
      } catch (Exception e) {
        log.error("Error invoking model: {}", modelId, e);
        throw new RuntimeException("Failed to generate semantic type: " + e.getMessage(), e);
      }
    }

    throw new RuntimeException("Failed to invoke AWS Bedrock model after all retry attempts");
  }
}

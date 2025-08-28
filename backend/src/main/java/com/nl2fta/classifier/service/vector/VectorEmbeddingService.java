package com.nl2fta.classifier.service.vector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Service for generating vector embeddings for semantic types using AWS Bedrock. Uses Amazon Titan
 * Text Embeddings model for high-quality embeddings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorEmbeddingService {

  private final ObjectMapper objectMapper;
  private final com.nl2fta.classifier.service.aws.AwsCredentialsService awsCredentialsService;
  private BedrockRuntimeClient bedrockClient;

  @Value("${aws.region:us-east-1}")
  private String awsRegion;

  @Value("${aws.bedrock.embedding.model-id:amazon.titan-embed-text-v2:0}")
  private String embeddingModelId;

  @PostConstruct
  public void init() {
    // Don't initialize Bedrock client on startup - wait for AWS connection
    log.info(
        "VectorEmbeddingService initialized - AWS Bedrock will be available after user connects");
  }

  /** Initialize Bedrock client when AWS credentials are provided. */
  public void initializeBedrockClient() {
    if (!awsCredentialsService.areCredentialsAvailable()) {
      log.warn("Cannot initialize Bedrock client - AWS credentials not available");
      return;
    }

    try {
      this.bedrockClient =
          BedrockRuntimeClient.builder()
              .region(Region.of(awsCredentialsService.getRegion()))
              .credentialsProvider(awsCredentialsService.getCredentialsProvider())
              .build();
      log.info("Bedrock client initialized for embeddings");
    } catch (Exception e) {
      log.error("Failed to initialize Bedrock client", e);
    }
  }

  /**
   * Generate embedding vector for a single text input.
   *
   * @param text The text to generate embedding for
   * @return The embedding vector as a list of floats
   */
  public List<Float> generateEmbedding(String text) {
    if (bedrockClient == null) {
      throw new IllegalStateException("Cannot generate embeddings - AWS credentials not connected");
    }

    log.debug(
        "Generating embedding for text: {}",
        text.substring(0, Math.min(text.length(), 100)) + "...");

    try {
      // Prepare the request payload for Titan embedding model
      Map<String, Object> requestBody = Map.of("inputText", text);

      String payload = objectMapper.writeValueAsString(requestBody);

      // Invoke the model
      InvokeModelRequest invokeRequest =
          InvokeModelRequest.builder()
              .modelId(embeddingModelId)
              .contentType("application/json")
              .body(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
              .build();

      InvokeModelResponse response = bedrockClient.invokeModel(invokeRequest);

      // Parse the response
      String responseJson = response.body().asUtf8String();
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);

      // Extract embedding from response
      @SuppressWarnings("unchecked")
      List<Number> embedding = (List<Number>) responseMap.get("embedding");

      // Convert to Float list
      return embedding.stream().map(Number::floatValue).collect(Collectors.toList());

    } catch (Exception e) {
      log.error("Error generating embedding for text", e);
      throw new RuntimeException("Failed to generate embedding", e);
    }
  }

  /**
   * Generate embeddings for multiple texts in batch. Note: Titan embedding model doesn't support
   * batch processing, so we process texts individually.
   *
   * @param texts List of texts to generate embeddings for
   * @return List of embedding vectors
   */
  public List<List<Float>> generateEmbeddings(List<String> texts) {
    log.debug("Generating embeddings for {} texts", texts.size());

    try {
      return texts.stream().map(this::generateEmbedding).collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Error generating embeddings for batch", e);
      throw new RuntimeException("Failed to generate embeddings", e);
    }
  }

  /**
   * Calculate cosine similarity between two embedding vectors.
   *
   * @param embedding1 First embedding vector
   * @param embedding2 Second embedding vector
   * @return Cosine similarity score between -1 and 1
   */
  public double calculateCosineSimilarity(List<Float> embedding1, List<Float> embedding2) {
    if (embedding1.size() != embedding2.size()) {
      throw new IllegalArgumentException("Embeddings must have the same dimension");
    }

    double dotProduct = 0.0;
    double norm1 = 0.0;
    double norm2 = 0.0;

    for (int i = 0; i < embedding1.size(); i++) {
      dotProduct += embedding1.get(i) * embedding2.get(i);
      norm1 += Math.pow(embedding1.get(i), 2);
      norm2 += Math.pow(embedding2.get(i), 2);
    }

    return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
  }

  /**
   * Generate a text representation for a semantic type that will be used for embedding. This
   * combines the type name, description, and examples into a single text.
   *
   * @param semanticType The semantic type name
   * @param description The type description
   * @param examples Sample values for the type
   * @return Combined text representation
   */
  public String generateSemanticTypeText(
      String semanticType, String description, List<String> examples) {
    StringBuilder text = new StringBuilder();

    // Include semantic type name
    text.append("Semantic Type: ").append(semanticType).append("\n");

    // Include description
    if (description != null && !description.isEmpty()) {
      text.append("Description: ").append(description).append("\n");
    }

    // Include examples
    if (examples != null && !examples.isEmpty()) {
      text.append("Examples: ");
      text.append(
          examples.stream()
              .limit(10) // Limit to first 10 examples
              .collect(Collectors.joining(", ")));
    }

    return text.toString();
  }
}

package com.nl2fta.classifier.service.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsBedrockService Tests")
class AwsBedrockServiceTest {

  private AwsBedrockService awsBedrockService;

  @Mock private ObjectMapper objectMapper;

  @Mock private BedrockRuntimeClient bedrockRuntimeClient;

  private static final String TEST_MODEL_ID = "anthropic.claude-sonnet-4-20250514";
  private static final String TEST_ACCESS_KEY = "AKIATESTFAKEKEY123456";
  private static final String TEST_SECRET_KEY = "test-fake-secret-key-for-unit-testing-purposes-only";
  private static final String TEST_REGION = "us-east-1";
  private static final String TEST_PROMPT = "Generate a semantic type for email addresses";

  @BeforeEach
  void setUp() {
    awsBedrockService = new AwsBedrockService(objectMapper);

    // Set up field values using reflection
    ReflectionTestUtils.setField(awsBedrockService, "modelId", TEST_MODEL_ID);
    ReflectionTestUtils.setField(awsBedrockService, "defaultAwsRegion", "us-east-1");
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryAttempts", 5);
    ReflectionTestUtils.setField(awsBedrockService, "initialRetryDelayMs", 1000L);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryDelayMs", 60000L);
    ReflectionTestUtils.setField(awsBedrockService, "retryJitterMs", 1000L);
    ReflectionTestUtils.setField(awsBedrockService, "maxTokens", 4096);
    ReflectionTestUtils.setField(awsBedrockService, "temperature", 0.1);
  }

  @Test
  void shouldInitializeClientWithProperConfiguration() {
    // When
    awsBedrockService.initializeClient(TEST_ACCESS_KEY, TEST_SECRET_KEY, TEST_REGION);

    // Then
    assertThat(awsBedrockService.isInitialized()).isTrue();
    assertThat(awsBedrockService.getCurrentRegion()).isEqualTo(TEST_REGION);
    assertThat(awsBedrockService.getCurrentModelId()).isEqualTo(TEST_MODEL_ID);
  }

  @Test
  void shouldUseDefaultRegionWhenNullProvided() {
    // When
    awsBedrockService.initializeClient(TEST_ACCESS_KEY, TEST_SECRET_KEY, null);

    // Then
    assertThat(awsBedrockService.isInitialized()).isTrue();
    assertThat(awsBedrockService.getCurrentRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldThrowExceptionWhenInvokingWithoutInitialization() {
    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWS Bedrock client not initialized");
  }

  @Test
  void shouldThrowExceptionWhenModelIdIsNull() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "modelId", null);
    awsBedrockService.initializeClient(TEST_ACCESS_KEY, TEST_SECRET_KEY, TEST_REGION);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No model selected");
  }

  @Test
  void shouldThrowExceptionWhenModelIdIsEmpty() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "modelId", "   ");
    awsBedrockService.initializeClient(TEST_ACCESS_KEY, TEST_SECRET_KEY, TEST_REGION);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No model selected");
  }

  @Test
  void shouldHandleSuccessfulModelInvocation() throws Exception {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);

    ContentBlock responseContent =
        ContentBlock.builder().text("Generated semantic type response").build();
    Message responseMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(responseContent).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

    // When
    String result = awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT);

    // Then
    assertThat(result).isEqualTo("Generated semantic type response");
    verify(bedrockRuntimeClient, times(1)).converse(any(ConverseRequest.class));
  }

  @Test
  void shouldHandleAccessDeniedException() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "currentRegion", TEST_REGION);

    AccessDeniedException accessDeniedException =
        AccessDeniedException.builder()
            .message("User is not authorized to perform bedrock:InvokeModel")
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenThrow(accessDeniedException);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("You don't have access to the model")
        .hasMessageContaining(TEST_MODEL_ID)
        .hasMessageContaining(TEST_REGION)
        .hasCauseInstanceOf(AccessDeniedException.class);
  }

  @Test
  void shouldHandleValidationException() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "currentRegion", TEST_REGION);

    ValidationException validationException =
        ValidationException.builder().message("Model not found in region").build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenThrow(validationException);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Model")
        .hasMessageContaining("validation error")
        .hasMessageContaining("may not be available in your region")
        .hasMessageContaining(TEST_REGION)
        .hasCauseInstanceOf(ValidationException.class);
  }

  @Test
  void shouldRetryOnThrottlingException() throws Exception {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryAttempts", 3);
    ReflectionTestUtils.setField(
        awsBedrockService, "initialRetryDelayMs", 10L); // Short delay for testing

    ThrottlingException throttlingException =
        ThrottlingException.builder().message("Too many requests").build();

    ContentBlock responseContent = ContentBlock.builder().text("Success after retry").build();
    Message responseMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(responseContent).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    // First two calls throw throttling exception, third succeeds
    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenThrow(throttlingException)
        .thenThrow(throttlingException)
        .thenReturn(mockResponse);

    // When
    String result = awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT);

    // Then
    assertThat(result).isEqualTo("Success after retry");
    verify(bedrockRuntimeClient, times(3)).converse(any(ConverseRequest.class));
  }

  @Test
  void shouldFailAfterMaxRetryAttempts() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryAttempts", 2);
    ReflectionTestUtils.setField(
        awsBedrockService, "initialRetryDelayMs", 10L); // Short delay for testing

    ThrottlingException throttlingException =
        ThrottlingException.builder().message("Too many requests").build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenThrow(throttlingException);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("AWS Bedrock throttling error after 2 retry attempts")
        .hasCauseInstanceOf(ThrottlingException.class);

    verify(bedrockRuntimeClient, times(2)).converse(any(ConverseRequest.class));
  }

  @Test
  void shouldHandleEmptyResponseContent() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);

    Message responseMessage = Message.builder().role(ConversationRole.ASSISTANT).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("No content in model response");
  }

  @Test
  void shouldHandleNullResponseText() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);

    ContentBlock responseContent = ContentBlock.builder().text(null).build();
    Message responseMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(responseContent).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("No content in model response");
  }

  @Test
  void shouldClearCredentialsProperly() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);

    // When
    awsBedrockService.clearCredentials();

    // Then
    assertThat(awsBedrockService.isInitialized()).isFalse();
    verify(bedrockRuntimeClient, times(1)).close();
  }

  @Test
  void shouldHandleClearCredentialsWhenClientIsNull() {
    // Given - client is null by default

    // When/Then - should not throw exception
    assertThatCode(() -> awsBedrockService.clearCredentials()).doesNotThrowAnyException();
    assertThat(awsBedrockService.isInitialized()).isFalse();
  }

  @Test
  void shouldConfigureInferenceParametersCorrectly() throws Exception {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "maxTokens", 2048);
    ReflectionTestUtils.setField(awsBedrockService, "temperature", 0.5);

    ContentBlock responseContent =
        ContentBlock.builder().text("Response with custom parameters").build();
    Message responseMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(responseContent).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

    // When
    String result = awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT);

    // Then
    assertThat(result).isEqualTo("Response with custom parameters");

    // Verify the request was made with correct parameters
    verify(bedrockRuntimeClient)
        .converse(
            argThat(
                (ConverseRequest request) -> {
                  InferenceConfiguration config = request.inferenceConfig();
                  return config.maxTokens() == 2048
                      && Math.abs(config.temperature() - 0.5f) < 0.001;
                }));
  }

  @Test
  void shouldHandleExponentialBackoffWithJitter() throws Exception {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryAttempts", 3);
    ReflectionTestUtils.setField(awsBedrockService, "initialRetryDelayMs", 100L);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryDelayMs", 1000L);
    ReflectionTestUtils.setField(awsBedrockService, "retryJitterMs", 50L);

    ThrottlingException throttlingException =
        ThrottlingException.builder().message("Rate exceeded").build();

    ContentBlock responseContent = ContentBlock.builder().text("Success").build();
    Message responseMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(responseContent).build();
    ConverseResponse mockResponse =
        ConverseResponse.builder()
            .output(ConverseOutput.builder().message(responseMessage).build())
            .build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenThrow(throttlingException)
        .thenThrow(throttlingException)
        .thenReturn(mockResponse);

    long startTime = System.currentTimeMillis();

    // When
    String result = awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT);

    long endTime = System.currentTimeMillis();

    // Then
    assertThat(result).isEqualTo("Success");
    // Verify that retries happened with delays (at least 200ms for two retries)
    assertThat(endTime - startTime).isGreaterThan(200);
    verify(bedrockRuntimeClient, times(3)).converse(any(ConverseRequest.class));
  }

  @Test
  void shouldHandleInterruptedExceptionDuringRetry() throws InterruptedException {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);
    ReflectionTestUtils.setField(awsBedrockService, "maxRetryAttempts", 2);

    ThrottlingException throttlingException =
        ThrottlingException.builder().message("Rate exceeded").build();

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenThrow(throttlingException);

    // Simulate thread interruption
    Thread currentThread = Thread.currentThread();
    Thread interrupterThread =
        new Thread(
            () -> {
              try {
                Thread.sleep(50); // Wait a bit before interrupting
                currentThread.interrupt();
              } catch (InterruptedException e) {
                // Ignore
              }
            });
    interrupterThread.start();

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Retry interrupted");

    // Clean up
    Thread.interrupted(); // Clear interrupt flag
    interrupterThread.join();
  }

  @Test
  void shouldHandleGenericException() {
    // Given
    ReflectionTestUtils.setField(awsBedrockService, "bedrockRuntimeClient", bedrockRuntimeClient);

    RuntimeException genericException = new RuntimeException("Unexpected error");

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenThrow(genericException);

    // When/Then
    assertThatThrownBy(() -> awsBedrockService.invokeClaudeForSemanticTypeGeneration(TEST_PROMPT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to generate semantic type: Unexpected error")
        .hasCause(genericException);
  }
}

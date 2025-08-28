package com.nl2fta.classifier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClientBuilder;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.InvalidSequenceTokenException;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudWatchLoggingService Tests")
class CloudWatchLoggingServiceTest {

  @Mock private CloudWatchLogsClient cloudWatchLogsClient;

  @Mock private CloudWatchLogsClientBuilder clientBuilder;

  @Mock private ScheduledExecutorService scheduler;

  private CloudWatchLoggingService cloudWatchLoggingService;

  @BeforeEach
  void setUp() {
    cloudWatchLoggingService = new CloudWatchLoggingService();

    // Set default configuration values
    ReflectionTestUtils.setField(cloudWatchLoggingService, "logGroupName", "/aws/nl2fta");
    ReflectionTestUtils.setField(cloudWatchLoggingService, "logStreamName", "application-logs");
    ReflectionTestUtils.setField(cloudWatchLoggingService, "awsRegion", "us-east-1");
    ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "");
    ReflectionTestUtils.setField(cloudWatchLoggingService, "adminSecretAccessKey", "");
  }

  @Nested
  @DisplayName("Initialization Tests")
  class InitializationTests {

    @Test
    @DisplayName("Should not initialize when CloudWatch is disabled")
    void shouldNotInitializeWhenCloudWatchIsDisabled() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", false);

      cloudWatchLoggingService.init();

      Object client =
          ReflectionTestUtils.getField(cloudWatchLoggingService, "cloudWatchLogsClient");
      assertThat(client).isNull();
    }

    @Test
    @DisplayName("Should not initialize when admin credentials not provided")
    void shouldNotInitializeWhenAdminCredentialsNotProvided() {
      // Admin credentials empty by default; service should auto-disable
      cloudWatchLoggingService.init();

      Object client =
          ReflectionTestUtils.getField(cloudWatchLoggingService, "cloudWatchLogsClient");
      assertThat(client).isNull();
    }

    @Test
    @DisplayName("Should initialize with admin credentials when provided")
    void shouldInitializeWithAdminCredentialsWhenProvided() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);
      ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "admin-key");
      ReflectionTestUtils.setField(
          cloudWatchLoggingService, "adminSecretAccessKey", "admin-secret");

      try (var clientMock = mockStatic(CloudWatchLogsClient.class);
          var credentialsMock = mockStatic(AwsBasicCredentials.class);
          var providerMock = mockStatic(StaticCredentialsProvider.class)) {

        clientMock.when(CloudWatchLogsClient::builder).thenReturn(clientBuilder);
        when(clientBuilder.region(any())).thenReturn(clientBuilder);
        when(clientBuilder.credentialsProvider(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(cloudWatchLogsClient);

        AwsBasicCredentials credentials = AwsBasicCredentials.create("x", "y");
        credentialsMock
            .when(() -> AwsBasicCredentials.create("admin-key", "admin-secret"))
            .thenReturn(credentials);

        StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);
        providerMock.when(() -> StaticCredentialsProvider.create(credentials)).thenReturn(provider);

        when(cloudWatchLogsClient.createLogGroup(any(CreateLogGroupRequest.class)))
            .thenReturn(CreateLogGroupResponse.builder().build());
        when(cloudWatchLogsClient.createLogStream(any(CreateLogStreamRequest.class)))
            .thenReturn(CreateLogStreamResponse.builder().build());

        cloudWatchLoggingService.init();

        verify(clientBuilder).credentialsProvider(provider);
      }
    }

    @Test
    @DisplayName("Should handle log group already exists exception")
    void shouldHandleLogGroupAlreadyExistsException() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);

      try (var clientMock = mockStatic(CloudWatchLogsClient.class)) {
        // Provide admin credentials to enable initialization
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "key");
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminSecretAccessKey", "secret");
        clientMock.when(CloudWatchLogsClient::builder).thenReturn(clientBuilder);
        when(clientBuilder.region(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(cloudWatchLogsClient);

        when(cloudWatchLogsClient.createLogGroup(any(CreateLogGroupRequest.class)))
            .thenThrow(ResourceAlreadyExistsException.builder().build());
        when(cloudWatchLogsClient.createLogStream(any(CreateLogStreamRequest.class)))
            .thenReturn(CreateLogStreamResponse.builder().build());

        assertThatCode(() -> cloudWatchLoggingService.init()).doesNotThrowAnyException();

        verify(cloudWatchLogsClient).createLogStream(any(CreateLogStreamRequest.class));
      }
    }

    @Test
    @DisplayName("Should handle log stream already exists exception")
    void shouldHandleLogStreamAlreadyExistsException() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);

      try (var clientMock = mockStatic(CloudWatchLogsClient.class)) {
        // Provide admin credentials to enable initialization
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "key");
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminSecretAccessKey", "secret");
        clientMock.when(CloudWatchLogsClient::builder).thenReturn(clientBuilder);
        when(clientBuilder.region(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(cloudWatchLogsClient);

        when(cloudWatchLogsClient.createLogGroup(any(CreateLogGroupRequest.class)))
            .thenReturn(CreateLogGroupResponse.builder().build());
        when(cloudWatchLogsClient.createLogStream(any(CreateLogStreamRequest.class)))
            .thenThrow(ResourceAlreadyExistsException.builder().build());

        assertThatCode(() -> cloudWatchLoggingService.init()).doesNotThrowAnyException();

        verify(cloudWatchLogsClient).createLogGroup(any(CreateLogGroupRequest.class));
      }
    }

    @Test
    @DisplayName("Should disable CloudWatch on initialization failure")
    void shouldDisableCloudWatchOnInitializationFailure() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);

      try (var clientMock = mockStatic(CloudWatchLogsClient.class)) {
        // Provide admin credentials to enable initialization
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "key");
        ReflectionTestUtils.setField(cloudWatchLoggingService, "adminSecretAccessKey", "secret");
        clientMock.when(CloudWatchLogsClient::builder).thenReturn(clientBuilder);
        when(clientBuilder.region(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenThrow(new RuntimeException("AWS error"));

        cloudWatchLoggingService.init();

        boolean enabled =
            (Boolean) ReflectionTestUtils.getField(cloudWatchLoggingService, "cloudWatchEnabled");
        assertThat(enabled).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Logging Tests")
  class LoggingTests {

    @BeforeEach
    void setupLoggingTests() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);
      ReflectionTestUtils.setField(
          cloudWatchLoggingService, "cloudWatchLogsClient", cloudWatchLogsClient);
    }

    @Test
    @DisplayName("Should not log when CloudWatch is disabled")
    void shouldNotLogWhenCloudWatchIsDisabled() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", false);

      cloudWatchLoggingService.log("INFO", "Test message", null);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");
      assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Should log message with basic data")
    void shouldLogMessageWithBasicData() throws Exception {
      Map<String, Object> data = new HashMap<>();
      data.put("key1", "value1");
      data.put("key2", "value2");

      cloudWatchLoggingService.log("INFO", "Test message", data);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      assertThat(queue).hasSize(1);

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[INFO] [anonymous] Test message");
      assertThat(event.message()).contains("key1");
      assertThat(event.message()).contains("value1");
    }

    @Test
    @DisplayName("Should log message with username from data")
    void shouldLogMessageWithUsernameFromData() throws Exception {
      Map<String, Object> data = new HashMap<>();
      data.put("username", "testuser");
      data.put("action", "test-action");

      cloudWatchLoggingService.log("ERROR", "Test error", data);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[ERROR] [testuser] Test error");
      assertThat(event.message()).contains("action");
      assertThat(event.message()).contains("\"username\":\"testuser\"");
    }

    @Test
    @DisplayName("Should normalize log level to uppercase")
    void shouldNormalizeLogLevelToUppercase() throws Exception {
      cloudWatchLoggingService.log("debug", "Debug message", null);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[DEBUG]");
    }

    @Test
    @DisplayName("Should handle null level")
    void shouldHandleNullLevel() throws Exception {
      cloudWatchLoggingService.log(null, "Test message", null);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[INFO]");
    }

    @Test
    @DisplayName("Should handle null username in data")
    void shouldHandleNullUsernameInData() throws Exception {
      Map<String, Object> data = new HashMap<>();
      data.put("username", null);

      cloudWatchLoggingService.log("INFO", "Test message", data);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[anonymous]");
    }

    @Test
    @DisplayName("Should handle empty data map")
    void shouldHandleEmptyDataMap() throws Exception {
      Map<String, Object> data = new HashMap<>();

      cloudWatchLoggingService.log("WARN", "Warning message", data);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[WARN] [anonymous] Warning message");
      assertThat(event.message()).doesNotContain("\"data\":");
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void shouldHandleJsonSerializationErrorGracefully() throws Exception {
      // Create a circular reference that will cause JSON serialization to fail
      Map<String, Object> data = new HashMap<>();
      data.put("self", data); // Circular reference

      assertThatCode(() -> cloudWatchLoggingService.log("INFO", "Test message", data))
          .doesNotThrowAnyException();

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      // Queue should be empty due to serialization error
      assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Should log feedback with correct format")
    void shouldLogFeedbackWithCorrectFormat() throws Exception {
      Map<String, Object> context = new HashMap<>();
      context.put("analysisId", "test-analysis");
      context.put("timestamp", "2023-01-01T00:00:00Z");

      cloudWatchLoggingService.logFeedback("testuser", "positive", "Great job!", context);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      assertThat(queue).hasSize(1);

      InputLogEvent event = queue.peek();
      assertThat(event.message()).contains("[INFO] [testuser] User feedback received");
      assertThat(event.message()).contains("positive");
      assertThat(event.message()).contains("Great job!");
      assertThat(event.message()).contains("USER_FEEDBACK");
      assertThat(event.message()).contains("analysisId");
    }
  }

  @Nested
  @DisplayName("Log Upload Tests")
  class LogUploadTests {

    @BeforeEach
    void setupUploadTests() {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);
      ReflectionTestUtils.setField(
          cloudWatchLoggingService, "cloudWatchLogsClient", cloudWatchLogsClient);
      ReflectionTestUtils.setField(cloudWatchLoggingService, "logGroupName", "test-group");
      ReflectionTestUtils.setField(cloudWatchLoggingService, "logStreamName", "test-stream");
    }

    @Test
    @DisplayName("Should not upload when queue is empty")
    void shouldNotUploadWhenQueueIsEmpty() {
      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient, never()).putLogEvents(any(PutLogEventsRequest.class));
    }

    @Test
    @DisplayName("Should upload logs successfully")
    void shouldUploadLogsSuccessfully() throws Exception {
      // Add a log event to the queue
      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message("Test log message")
              .build();
      queue.offer(event);

      PutLogEventsResponse response =
          PutLogEventsResponse.builder().nextSequenceToken("next-token").build();
      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class))).thenReturn(response);

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));

      assertThat(queue).isEmpty();

      String sequenceToken =
          (String) ReflectionTestUtils.getField(cloudWatchLoggingService, "sequenceToken");
      assertThat(sequenceToken).isEqualTo("next-token");
    }

    @Test
    @DisplayName("Should use sequence token in subsequent uploads")
    void shouldUseSequenceTokenInSubsequentUploads() throws Exception {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "sequenceToken", "existing-token");

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message("Test log message")
              .build();
      queue.offer(event);

      PutLogEventsResponse response =
          PutLogEventsResponse.builder().nextSequenceToken("newer-token").build();
      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class))).thenReturn(response);

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));
    }

    @Test
    @DisplayName("Should handle invalid sequence token exception")
    void shouldHandleInvalidSequenceTokenException() throws Exception {
      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message("Test log message")
              .build();
      queue.offer(event);

      InvalidSequenceTokenException exception =
          InvalidSequenceTokenException.builder().expectedSequenceToken("correct-token").build();

      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class)))
          .thenThrow(exception)
          .thenReturn(PutLogEventsResponse.builder().nextSequenceToken("next-token").build());

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient, times(2)).putLogEvents(any(PutLogEventsRequest.class));

      String sequenceToken =
          (String) ReflectionTestUtils.getField(cloudWatchLoggingService, "sequenceToken");
      assertThat(sequenceToken).isEqualTo("next-token");
    }

    @Test
    @DisplayName("Should re-queue events on upload failure")
    void shouldReQueueEventsOnUploadFailure() throws Exception {
      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message("Test log message")
              .build();
      queue.offer(event);

      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class)))
          .thenThrow(new RuntimeException("Upload failed"));

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      assertThat(queue).hasSize(1);
    }

    @Test
    @DisplayName("Should respect batch size limits")
    void shouldRespectBatchSizeLimits() throws Exception {
      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      // Add more events than the batch size limit
      for (int i = 0; i < 15000; i++) {
        InputLogEvent event =
            InputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message("Test log message " + i)
                .build();
        queue.offer(event);
      }

      PutLogEventsResponse response =
          PutLogEventsResponse.builder().nextSequenceToken("next-token").build();
      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class))).thenReturn(response);

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));

      // Should still have events in queue
      assertThat(queue).hasSizeGreaterThan(0);
    }

    @Test
    @DisplayName("Should respect batch byte size limits")
    void shouldRespectBatchByteSizeLimits() throws Exception {
      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      // Create a large message that would exceed byte limit
      String largeMessage = "x".repeat(100000); // 100KB message

      for (int i = 0; i < 15; i++) {
        InputLogEvent event =
            InputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message(largeMessage + i)
                .build();
        queue.offer(event);
      }

      PutLogEventsResponse response =
          PutLogEventsResponse.builder().nextSequenceToken("next-token").build();
      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class))).thenReturn(response);

      ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

      verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));

      // Should still have events in queue
      assertThat(queue).hasSizeGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("Shutdown Tests")
  class ShutdownTests {

    @Test
    @DisplayName("Should shutdown scheduler and upload remaining logs")
    void shouldShutdownSchedulerAndUploadRemainingLogs() throws Exception {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "scheduler", scheduler);
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);
      ReflectionTestUtils.setField(
          cloudWatchLoggingService, "cloudWatchLogsClient", cloudWatchLogsClient);

      when(scheduler.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);

      @SuppressWarnings("unchecked")
      ConcurrentLinkedQueue<InputLogEvent> queue =
          (ConcurrentLinkedQueue<InputLogEvent>)
              ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

      InputLogEvent event =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message("Shutdown test message")
              .build();
      queue.offer(event);

      PutLogEventsResponse response =
          PutLogEventsResponse.builder().nextSequenceToken("final-token").build();
      when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class))).thenReturn(response);

      cloudWatchLoggingService.shutdown();

      verify(scheduler).shutdown();
      verify(scheduler).awaitTermination(10, TimeUnit.SECONDS);
      verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));
    }

    @Test
    @DisplayName("Should force shutdown when termination times out")
    void shouldForceShutdownWhenTerminationTimesOut() throws Exception {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "scheduler", scheduler);

      when(scheduler.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(false);

      cloudWatchLoggingService.shutdown();

      verify(scheduler).shutdown();
      verify(scheduler).shutdownNow();
    }

    @Test
    @DisplayName("Should handle interrupted shutdown gracefully")
    void shouldHandleInterruptedShutdownGracefully() throws Exception {
      ReflectionTestUtils.setField(cloudWatchLoggingService, "scheduler", scheduler);

      when(scheduler.awaitTermination(10, TimeUnit.SECONDS))
          .thenThrow(new InterruptedException("Interrupted"));

      cloudWatchLoggingService.shutdown();

      verify(scheduler).shutdown();
      verify(scheduler).shutdownNow();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should handle complete logging workflow")
    void shouldHandleCompleteLoggingWorkflow() throws Exception {
      // Initialize with valid configuration
      ReflectionTestUtils.setField(cloudWatchLoggingService, "cloudWatchEnabled", true);
      // Provide admin credentials to enable initialization with our new behavior
      ReflectionTestUtils.setField(cloudWatchLoggingService, "adminAccessKeyId", "key");
      ReflectionTestUtils.setField(cloudWatchLoggingService, "adminSecretAccessKey", "secret");

      try (var clientMock = mockStatic(CloudWatchLogsClient.class)) {
        clientMock.when(CloudWatchLogsClient::builder).thenReturn(clientBuilder);
        when(clientBuilder.region(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(cloudWatchLogsClient);

        when(cloudWatchLogsClient.createLogGroup(any(CreateLogGroupRequest.class)))
            .thenReturn(CreateLogGroupResponse.builder().build());
        when(cloudWatchLogsClient.createLogStream(any(CreateLogStreamRequest.class)))
            .thenReturn(CreateLogStreamResponse.builder().build());

        cloudWatchLoggingService.init();

        // Log some messages
        Map<String, Object> data = new HashMap<>();
        data.put("username", "testuser");
        data.put("action", "test-action");

        cloudWatchLoggingService.log("INFO", "Test message 1", data);
        cloudWatchLoggingService.log("ERROR", "Test error", null);
        cloudWatchLoggingService.logFeedback("testuser", "positive", "Great!", new HashMap<>());

        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<InputLogEvent> queue =
            (ConcurrentLinkedQueue<InputLogEvent>)
                ReflectionTestUtils.getField(cloudWatchLoggingService, "logEventQueue");

        assertThat(queue).hasSize(3);

        // Mock successful upload
        PutLogEventsResponse response =
            PutLogEventsResponse.builder().nextSequenceToken("final-token").build();
        when(cloudWatchLogsClient.putLogEvents(any(PutLogEventsRequest.class)))
            .thenReturn(response);

        // Upload logs
        ReflectionTestUtils.invokeMethod(cloudWatchLoggingService, "uploadLogs");

        assertThat(queue).isEmpty();
        verify(cloudWatchLogsClient).putLogEvents(any(PutLogEventsRequest.class));
      }
    }
  }
}

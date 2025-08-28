package com.nl2fta.classifier.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClientBuilder;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.InvalidSequenceTokenException;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

@Service
public class CloudWatchLoggingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(CloudWatchLoggingService.class);
  private static final int MAX_BATCH_SIZE = 10000; // CloudWatch limit
  private static final int MAX_BATCH_BYTES = 1048576; // 1MB CloudWatch limit
  private static final int MAX_MESSAGE_SIZE =
      256000; // 250KB per message chunk (leave room for overhead)

  @Value("${aws.cloudwatch.log-group:/aws/nl2fta}")
  private String logGroupName;

  @Value("${aws.cloudwatch.log-stream:application-logs}")
  private String logStreamName;

  // Enable CloudWatch automatically when admin credentials exist
  private boolean cloudWatchEnabled;

  @Value("${aws.region:us-east-1}")
  private String awsRegion;

  @Value("${aws.cloudwatch.admin-access-key-id:}")
  private String adminAccessKeyId;

  @Value("${aws.cloudwatch.admin-secret-access-key:}")
  private String adminSecretAccessKey;

  private CloudWatchLogsClient cloudWatchLogsClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConcurrentLinkedQueue<InputLogEvent> logEventQueue = new ConcurrentLinkedQueue<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private String sequenceToken = null;

  // Filtering configuration
  @Value("${aws.cloudwatch.filter.enabled:true}")
  private boolean filterEnabled;

  @Value("${aws.cloudwatch.filter.min-level:INFO}")
  private String minLevel;

  @Value(
      "${aws.cloudwatch.filter.allowed-loggers:com.nl2fta.classifier.service.semantic_type.generation,com.nl2fta.classifier.controller.semantic_type}")
  private String allowedLoggersCsv;

  @Value(
      "${aws.cloudwatch.filter.allow-event-types:SEMANTIC_TYPE_GENERATION_REQUEST,SEMANTIC_TYPE_GENERATION_PROMPT,SEMANTIC_TYPE_GENERATION_RESULT,SEMANTIC_TYPE_SAVE_REQUEST,SEMANTIC_TYPE_SAVE_RESULT,USER_FEEDBACK}")
  private String allowedEventTypesCsv;

  @Value("${aws.cloudwatch.filter.exclude-message-patterns:}")
  private String excludeMessagePatterns;

  @Value("${aws.cloudwatch.filter.include-frontend:false}")
  private boolean includeFrontendLogs;

  @PostConstruct
  public void init() {
    // Auto-enable when admin credentials are present
    cloudWatchEnabled =
        adminAccessKeyId != null
            && !adminAccessKeyId.isEmpty()
            && adminSecretAccessKey != null
            && !adminSecretAccessKey.isEmpty();
    if (!cloudWatchEnabled) {
      LOGGER.info("CloudWatch logging is disabled");
      return;
    }

    try {
      CloudWatchLogsClientBuilder clientBuilder =
          CloudWatchLogsClient.builder().region(Region.of(awsRegion));

      // Use admin credentials (required to enable)
      if (!adminAccessKeyId.isEmpty() && !adminSecretAccessKey.isEmpty()) {
        AwsBasicCredentials adminCredentials =
            AwsBasicCredentials.create(adminAccessKeyId, adminSecretAccessKey);
        clientBuilder.credentialsProvider(StaticCredentialsProvider.create(adminCredentials));
        LOGGER.info("Using admin credentials for CloudWatch logging");
      } else {
        // Should not happen since we auto-disable above
        LOGGER.info("Admin credentials missing; CloudWatch will remain disabled");
      }

      cloudWatchLogsClient = clientBuilder.build();

      ensureLogGroupAndStreamExist();

      // Schedule batch uploads every 5 seconds
      scheduler.scheduleAtFixedRate(this::uploadLogs, 5, 5, TimeUnit.SECONDS);

      LOGGER.info(
          "CloudWatch logging initialized for log group: {} and stream: {}",
          logGroupName,
          logStreamName);
    } catch (Exception e) {
      LOGGER.error("Failed to initialize CloudWatch logging", e);
      cloudWatchEnabled = false;
    }
  }

  public void log(String level, String message, Map<String, Object> data) {
    if (!cloudWatchEnabled) {
      return;
    }

    try {
      if (filterEnabled && !shouldSendToCloudWatch(level, message, data)) {
        return;
      }
      // Resolve username from data or MDC
      String username = "anonymous";
      if (data != null && data.get("username") != null) {
        username = String.valueOf(data.get("username"));
      } else {
        String mdcUsername = MDC.get("username");
        if (mdcUsername != null && !mdcUsername.isEmpty()) {
          username = mdcUsername;
        }
      }

      // Resolve correlationId from data or MDC
      String correlationId = null;
      if (data != null && data.get("correlationId") != null) {
        correlationId = String.valueOf(data.get("correlationId"));
      } else {
        correlationId = MDC.get("correlationId");
      }

      // Normalize level to uppercase
      String normalizedLevel = level != null ? level.toUpperCase() : "INFO";

      // Create the full JSON object with all details
      Map<String, Object> fullData = new LinkedHashMap<>();
      fullData.put("level", normalizedLevel);
      fullData.put("username", username);
      fullData.put("message", message);
      fullData.put("timestamp", Instant.now().toString());
      if (correlationId != null && !correlationId.isEmpty()) {
        fullData.put("correlationId", correlationId);
      }

      // Add additional data but exclude username since it's already at top level
      if (data != null && !data.isEmpty()) {
        Map<String, Object> cleanData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
          if (!"username".equals(entry.getKey())) {
            cleanData.put(entry.getKey(), entry.getValue());
          }
        }
        if (!cleanData.isEmpty()) {
          fullData.put("data", cleanData);
        }
      }

      String jsonDetails = objectMapper.writeValueAsString(fullData);

      // Format: [LEVEL] [USERNAME] MESSAGE \n JSON
      String formattedMessage =
          String.format("[%s] [%s] %s%n%s", normalizedLevel, username, message, jsonDetails);

      // Check if message needs chunking
      if (formattedMessage.getBytes().length > MAX_MESSAGE_SIZE) {
        createChunkedLogEvents(formattedMessage, normalizedLevel, username, message);
      } else {
        InputLogEvent logEvent =
            InputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message(formattedMessage)
                .build();

        logEventQueue.offer(logEvent);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to queue log event", e);
    }
  }

  private boolean shouldSendToCloudWatch(String level, String message, Map<String, Object> data) {
    try {
      // Level threshold
      List<String> order = Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
      String normalized = level != null ? level.toUpperCase() : "INFO";
      int lvl = order.indexOf(normalized);
      int min = order.indexOf(minLevel != null ? minLevel.toUpperCase() : "INFO");
      if (lvl < 0 || min < 0) {
        return true; // fallback
      }
      if (lvl < min) {
        return false;
      }

      // Allow-list of event types from structured data (preferred)
      if (data != null && data.containsKey("eventType")) {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedEventTypesCsv.split(",")));
        if (!allowed.contains(String.valueOf(data.get("eventType")))) {
          return false;
        }
        return true;
      }

      // Logger-based allow-list (for logback appender path)
      if (data != null && data.containsKey("logger")) {
        String logger = String.valueOf(data.get("logger"));
        for (String prefix : allowedLoggersCsv.split(",")) {
          if (logger.startsWith(prefix.trim())) {
            return true;
          }
        }
        // Exclude frontend noise unless explicitly included
        if (!includeFrontendLogs) {
          return false;
        }
      }

      // Message pattern excludes
      if (excludeMessagePatterns != null && !excludeMessagePatterns.isEmpty()) {
        Pattern p = Pattern.compile(excludeMessagePatterns);
        if (message != null && p.matcher(message).find()) {
          return false;
        }
      }

      // Default: allow if above level threshold
      return true;
    } catch (Exception e) {
      // On any filter error, do not block logs
      return true;
    }
  }

  private void createChunkedLogEvents(
      String originalMessage, String level, String username, String shortMessage) {
    try {
      byte[] messageBytes = originalMessage.getBytes();
      int totalChunks = (int) Math.ceil((double) messageBytes.length / MAX_MESSAGE_SIZE);
      String chunkId = java.util.UUID.randomUUID().toString().substring(0, 8);
      long timestamp = Instant.now().toEpochMilli();

      LOGGER.info("Chunking large log message into {} parts (ID: {})", totalChunks, chunkId);

      for (int i = 0; i < totalChunks; i++) {
        int start = i * MAX_MESSAGE_SIZE;
        int end = Math.min(start + MAX_MESSAGE_SIZE, messageBytes.length);

        String chunk = new String(messageBytes, start, end - start);
        String chunkHeader =
            String.format(
                "[%s] [%s] [CHUNK %d/%d ID:%s] %s",
                level, username, i + 1, totalChunks, chunkId, shortMessage);

        String chunkMessage;
        if (i == 0) {
          // First chunk includes the header info
          chunkMessage = chunkHeader + "\n" + chunk;
        } else {
          // Subsequent chunks are just the data
          chunkMessage =
              String.format("[CHUNK %d/%d ID:%s]\n%s", i + 1, totalChunks, chunkId, chunk);
        }

        InputLogEvent chunkEvent =
            InputLogEvent.builder()
                .timestamp(timestamp + i) // Slight offset to maintain order
                .message(chunkMessage)
                .build();

        logEventQueue.offer(chunkEvent);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to create chunked log events", e);
      // Fallback: create a truncated single event
      String truncatedMessage =
          originalMessage.length() > MAX_MESSAGE_SIZE
              ? originalMessage.substring(0, MAX_MESSAGE_SIZE - 100) + "... [TRUNCATED]"
              : originalMessage;

      InputLogEvent fallbackEvent =
          InputLogEvent.builder()
              .timestamp(Instant.now().toEpochMilli())
              .message(truncatedMessage)
              .build();

      logEventQueue.offer(fallbackEvent);
    }
  }

  public void logFeedback(
      String username, String feedbackType, String feedback, Map<String, Object> context) {
    Map<String, Object> data = new java.util.HashMap<>();
    data.put("username", username);
    data.put("feedbackType", feedbackType);
    data.put("feedback", feedback);
    data.put("context", context);
    data.put("eventType", "USER_FEEDBACK");

    String feedbackMessage =
        String.format("User feedback received - Type: %s, Feedback: %s", feedbackType, feedback);
    log("INFO", feedbackMessage, data);
  }

  private void ensureLogGroupAndStreamExist() {
    try {
      // Try to create log group
      CreateLogGroupRequest createLogGroupRequest =
          CreateLogGroupRequest.builder().logGroupName(logGroupName).build();
      cloudWatchLogsClient.createLogGroup(createLogGroupRequest);
      LOGGER.info("Created CloudWatch log group: {}", logGroupName);
    } catch (ResourceAlreadyExistsException e) {
      // Log group already exists, which is fine
    }

    try {
      // Try to create log stream
      CreateLogStreamRequest createStreamRequest =
          CreateLogStreamRequest.builder()
              .logGroupName(logGroupName)
              .logStreamName(logStreamName)
              .build();
      cloudWatchLogsClient.createLogStream(createStreamRequest);
      LOGGER.info("Created CloudWatch log stream: {}", logStreamName);
    } catch (ResourceAlreadyExistsException e) {
      // Log stream already exists, which is fine
    }
  }

  private void uploadLogs() {
    if (logEventQueue.isEmpty()) {
      return;
    }

    List<InputLogEvent> batch = new ArrayList<>();
    int batchSize = 0;

    while (!logEventQueue.isEmpty() && batch.size() < MAX_BATCH_SIZE) {
      InputLogEvent event = logEventQueue.peek();
      if (event == null) {
        break;
      }

      int eventSize = event.message().getBytes().length + 26; // 26 bytes overhead per event
      if (batchSize + eventSize > MAX_BATCH_BYTES && !batch.isEmpty()) {
        break;
      }

      logEventQueue.poll();
      batch.add(event);
      batchSize += eventSize;
    }

    if (!batch.isEmpty()) {
      try {
        // Sort events by timestamp to ensure chronological order (CloudWatch requirement)
        batch.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));

        PutLogEventsRequest.Builder requestBuilder =
            PutLogEventsRequest.builder()
                .logGroupName(logGroupName)
                .logStreamName(logStreamName)
                .logEvents(batch);

        if (sequenceToken != null) {
          requestBuilder.sequenceToken(sequenceToken);
        }

        PutLogEventsResponse response = cloudWatchLogsClient.putLogEvents(requestBuilder.build());
        sequenceToken = response.nextSequenceToken();

      } catch (InvalidSequenceTokenException e) {
        sequenceToken = e.expectedSequenceToken();
        // Re-queue the events and retry with the correct sequence token
        batch.forEach(logEventQueue::offer);
        uploadLogs();
      } catch (Exception e) {
        LOGGER.error("Failed to upload logs to CloudWatch", e);
        // Re-queue the events
        batch.forEach(logEventQueue::offer);
      }
    }
  }

  public void shutdown() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
      // Upload any remaining logs
      uploadLogs();
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}

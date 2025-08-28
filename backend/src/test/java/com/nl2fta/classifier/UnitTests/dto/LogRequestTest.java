package com.nl2fta.classifier.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("LogRequest Tests")
class LogRequestTest {

  private LogRequest logRequest;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    logRequest = new LogRequest();
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("Getter and Setter Tests")
  class GetterSetterTests {

    @Test
    @DisplayName("Should set and get level correctly")
    void shouldSetAndGetLevel() {
      // Given
      String expectedLevel = "INFO";

      // When
      logRequest.setLevel(expectedLevel);

      // Then
      assertThat(logRequest.getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("Should set and get message correctly")
    void shouldSetAndGetMessage() {
      // Given
      String expectedMessage = "Test log message";

      // When
      logRequest.setMessage(expectedMessage);

      // Then
      assertThat(logRequest.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should set and get username correctly")
    void shouldSetAndGetUsername() {
      // Given
      String expectedUsername = "testuser";

      // When
      logRequest.setUsername(expectedUsername);

      // Then
      assertThat(logRequest.getUsername()).isEqualTo(expectedUsername);
    }

    @Test
    @DisplayName("Should set and get source correctly")
    void shouldSetAndGetSource() {
      // Given
      String expectedSource = "application";

      // When
      logRequest.setSource(expectedSource);

      // Then
      assertThat(logRequest.getSource()).isEqualTo(expectedSource);
    }

    @Test
    @DisplayName("Should set and get application correctly")
    void shouldSetAndGetApplication() {
      // Given
      String expectedApplication = "nl2fta-classifier";

      // When
      logRequest.setApplication(expectedApplication);

      // Then
      assertThat(logRequest.getApplication()).isEqualTo(expectedApplication);
    }

    @Test
    @DisplayName("Should set and get timestamp correctly")
    void shouldSetAndGetTimestamp() {
      // Given
      String expectedTimestamp = "2023-01-01T10:00:00Z";

      // When
      logRequest.setTimestamp(expectedTimestamp);

      // Then
      assertThat(logRequest.getTimestamp()).isEqualTo(expectedTimestamp);
    }

    @Test
    @DisplayName("Should set and get data correctly")
    void shouldSetAndGetData() {
      // Given
      Map<String, Object> expectedData = new HashMap<>();
      expectedData.put("key1", "value1");
      expectedData.put("key2", 42);
      expectedData.put("key3", true);

      // When
      logRequest.setData(expectedData);

      // Then
      assertThat(logRequest.getData()).isEqualTo(expectedData);
      assertThat(logRequest.getData().get("key1")).isEqualTo("value1");
      assertThat(logRequest.getData().get("key2")).isEqualTo(42);
      assertThat(logRequest.getData().get("key3")).isEqualTo(true);
    }
  }

  @Nested
  @DisplayName("Null Value Handling")
  class NullValueHandlingTests {

    @Test
    @DisplayName("Should handle null values correctly")
    void shouldHandleNullValues() {
      // When - Set all fields to null
      logRequest.setLevel(null);
      logRequest.setMessage(null);
      logRequest.setUsername(null);
      logRequest.setSource(null);
      logRequest.setApplication(null);
      logRequest.setTimestamp(null);
      logRequest.setData(null);

      // Then - All getters should return null
      assertThat(logRequest.getLevel()).isNull();
      assertThat(logRequest.getMessage()).isNull();
      assertThat(logRequest.getUsername()).isNull();
      assertThat(logRequest.getSource()).isNull();
      assertThat(logRequest.getApplication()).isNull();
      assertThat(logRequest.getTimestamp()).isNull();
      assertThat(logRequest.getData()).isNull();
    }

    @Test
    @DisplayName("Should handle empty strings correctly")
    void shouldHandleEmptyStrings() {
      // When - Set string fields to empty
      logRequest.setLevel("");
      logRequest.setMessage("");
      logRequest.setUsername("");
      logRequest.setSource("");
      logRequest.setApplication("");
      logRequest.setTimestamp("");

      // Then - All getters should return empty strings
      assertThat(logRequest.getLevel()).isEmpty();
      assertThat(logRequest.getMessage()).isEmpty();
      assertThat(logRequest.getUsername()).isEmpty();
      assertThat(logRequest.getSource()).isEmpty();
      assertThat(logRequest.getApplication()).isEmpty();
      assertThat(logRequest.getTimestamp()).isEmpty();
    }
  }

  @Nested
  @DisplayName("JSON Serialization Tests")
  class JsonSerializationTests {

    @Test
    @DisplayName("Should serialize to JSON correctly")
    void shouldSerializeToJsonCorrectly() throws IOException {
      // Given
      logRequest.setLevel("ERROR");
      logRequest.setMessage("Test error message");
      logRequest.setUsername("testuser");
      logRequest.setSource("frontend");
      logRequest.setApplication("nl2fta");
      logRequest.setTimestamp("2023-01-01T10:00:00Z");

      Map<String, Object> data = new HashMap<>();
      data.put("errorCode", 500);
      data.put("stack", "Error stack trace");
      logRequest.setData(data);

      // When
      String json = objectMapper.writeValueAsString(logRequest);

      // Then
      assertThat(json).contains("\"level\":\"ERROR\"");
      assertThat(json).contains("\"message\":\"Test error message\"");
      assertThat(json).contains("\"username\":\"testuser\"");
      assertThat(json).contains("\"source\":\"frontend\"");
      assertThat(json).contains("\"application\":\"nl2fta\"");
      assertThat(json).contains("\"timestamp\":\"2023-01-01T10:00:00Z\"");
      assertThat(json).contains("\"errorCode\":500");
      assertThat(json).contains("\"stack\":\"Error stack trace\"");
    }

    @Test
    @DisplayName("Should deserialize from JSON correctly")
    void shouldDeserializeFromJsonCorrectly() throws IOException {
      // Given
      String json =
          """
                {
                    "level": "WARN",
                    "message": "Warning message",
                    "username": "admin",
                    "source": "backend",
                    "application": "classifier",
                    "timestamp": "2023-01-01T12:00:00Z",
                    "data": {
                        "component": "semantic-type",
                        "duration": 1500
                    }
                }
                """;

      // When
      LogRequest deserializedRequest = objectMapper.readValue(json, LogRequest.class);

      // Then
      assertThat(deserializedRequest.getLevel()).isEqualTo("WARN");
      assertThat(deserializedRequest.getMessage()).isEqualTo("Warning message");
      assertThat(deserializedRequest.getUsername()).isEqualTo("admin");
      assertThat(deserializedRequest.getSource()).isEqualTo("backend");
      assertThat(deserializedRequest.getApplication()).isEqualTo("classifier");
      assertThat(deserializedRequest.getTimestamp()).isEqualTo("2023-01-01T12:00:00Z");
      assertThat(deserializedRequest.getData()).containsEntry("component", "semantic-type");
      assertThat(deserializedRequest.getData()).containsEntry("duration", 1500);
    }

    @Test
    @DisplayName("Should handle missing fields in JSON gracefully")
    void shouldHandleMissingFieldsInJsonGracefully() throws IOException {
      // Given - JSON with only some fields
      String json =
          """
                {
                    "level": "DEBUG",
                    "message": "Debug message"
                }
                """;

      // When
      LogRequest deserializedRequest = objectMapper.readValue(json, LogRequest.class);

      // Then
      assertThat(deserializedRequest.getLevel()).isEqualTo("DEBUG");
      assertThat(deserializedRequest.getMessage()).isEqualTo("Debug message");
      assertThat(deserializedRequest.getUsername()).isNull();
      assertThat(deserializedRequest.getSource()).isNull();
      assertThat(deserializedRequest.getApplication()).isNull();
      assertThat(deserializedRequest.getTimestamp()).isNull();
      assertThat(deserializedRequest.getData()).isNull();
    }
  }

  @Nested
  @DisplayName("Data Map Operations")
  class DataMapOperationsTests {

    @Test
    @DisplayName("Should handle complex data structures")
    void shouldHandleComplexDataStructures() {
      // Given
      Map<String, Object> complexData = new HashMap<>();
      complexData.put("string", "value");
      complexData.put("number", 123);
      complexData.put("boolean", false);
      complexData.put("null", null);

      Map<String, Object> nestedMap = new HashMap<>();
      nestedMap.put("nested", "value");
      complexData.put("nested", nestedMap);

      // When
      logRequest.setData(complexData);

      // Then
      assertThat(logRequest.getData()).hasSize(5);
      assertThat(logRequest.getData().get("string")).isEqualTo("value");
      assertThat(logRequest.getData().get("number")).isEqualTo(123);
      assertThat(logRequest.getData().get("boolean")).isEqualTo(false);
      assertThat(logRequest.getData().get("null")).isNull();
      assertThat(logRequest.getData().get("nested")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("Should handle empty data map")
    void shouldHandleEmptyDataMap() {
      // Given
      Map<String, Object> emptyData = new HashMap<>();

      // When
      logRequest.setData(emptyData);

      // Then
      assertThat(logRequest.getData()).isEmpty();
      assertThat(logRequest.getData()).hasSize(0);
    }
  }
}

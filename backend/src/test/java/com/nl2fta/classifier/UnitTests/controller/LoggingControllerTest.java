package com.nl2fta.classifier.UnitTests.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.controller.LoggingController;
import com.nl2fta.classifier.dto.LogRequest;
import com.nl2fta.classifier.service.CloudWatchLoggingService;

@WebMvcTest(LoggingController.class)
@DisplayName("LoggingController Tests")
class LoggingControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CloudWatchLoggingService cloudWatchLoggingService;

  @Nested
  @DisplayName("POST /api/logs")
  class LogEndpointTests {

    @Test
    @DisplayName("Should log message successfully")
    void shouldLogMessageSuccessfully() throws Exception {
      // Given
      LogRequest request = new LogRequest();
      request.setLevel("INFO");
      request.setMessage("Test log message");
      request.setUsername("testuser");
      request.setSource("frontend");
      request.setApplication("NL2FTA");
      request.setTimestamp("2024-01-01T12:00:00Z");

      Map<String, Object> data = new HashMap<>();
      data.put("key1", "value1");
      data.put("key2", 123);
      request.setData(data);

      doNothing()
          .when(cloudWatchLoggingService)
          .log(anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any());

      // When & Then
      mockMvc
          .perform(
              post("/api/logs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle missing required fields")
    void shouldHandleMissingRequiredFields() throws Exception {
      // Given - empty request
      LogRequest request = new LogRequest();

      // When & Then
      mockMvc
          .perform(
              post("/api/logs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle logging service exception")
    void shouldHandleLoggingServiceException() throws Exception {
      // Given
      LogRequest request = new LogRequest();
      request.setLevel("ERROR");
      request.setMessage("Test error message");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doThrow(new RuntimeException("CloudWatch error"))
          .when(cloudWatchLoggingService)
          .log(anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any());

      // When & Then
      mockMvc
          .perform(
              post("/api/logs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should handle malformed JSON")
    void shouldHandleMalformedJson() throws Exception {
      // When & Then
      mockMvc
          .perform(
              post("/api/logs").contentType(MediaType.APPLICATION_JSON).content("{ invalid json }"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty request body")
    void shouldHandleEmptyRequestBody() throws Exception {
      // When & Then
      mockMvc.perform(post("/api/logs")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle null data field")
    void shouldHandleNullDataField() throws Exception {
      // Given
      LogRequest request = new LogRequest();
      request.setLevel("INFO");
      request.setMessage("Test log message with null data");
      request.setUsername("testuser");
      request.setTimestamp("2024-01-01T12:00:00Z");
      request.setData(null);

      doNothing()
          .when(cloudWatchLoggingService)
          .log(anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any());

      // When & Then
      mockMvc
          .perform(
              post("/api/logs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle complex nested data")
    void shouldHandleComplexNestedData() throws Exception {
      // Given
      LogRequest request = new LogRequest();
      request.setLevel("DEBUG");
      request.setMessage("Complex data test");
      request.setTimestamp("2024-01-01T12:00:00Z");

      Map<String, Object> nestedData = new HashMap<>();
      nestedData.put("user", Map.of("id", 123, "name", "John"));
      nestedData.put("metrics", Map.of("duration", 150, "status", "OK"));
      request.setData(nestedData);

      doNothing()
          .when(cloudWatchLoggingService)
          .log(anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any());

      // When & Then
      mockMvc
          .perform(
              post("/api/logs")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }
  }

  @Nested
  @DisplayName("CORS and HTTP Methods")
  class CorsAndHttpMethodsTests {

    @Test
    @DisplayName("Should support CORS preflight for logs endpoint")
    void shouldSupportCorsPreflightForLogs() throws Exception {
      mockMvc
          .perform(
              options("/api/logs")
                  .header("Origin", "http://localhost:4200")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should not support invalid HTTP methods for logs")
    void shouldNotSupportInvalidHttpMethodsForLogs() throws Exception {
      // Only POST is supported; GET/DELETE/PUT should be 405
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/logs"))
          .andExpect(status().isMethodNotAllowed());
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                  "/api/logs"))
          .andExpect(status().isMethodNotAllowed());
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/logs"))
          .andExpect(status().isMethodNotAllowed());
    }
  }
}

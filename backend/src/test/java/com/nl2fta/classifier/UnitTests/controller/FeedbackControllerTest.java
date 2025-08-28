package com.nl2fta.classifier.UnitTests.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.nl2fta.classifier.controller.FeedbackController;
import com.nl2fta.classifier.dto.FeedbackRequest;
import com.nl2fta.classifier.service.CloudWatchLoggingService;

@WebMvcTest(FeedbackController.class)
@DisplayName("FeedbackController Tests")
class FeedbackControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CloudWatchLoggingService cloudWatchLoggingService;

  @Nested
  @DisplayName("POST /api/feedback")
  class SubmitFeedbackTests {

    @Test
    @DisplayName("Should submit feedback successfully")
    void shouldSubmitFeedbackSuccessfully() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("bug_report");
      request.setFeedback("Found an issue with the classification accuracy");
      request.setSemanticTypeName("EMAIL.ADDRESS");
      request.setDescription("Email detection not working properly");
      request.setPluginType("regex");
      request.setRegexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
      request.setHeaderPatterns("email,e-mail,mail");
      request.setUsername("testuser");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doNothing()
          .when(cloudWatchLoggingService)
          .logFeedback(anyString(), anyString(), anyString(), any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should submit feature request feedback")
    void shouldSubmitFeatureRequestFeedback() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("feature_request");
      request.setFeedback("Would like to see support for phone number detection");
      request.setUsername("user123");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doNothing()
          .when(cloudWatchLoggingService)
          .logFeedback(anyString(), anyString(), anyString(), any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Should submit general feedback")
    void shouldSubmitGeneralFeedback() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("general");
      request.setFeedback("Great tool, very helpful for data classification");
      request.setUsername("happyuser");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doNothing()
          .when(cloudWatchLoggingService)
          .logFeedback(anyString(), anyString(), anyString(), any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle semantic type improvement feedback")
    void shouldHandleSemanticTypeImprovementFeedback() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("semantic_type_improvement");
      request.setFeedback("The regex pattern for SSN needs improvement");
      request.setSemanticTypeName("SSN");
      request.setDescription("Social Security Number detection");
      request.setPluginType("regex");
      request.setRegexPattern("^\\d{3}-\\d{2}-\\d{4}$");
      request.setHeaderPatterns("ssn,social,security");
      request.setUsername("analyst");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doNothing()
          .when(cloudWatchLoggingService)
          .logFeedback(anyString(), anyString(), anyString(), any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle feedback service exception")
    void shouldHandleFeedbackServiceException() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("bug_report");
      request.setFeedback("Test feedback");
      request.setTimestamp("2024-01-01T12:00:00Z");

      doThrow(new RuntimeException("CloudWatch error"))
          .when(cloudWatchLoggingService)
          .logFeedback(any(), any(), any(), ArgumentMatchers.<Map<String, Object>>any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
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
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{ invalid json }"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty request body")
    void shouldHandleEmptyRequestBody() throws Exception {
      // When & Then
      mockMvc.perform(post("/api/feedback")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle null feedback content")
    void shouldHandleNullFeedbackContent() throws Exception {
      // Given
      FeedbackRequest request = new FeedbackRequest();
      request.setType("bug_report");
      request.setFeedback(null);

      doNothing()
          .when(cloudWatchLoggingService)
          .logFeedback(anyString(), anyString(), anyString(), any());

      // When & Then
      mockMvc
          .perform(
              post("/api/feedback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("CORS and HTTP Methods")
  class CorsAndHttpMethods {

    @Test
    @DisplayName("Should support CORS preflight for feedback endpoint")
    void shouldSupportCorsPreflightForFeedback() throws Exception {
      mockMvc
          .perform(
              options("/api/feedback")
                  .header("Origin", "http://localhost:4200")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should not support GET method for feedback")
    void shouldNotSupportGetMethod() throws Exception {
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  "/api/feedback"))
          .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should not support DELETE method for feedback")
    void shouldNotSupportDeleteMethod() throws Exception {
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                  "/api/feedback"))
          .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should not support PUT method for feedback")
    void shouldNotSupportPutMethod() throws Exception {
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                  "/api/feedback"))
          .andExpect(status().isMethodNotAllowed());
    }
  }
}

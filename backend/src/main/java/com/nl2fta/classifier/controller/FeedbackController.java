package com.nl2fta.classifier.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.dto.FeedbackRequest;
import com.nl2fta.classifier.service.CloudWatchLoggingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/feedback")
@Tag(name = "Feedback", description = "User feedback endpoints")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class FeedbackController {
  private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackController.class);
  private final CloudWatchLoggingService cloudWatchLoggingService;

  public FeedbackController(CloudWatchLoggingService cloudWatchLoggingService) {
    this.cloudWatchLoggingService = cloudWatchLoggingService;
  }

  @PostMapping
  @Operation(
      summary = "Submit user feedback",
      description = "Logs user feedback about semantic type generation")
  public ResponseEntity<Map<String, String>> submitFeedback(@RequestBody FeedbackRequest request) {
    try {
      Map<String, Object> context = new HashMap<>();
      context.put("semanticTypeName", request.getSemanticTypeName());
      context.put("description", request.getDescription());
      context.put("pluginType", request.getPluginType());
      context.put("regexPattern", request.getRegexPattern());
      context.put("headerPatterns", request.getHeaderPatterns());
      context.put("timestamp", request.getTimestamp());
      // Link to generation flow via correlationId if present
      String correlationId = MDC.get("correlationId");
      if (correlationId != null && !correlationId.isEmpty()) {
        context.put("correlationId", correlationId);
      }

      // Log to CloudWatch only
      cloudWatchLoggingService.logFeedback(
          request.getUsername(), request.getType(), request.getFeedback(), context);

      Map<String, String> response = new HashMap<>();
      response.put("status", "success");
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      LOGGER.error("Failed to submit feedback", e);
      throw new RuntimeException("Failed to submit feedback", e);
    }
  }
}

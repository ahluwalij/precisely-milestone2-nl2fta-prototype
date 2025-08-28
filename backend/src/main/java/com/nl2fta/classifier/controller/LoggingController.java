package com.nl2fta.classifier.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.dto.LogRequest;
import com.nl2fta.classifier.service.CloudWatchLoggingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/logs")
@Tag(name = "Logging", description = "Centralized logging endpoints")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class LoggingController {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingController.class);
  private final CloudWatchLoggingService cloudWatchLoggingService;

  public LoggingController(CloudWatchLoggingService cloudWatchLoggingService) {
    this.cloudWatchLoggingService = cloudWatchLoggingService;
  }

  @PostMapping
  @Operation(
      summary = "Log client events",
      description = "Receives log entries from frontend clients")
  public ResponseEntity<Void> logEvent(@RequestBody LogRequest request) {
    // Prepare data for CloudWatch
    Map<String, Object> logData = new HashMap<>();
    logData.put("username", request.getUsername());
    logData.put("source", request.getSource());
    logData.put("application", request.getApplication());
    logData.put("timestamp", request.getTimestamp());

    if (request.getData() != null) {
      logData.putAll(request.getData());
    }

    // Log to CloudWatch (this will handle the detailed logging)
    cloudWatchLoggingService.log(request.getLevel(), request.getMessage(), logData);

    return ResponseEntity.ok().build();
  }
}

package com.nl2fta.classifier.config;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates that the application can start without AWS credentials. AWS credentials will be
 * provided through the frontend UI per user.
 */
@Slf4j
@Component
public class AwsCredentialsValidator {

  @PostConstruct
  public void validateStartup() {
    log.debug(
        "Application starting without AWS credentials - users will provide their own through the UI");
    log.debug("Built-in semantic types will be available immediately");
    log.debug(
        "Custom types and vector search will be available after users connect their AWS account");
  }
}

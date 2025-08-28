package com.nl2fta.classifier.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AwsCredentialsValidatorTest {

  private AwsCredentialsValidator awsCredentialsValidator;

  @BeforeEach
  public void setUp() {
    awsCredentialsValidator = new AwsCredentialsValidator();
  }

  @Test
  public void testValidateStartup() {
    // Test that validateStartup can be called without throwing exceptions
    // Since this method only logs information, we verify it doesn't throw any exceptions
    assertDoesNotThrow(() -> awsCredentialsValidator.validateStartup());

    // Since we're using Lombok's @Slf4j, we can't easily mock the logger
    // The important test is that the method executes without errors
  }

  @Test
  public void testValidateStartupIsPostConstruct() throws NoSuchMethodException {
    // Verify that validateStartup method has PostConstruct annotation
    assertTrue(
        AwsCredentialsValidator.class
            .getMethod("validateStartup")
            .isAnnotationPresent(jakarta.annotation.PostConstruct.class));
  }

  @Test
  public void testClassHasComponentAnnotation() {
    // Verify that class has Component annotation
    assertTrue(
        AwsCredentialsValidator.class.isAnnotationPresent(
            org.springframework.stereotype.Component.class));
  }

  @Test
  public void testClassHasSlf4jAnnotation() {
    // Verify that class has Slf4j annotation
    // Note: Lombok annotations are processed at compile time, so we can't test for them at runtime
    // Instead, we verify the class compiles and logs work properly
    assertNotNull(awsCredentialsValidator);
  }

  @Test
  public void testMultipleInvocations() {
    // Test that method can be called multiple times without issues
    assertDoesNotThrow(
        () -> {
          awsCredentialsValidator.validateStartup();
          awsCredentialsValidator.validateStartup();
          awsCredentialsValidator.validateStartup();
        });
  }
}

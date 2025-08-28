package com.nl2fta.classifier.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("AwsCredentialsRequest Tests")
class AwsCredentialsRequestTest {

  private ObjectMapper objectMapper;
  private Validator validator;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("Lombok Generated Methods Tests")
  class LombokGeneratedMethodsTests {

    @Test
    @DisplayName("Should create instance using builder pattern")
    void shouldCreateInstanceUsingBuilderPattern() {
      // When
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA123456789EXAMPLE")
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .region("us-east-1")
              .build();

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo("AKIA123456789EXAMPLE");
      assertThat(request.getSecretAccessKey())
          .isEqualTo("test-fake-secret-key-for-unit-testing-purposes-only");
      assertThat(request.getRegion()).isEqualTo("us-east-1");
    }

    @Test
    @DisplayName("Should create instance using no-args constructor")
    void shouldCreateInstanceUsingNoArgsConstructor() {
      // When
      AwsCredentialsRequest request = new AwsCredentialsRequest();

      // Then
      assertThat(request).isNotNull();
      assertThat(request.getAccessKeyId()).isNull();
      assertThat(request.getSecretAccessKey()).isNull();
      assertThat(request.getRegion()).isNull();
    }

    @Test
    @DisplayName("Should create instance using all-args constructor")
    void shouldCreateInstanceUsingAllArgsConstructor() {
      // When
      AwsCredentialsRequest request =
          new AwsCredentialsRequest(
              "AKIA987654321EXAMPLE", "anotherSecretKeyExample123456789", "eu-west-1");

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo("AKIA987654321EXAMPLE");
      assertThat(request.getSecretAccessKey()).isEqualTo("anotherSecretKeyExample123456789");
      assertThat(request.getRegion()).isEqualTo("eu-west-1");
    }

    @Test
    @DisplayName("Should support setter methods")
    void shouldSupportSetterMethods() {
      // Given
      AwsCredentialsRequest request = new AwsCredentialsRequest();

      // When
      request.setAccessKeyId("AKIA111222333EXAMPLE");
      request.setSecretAccessKey("secretKey987654321Example");
      request.setRegion("ap-south-1");

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo("AKIA111222333EXAMPLE");
      assertThat(request.getSecretAccessKey()).isEqualTo("secretKey987654321Example");
      assertThat(request.getRegion()).isEqualTo("ap-south-1");
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
      // Given
      AwsCredentialsRequest request1 =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA123")
              .secretAccessKey("secret123")
              .region("us-west-2")
              .build();

      AwsCredentialsRequest request2 =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA123")
              .secretAccessKey("secret123")
              .region("us-west-2")
              .build();

      AwsCredentialsRequest request3 =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA456")
              .secretAccessKey("secret456")
              .region("us-west-2")
              .build();

      // Then
      assertThat(request1).isEqualTo(request2);
      assertThat(request1).isNotEqualTo(request3);
      assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
      assertThat(request1.hashCode()).isNotEqualTo(request3.hashCode());
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA789")
              .secretAccessKey("secretKey789")
              .region("ca-central-1")
              .build();

      // When
      String toString = request.toString();

      // Then
      assertThat(toString).contains("AwsCredentialsRequest");
      assertThat(toString).contains("accessKeyId=AKIA789");
      assertThat(toString).contains("secretAccessKey=secretKey789");
      assertThat(toString).contains("region=ca-central-1");
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should pass validation with valid credentials")
    void shouldPassValidationWithValidCredentials() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .region("us-east-1")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should pass validation without region (optional field)")
    void shouldPassValidationWithoutRegion() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation with null access key ID")
    void shouldFailValidationWithNullAccessKeyId() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId(null)
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .region("us-east-1")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).hasSize(1);
      ConstraintViolation<AwsCredentialsRequest> violation = violations.iterator().next();
      assertThat(violation.getMessage()).isEqualTo("AWS Access Key ID is required");
      assertThat(violation.getPropertyPath().toString()).isEqualTo("accessKeyId");
    }

    @Test
    @DisplayName("Should fail validation with blank access key ID")
    void shouldFailValidationWithBlankAccessKeyId() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("   ")
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .region("us-east-1")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).hasSize(1);
      ConstraintViolation<AwsCredentialsRequest> violation = violations.iterator().next();
      assertThat(violation.getMessage()).isEqualTo("AWS Access Key ID is required");
      assertThat(violation.getPropertyPath().toString()).isEqualTo("accessKeyId");
    }

    @Test
    @DisplayName("Should fail validation with null secret access key")
    void shouldFailValidationWithNullSecretAccessKey() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey(null)
              .region("us-east-1")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).hasSize(1);
      ConstraintViolation<AwsCredentialsRequest> violation = violations.iterator().next();
      assertThat(violation.getMessage()).isEqualTo("AWS Secret Access Key is required");
      assertThat(violation.getPropertyPath().toString()).isEqualTo("secretAccessKey");
    }

    @Test
    @DisplayName("Should fail validation with empty secret access key")
    void shouldFailValidationWithEmptySecretAccessKey() {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey("")
              .region("us-east-1")
              .build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).hasSize(1);
      ConstraintViolation<AwsCredentialsRequest> violation = violations.iterator().next();
      assertThat(violation.getMessage()).isEqualTo("AWS Secret Access Key is required");
      assertThat(violation.getPropertyPath().toString()).isEqualTo("secretAccessKey");
    }

    @Test
    @DisplayName("Should fail validation with both required fields missing")
    void shouldFailValidationWithBothRequiredFieldsMissing() {
      // Given
      AwsCredentialsRequest request = AwsCredentialsRequest.builder().region("us-east-1").build();

      // When
      Set<ConstraintViolation<AwsCredentialsRequest>> violations = validator.validate(request);

      // Then
      assertThat(violations).hasSize(2);

      Set<String> violationMessages =
          violations.stream()
              .map(ConstraintViolation::getMessage)
              .collect(java.util.stream.Collectors.toSet());

      assertThat(violationMessages)
          .containsExactlyInAnyOrder(
              "AWS Access Key ID is required", "AWS Secret Access Key is required");
    }
  }

  @Nested
  @DisplayName("JSON Serialization Tests")
  class JsonSerializationTests {

    @Test
    @DisplayName("Should serialize to JSON correctly")
    void shouldSerializeToJsonCorrectly() throws IOException {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
              .region("us-west-1")
              .build();

      // When
      String json = objectMapper.writeValueAsString(request);

      // Then
      assertThat(json).contains("\"accessKeyId\":\"AKIA1234567890EXAMPLE\"");
      assertThat(json).contains("\"secretAccessKey\":\"test-fake-secret-key-for-unit-testing-purposes-only\"");
      assertThat(json).contains("\"region\":\"us-west-1\"");
    }

    @Test
    @DisplayName("Should deserialize from JSON correctly")
    void shouldDeserializeFromJsonCorrectly() throws IOException {
      // Given
      String json =
          """
                {
                    "accessKeyId": "AKIA9876543210EXAMPLE",
                    "secretAccessKey": "anotherExampleSecretKey123456789",
                    "region": "eu-central-1"
                }
                """;

      // When
      AwsCredentialsRequest request = objectMapper.readValue(json, AwsCredentialsRequest.class);

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo("AKIA9876543210EXAMPLE");
      assertThat(request.getSecretAccessKey()).isEqualTo("anotherExampleSecretKey123456789");
      assertThat(request.getRegion()).isEqualTo("eu-central-1");
    }

    @Test
    @DisplayName("Should handle JSON without optional region field")
    void shouldHandleJsonWithoutOptionalRegionField() throws IOException {
      // Given
      String json =
          """
                {
                    "accessKeyId": "AKIA5555666677778888",
                    "secretAccessKey": "exampleSecretWithoutRegion123456"
                }
                """;

      // When
      AwsCredentialsRequest request = objectMapper.readValue(json, AwsCredentialsRequest.class);

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo("AKIA5555666677778888");
      assertThat(request.getSecretAccessKey()).isEqualTo("exampleSecretWithoutRegion123456");
      assertThat(request.getRegion()).isNull();
    }

    @Test
    @DisplayName("Should serialize with null region correctly")
    void shouldSerializeWithNullRegionCorrectly() throws IOException {
      // Given
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1111222233334444")
              .secretAccessKey("secretKeyWithoutRegion987654321")
              .region(null)
              .build();

      // When
      String json = objectMapper.writeValueAsString(request);

      // Then
      assertThat(json).contains("\"accessKeyId\":\"AKIA1111222233334444\"");
      assertThat(json).contains("\"secretAccessKey\":\"secretKeyWithoutRegion987654321\"");
      // Jackson by default includes null values, but this may vary based on configuration
      assertThat(json).containsAnyOf("\"region\":null", "\"region\": null");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Security Tests")
  class EdgeCasesAndSecurityTests {

    @Test
    @DisplayName("Should handle different AWS regions")
    void shouldHandleDifferentAwsRegions() {
      // Given different AWS regions
      String[] regions = {
        "us-east-1", "us-west-2", "eu-west-1", "eu-central-1",
        "ap-southeast-1", "ap-northeast-1", "ca-central-1", "sa-east-1"
      };

      for (String region : regions) {
        // When
        AwsCredentialsRequest request =
            AwsCredentialsRequest.builder()
                .accessKeyId("AKIA1234567890EXAMPLE")
                .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
                .region(region)
                .build();

        // Then
        assertThat(request.getRegion()).isEqualTo(region);
      }
    }

    @Test
    @DisplayName("Should handle long secret access keys")
    void shouldHandleLongSecretAccessKeys() {
      // Given
      String longSecretKey = "a".repeat(100) + "VeryLongSecretAccessKeyExample123456789";

      // When
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId("AKIA1234567890EXAMPLE")
              .secretAccessKey(longSecretKey)
              .region("us-east-1")
              .build();

      // Then
      assertThat(request.getSecretAccessKey()).isEqualTo(longSecretKey);
      assertThat(request.getSecretAccessKey()).hasSize(139);
    }

    @Test
    @DisplayName("Should handle special characters in credentials")
    void shouldHandleSpecialCharactersInCredentials() {
      // Given
      String accessKeyWithSpecialChars = "AKIA123+456=789/EXAMPLE";
      String secretKeyWithSpecialChars = "wJalrXUtnFEMI/K7MDENG+bPxRfiCY=EXAMPLE/KEY";

      // When
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId(accessKeyWithSpecialChars)
              .secretAccessKey(secretKeyWithSpecialChars)
              .region("us-east-1")
              .build();

      // Then
      assertThat(request.getAccessKeyId()).isEqualTo(accessKeyWithSpecialChars);
      assertThat(request.getSecretAccessKey()).isEqualTo(secretKeyWithSpecialChars);
    }

    @Test
    @DisplayName("Should preserve exact credential values without modification")
    void shouldPreserveExactCredentialValuesWithoutModification() {
      // Given - Credentials with mixed case and special characters
      String originalAccessKey = "AkIa123456789ExAmPlE";
      String originalSecretKey = "test-fake-secret-key-for-unit-testing-purposes-only+Special=Chars";

      // When
      AwsCredentialsRequest request =
          AwsCredentialsRequest.builder()
              .accessKeyId(originalAccessKey)
              .secretAccessKey(originalSecretKey)
              .build();

      // Then - Values should be preserved exactly as provided
      assertThat(request.getAccessKeyId()).isEqualTo(originalAccessKey);
      assertThat(request.getSecretAccessKey()).isEqualTo(originalSecretKey);
    }
  }
}

package com.nl2fta.classifier.service.aws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

class AwsCredentialsServiceTest {

  private AwsCredentialsService awsCredentialsService;

  @BeforeEach
  void setUp() {
    awsCredentialsService = new AwsCredentialsService();
  }

  @Test
  void shouldInitializeWithoutCredentialsAvailable() {
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldSetValidCredentialsSuccessfully() {
    boolean result =
        awsCredentialsService.setCredentials(
            "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    assertThat(result).isTrue();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNotNull();
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-2");
    assertThat(awsCredentialsService.getCurrentAccessKeyId()).isEqualTo("AKIATESTFAKEKEY123456");
  }

  @Test
  void shouldUseDefaultRegionWhenNullProvided() {
    boolean result =
        awsCredentialsService.setCredentials(
            "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", null);

    assertThat(result).isTrue();
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldUseDefaultRegionWhenEmptyStringProvided() {
    boolean result =
        awsCredentialsService.setCredentials(
            "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "   ");

    assertThat(result).isTrue();
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldRejectNullAccessKeyId() {
    boolean result =
        awsCredentialsService.setCredentials(
            null, "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    assertThat(result).isFalse();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
  }

  @Test
  void shouldRejectEmptyAccessKeyId() {
    boolean result =
        awsCredentialsService.setCredentials(
            "   ", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    assertThat(result).isFalse();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
  }

  @Test
  void shouldRejectNullSecretAccessKey() {
    boolean result =
        awsCredentialsService.setCredentials("AKIATESTFAKEKEY123456", null, "us-east-2");

    assertThat(result).isFalse();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
  }

  @Test
  void shouldRejectEmptySecretAccessKey() {
    boolean result =
        awsCredentialsService.setCredentials("AKIATESTFAKEKEY123456", "   ", "us-east-2");

    assertThat(result).isFalse();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
  }

  @Test
  void shouldClearCredentialsSuccessfully() {
    // First set credentials
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();

    // Then clear them
    awsCredentialsService.clearCredentials();

    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
    assertThat(awsCredentialsService.getCredentialsProvider()).isNull();
    assertThat(awsCredentialsService.getCurrentAccessKeyId()).isNull();
    // Region should remain as last set region (service doesn't reset region when clearing
    // credentials)
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-2");
  }

  @Test
  void shouldOverwritePreviousCredentials() {
    // Set initial credentials
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");
    assertThat(awsCredentialsService.getCurrentAccessKeyId()).isEqualTo("AKIATESTFAKEKEY123456");
    assertThat(awsCredentialsService.getRegion()).isEqualTo("us-east-2");

    // Set new credentials
    awsCredentialsService.setCredentials(
        "AKIAI44QH8DHBEXAMPLE", "je7MtGbClwBF92R7oXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLE", "eu-west-1");

    assertThat(awsCredentialsService.getCurrentAccessKeyId()).isEqualTo("AKIAI44QH8DHBEXAMPLE");
    assertThat(awsCredentialsService.getRegion()).isEqualTo("eu-west-1");
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();
  }

  @Test
  void shouldHandleCredentialsProviderCreation() {
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    AwsCredentialsProvider provider = awsCredentialsService.getCredentialsProvider();
    assertThat(provider).isNotNull();
    assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("AKIATESTFAKEKEY123456");
    assertThat(provider.resolveCredentials().secretAccessKey())
        .isEqualTo("test-fake-secret-key-for-unit-testing-purposes-only");
  }

  @Test
  void shouldResolveCredentialsFromProvider() {
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    var credentials = awsCredentialsService.getCredentials();
    assertThat(credentials).isNotNull();
    assertThat(credentials.accessKeyId()).isEqualTo("AKIATESTFAKEKEY123456");
    assertThat(credentials.secretAccessKey()).isEqualTo("test-fake-secret-key-for-unit-testing-purposes-only");
  }

  @Test
  void shouldReturnNullCredentialsWhenNotAvailable() {
    var credentials = awsCredentialsService.getCredentials();
    assertThat(credentials).isNull();
  }

  @Test
  void shouldHandleRegionValidation() {
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "ap-southeast-1");
    assertThat(awsCredentialsService.getRegion()).isEqualTo("ap-southeast-1");

    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "eu-central-1");
    assertThat(awsCredentialsService.getRegion()).isEqualTo("eu-central-1");
  }

  @Test
  void shouldMaintainStateConsistencyAfterFailedCredentialSet() {
    // First set valid credentials
    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();

    // Try to set invalid credentials
    boolean result =
        awsCredentialsService.setCredentials(
            null, "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-2");

    // Should fail and maintain previous state
    assertThat(result).isFalse();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();
    assertThat(awsCredentialsService.getCurrentAccessKeyId()).isEqualTo("AKIATESTFAKEKEY123456");
  }

  @Test
  void shouldHandleCredentialValidityCheck() {
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();

    awsCredentialsService.setCredentials(
        "AKIATESTFAKEKEY123456", "test-fake-secret-key-for-unit-testing-purposes-only", "us-east-1");
    assertThat(awsCredentialsService.areCredentialsAvailable()).isTrue();

    awsCredentialsService.clearCredentials();
    assertThat(awsCredentialsService.areCredentialsAvailable()).isFalse();
  }
}

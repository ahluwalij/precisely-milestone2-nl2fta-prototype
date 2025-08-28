package com.nl2fta.classifier.service.aws;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Service for managing AWS credentials dynamically. Only uses credentials provided through the
 * frontend UI, not environment variables. This ensures each user connects to their own AWS account.
 */
@Slf4j
@Service
public class AwsCredentialsService {

  private volatile AwsCredentialsProvider credentialsProvider = null;
  private volatile boolean credentialsAvailable = false;
  private volatile String currentAccessKeyId = null;
  private volatile String currentSecretAccessKey = null;
  private volatile String currentRegion = "us-east-1";

  public AwsCredentialsService() {
    // Wait for frontend to provide credentials
    log.debug(
        "Application starting without AWS credentials - users will provide their own through the UI");
  }

  /**
   * Check if AWS credentials are currently available. Returns true if credentials were set through
   * the frontend.
   *
   * @return true if credentials are available, false otherwise
   */
  public boolean areCredentialsAvailable() {
    return credentialsAvailable && credentialsProvider != null;
  }

  /**
   * Set AWS credentials from the frontend.
   *
   * @param accessKeyId the AWS access key ID
   * @param secretAccessKey the AWS secret access key
   * @param region the AWS region (optional, defaults to us-east-1)
   * @return true if credentials were set successfully
   */
  public boolean setCredentials(String accessKeyId, String secretAccessKey, String region) {
    try {
      if (accessKeyId == null
          || accessKeyId.trim().isEmpty()
          || secretAccessKey == null
          || secretAccessKey.trim().isEmpty()) {
        log.warn("Invalid credentials provided - access key or secret key is empty");
        return false;
      }

      // Create new credentials provider with the provided credentials
      AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
      this.credentialsProvider = StaticCredentialsProvider.create(awsCredentials);

      this.currentAccessKeyId = accessKeyId;
      this.currentSecretAccessKey = secretAccessKey;
      this.currentRegion = region != null && !region.trim().isEmpty() ? region : "us-east-1";

      this.credentialsAvailable = true;
      log.info("AWS credentials set successfully from frontend for region: {}", this.currentRegion);

      return true;
    } catch (Exception e) {
      log.error("Failed to set AWS credentials", e);
      this.credentialsAvailable = false;
      this.credentialsProvider = null;
      return false;
    }
  }

  /** Clear AWS credentials (user disconnected). */
  public void clearCredentials() {

    this.credentialsProvider = null;
    this.credentialsAvailable = false;
    this.currentAccessKeyId = null;
    this.currentSecretAccessKey = null;
    log.info("AWS credentials cleared - switching to built-in types only");
  }

  /**
   * Get the current AWS region.
   *
   * @return the AWS region
   */
  public String getRegion() {
    return currentRegion;
  }

  /**
   * Get the credentials provider for AWS services.
   *
   * @return the credentials provider or null if not set
   */
  public AwsCredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  /**
   * Get current credentials if available.
   *
   * @return AWS credentials or null if not available
   */
  public AwsCredentials getCredentials() {
    if (!areCredentialsAvailable()) {
      return null;
    }

    try {
      return credentialsProvider.resolveCredentials();
    } catch (SdkClientException e) {
      log.warn("Failed to resolve AWS credentials", e);
      credentialsAvailable = false;
      return null;
    }
  }

  /**
   * Get the current access key ID for display purposes.
   *
   * @return the current access key ID or null if not available
   */
  public String getCurrentAccessKeyId() {
    return currentAccessKeyId;
  }

  /**
   * Get the current secret access key for display purposes. Note: This returns the actual secret
   * key - use carefully.
   *
   * @return the current secret access key or null if not available
   */
  public String getCurrentSecretAccessKey() {
    return currentSecretAccessKey;
  }
}

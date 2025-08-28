package com.nl2fta.classifier.service.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsRegionModelService Tests")
class AwsRegionModelServiceTest {

  private AwsRegionModelService awsRegionModelService;
  private static final String TEST_CLAUDE_MODEL_ID = "anthropic.claude-sonnet-4-20250514";
  private static final String INVALID_ACCESS_KEY = "invalid-access-key";
  private static final String INVALID_SECRET_KEY = "invalid-secret-key";
  private static final String VALID_ACCESS_KEY = "AKIATESTFAKEKEY123456";
  private static final String VALID_SECRET_KEY = "test-fake-secret-key-for-unit-testing-purposes-only";
  private static final String TITAN_EMBEDDING_MODEL_ID = "amazon.titan-embed-text-v1";

  @BeforeEach
  void setUp() {
    awsRegionModelService = new AwsRegionModelService();
    ReflectionTestUtils.setField(awsRegionModelService, "claudeModelId", TEST_CLAUDE_MODEL_ID);
  }

  @Nested
  @DisplayName("Region Management Tests")
  class RegionManagementTests {

    @Test
    @DisplayName("Should return correct Bedrock regions")
    void shouldReturnCorrectBedrockRegions() {
      List<AwsRegionModelService.RegionInfo> regions =
          awsRegionModelService.getAvailableBedrockRegions(VALID_ACCESS_KEY, VALID_SECRET_KEY);

      assertThat(regions).isNotNull().hasSize(4);

      List<String> regionIds =
          regions.stream()
              .map(AwsRegionModelService.RegionInfo::getRegionId)
              .collect(Collectors.toList());

      assertThat(regionIds)
          .containsExactlyInAnyOrder("us-east-1", "us-east-2", "us-west-1", "us-west-2");

      AwsRegionModelService.RegionInfo usEast1 =
          regions.stream()
              .filter(r -> "us-east-1".equals(r.getRegionId()))
              .findFirst()
              .orElse(null);

      assertThat(usEast1).isNotNull();
      assertThat(usEast1.getDisplayName()).isEqualTo("us-east-1 - US East (N. Virginia)");
    }

    @Test
    @DisplayName("Should handle null credentials when getting regions")
    void shouldHandleNullCredentialsWhenGettingRegions() {
      List<AwsRegionModelService.RegionInfo> regions =
          awsRegionModelService.getAvailableBedrockRegions(null, null);

      assertThat(regions).isNotNull().hasSize(4);
    }

    @Test
    @DisplayName("Should handle empty credentials when getting regions")
    void shouldHandleEmptyCredentialsWhenGettingRegions() {
      List<AwsRegionModelService.RegionInfo> regions =
          awsRegionModelService.getAvailableBedrockRegions("", "");

      assertThat(regions).isNotNull().hasSize(4);
    }

    @Test
    @DisplayName("Should return all region display names correctly")
    void shouldReturnAllRegionDisplayNamesCorrectly() {
      List<AwsRegionModelService.RegionInfo> regions =
          awsRegionModelService.getAvailableBedrockRegions(VALID_ACCESS_KEY, VALID_SECRET_KEY);

      assertThat(regions)
          .extracting(AwsRegionModelService.RegionInfo::getDisplayName)
          .containsExactlyInAnyOrder(
              "us-east-1 - US East (N. Virginia)",
              "us-east-2 - US East (Ohio)",
              "us-west-1 - US West (N. California)",
              "us-west-2 - US West (Oregon)");
    }

    @Test
    @DisplayName("Should return consistent results across multiple calls")
    void shouldReturnConsistentResultsAcrossMultipleCalls() {
      List<AwsRegionModelService.RegionInfo> regions1 =
          awsRegionModelService.getAvailableBedrockRegions(VALID_ACCESS_KEY, VALID_SECRET_KEY);
      List<AwsRegionModelService.RegionInfo> regions2 =
          awsRegionModelService.getAvailableBedrockRegions(VALID_ACCESS_KEY, VALID_SECRET_KEY);

      assertThat(regions1).hasSize(regions2.size());
      for (int i = 0; i < regions1.size(); i++) {
        assertThat(regions1.get(i).getRegionId()).isEqualTo(regions2.get(i).getRegionId());
        assertThat(regions1.get(i).getDisplayName()).isEqualTo(regions2.get(i).getDisplayName());
      }
    }
  }

  @Nested
  @DisplayName("Model Management Tests")
  class ModelManagementTests {

    @Test
    @DisplayName("Should return Claude Sonnet 4.0 model")
    void shouldReturnClaudeSonnet4Model() {
      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");

      assertThat(models).isNotNull().hasSize(1);

      AwsRegionModelService.ModelInfo model = models.get(0);
      assertThat(model.getModelId()).isEqualTo(TEST_CLAUDE_MODEL_ID);
      assertThat(model.getModelName()).isEqualTo("Claude Sonnet 4.0");
      assertThat(model.getProvider()).isEqualTo("Anthropic");
      assertThat(model.getInputModalities()).containsExactly("TEXT");
      assertThat(model.getOutputModalities()).containsExactly("TEXT");
      assertThat(model.isRequiresInferenceProfile()).isTrue();
    }

    @Test
    @DisplayName("Should return same model for all regions")
    void shouldReturnSameModelForAllRegions() {
      List<AwsRegionModelService.ModelInfo> models1 =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");
      List<AwsRegionModelService.ModelInfo> models2 =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-2");

      assertThat(models1).hasSize(models2.size());
      assertThat(models1.get(0).getModelId()).isEqualTo(models2.get(0).getModelId());
    }

    @Test
    @DisplayName("Should handle null credentials when getting models")
    void shouldHandleNullCredentialsWhenGettingModels() {
      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels(null, null, "us-east-1");

      assertThat(models).isNotNull().hasSize(1);
      assertThat(models.get(0).getModelId()).isEqualTo(TEST_CLAUDE_MODEL_ID);
    }

    @Test
    @DisplayName("Should handle empty credentials when getting models")
    void shouldHandleEmptyCredentialsWhenGettingModels() {
      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels("", "", "us-east-1");

      assertThat(models).isNotNull().hasSize(1);
      assertThat(models.get(0).getModelId()).isEqualTo(TEST_CLAUDE_MODEL_ID);
    }

    @Test
    @DisplayName("Should handle null region when getting models")
    void shouldHandleNullRegionWhenGettingModels() {
      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, null);

      assertThat(models).isNotNull().hasSize(1);
      assertThat(models.get(0).getModelId()).isEqualTo(TEST_CLAUDE_MODEL_ID);
    }

    @Test
    @DisplayName("Should return consistent model results across multiple calls")
    void shouldReturnConsistentModelResultsAcrossMultipleCalls() {
      List<AwsRegionModelService.ModelInfo> models1 =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");
      List<AwsRegionModelService.ModelInfo> models2 =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");

      assertThat(models1).hasSize(models2.size());
      for (int i = 0; i < models1.size(); i++) {
        assertThat(models1.get(i).getModelId()).isEqualTo(models2.get(i).getModelId());
        assertThat(models1.get(i).getModelName()).isEqualTo(models2.get(i).getModelName());
        assertThat(models1.get(i).getProvider()).isEqualTo(models2.get(i).getProvider());
      }
    }
  }

  @Nested
  @DisplayName("Credential Validation Tests")
  class CredentialValidationTests {

    @Test
    @DisplayName("Should handle credential validation with invalid credentials")
    void shouldHandleCredentialValidationWithInvalidCredentials() {
      // Test with obviously invalid credentials should return false
      boolean result =
          awsRegionModelService.validateCredentials(INVALID_ACCESS_KEY, INVALID_SECRET_KEY);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle credential validation with specific region")
    void shouldHandleCredentialValidationWithSpecificRegion() {
      // Test with invalid credentials and specific region should return false
      boolean result =
          awsRegionModelService.validateCredentials(
              INVALID_ACCESS_KEY, INVALID_SECRET_KEY, "us-east-2");
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null credentials in validation")
    void shouldHandleNullCredentialsInValidation() {
      boolean result = awsRegionModelService.validateCredentials(null, null);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle empty credentials in validation")
    void shouldHandleEmptyCredentialsInValidation() {
      boolean result = awsRegionModelService.validateCredentials("", "");
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle mixed null/empty credentials")
    void shouldHandleMixedNullEmptyCredentials() {
      assertThat(awsRegionModelService.validateCredentials(null, "")).isFalse();
      assertThat(awsRegionModelService.validateCredentials("", null)).isFalse();
      assertThat(awsRegionModelService.validateCredentials(VALID_ACCESS_KEY, null)).isFalse();
      assertThat(awsRegionModelService.validateCredentials(null, VALID_SECRET_KEY)).isFalse();
    }
  }

  @Nested
  @DisplayName("Model Access Validation Tests")
  class ModelAccessValidationTests {

    @Test
    @DisplayName("Should handle Claude model validation with invalid credentials")
    void shouldHandleClaudeModelValidationWithInvalidCredentials() {
      // Test model validation with invalid credentials should return false
      boolean result =
          awsRegionModelService.validateModelAccess(
              INVALID_ACCESS_KEY, INVALID_SECRET_KEY, "us-east-1", TEST_CLAUDE_MODEL_ID);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle Titan model validation with invalid credentials")
    void shouldHandleTitanModelValidationWithInvalidCredentials() {
      // Test Titan model validation with invalid credentials should return false
      boolean result =
          awsRegionModelService.validateModelAccess(
              INVALID_ACCESS_KEY, INVALID_SECRET_KEY, "us-east-1", TITAN_EMBEDDING_MODEL_ID);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null credentials in model validation")
    void shouldHandleNullCredentialsInModelValidation() {
      boolean result =
          awsRegionModelService.validateModelAccess(null, null, "us-east-1", TEST_CLAUDE_MODEL_ID);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null model ID in validation")
    void shouldHandleNullModelIdInValidation() {
      boolean result =
          awsRegionModelService.validateModelAccess(
              VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1", null);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null region in model validation")
    void shouldHandleNullRegionInModelValidation() {
      boolean result =
          awsRegionModelService.validateModelAccess(
              VALID_ACCESS_KEY, VALID_SECRET_KEY, null, TEST_CLAUDE_MODEL_ID);
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should validate different Claude model variants")
    void shouldValidateDifferentClaudeModelVariants() {
      // Test different Claude model ID variants with invalid credentials (all should fail)
      String[] claudeVariants = {
        "claude-sonnet-4-latest", "anthropic.claude-sonnet-4-20250514", "claude-sonnet-4v2"
      };

      for (String modelId : claudeVariants) {
        boolean result =
            awsRegionModelService.validateModelAccess(
                INVALID_ACCESS_KEY, INVALID_SECRET_KEY, "us-east-1", modelId);
        assertThat(result).isFalse();
      }
    }

    @Test
    @DisplayName("Should handle invalid model ID")
    void shouldHandleInvalidModelId() {
      boolean result =
          awsRegionModelService.validateModelAccess(
              INVALID_ACCESS_KEY, INVALID_SECRET_KEY, "us-east-1", "nonexistent-model");
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("DTO Tests")
  class DtoTests {

    @Test
    @DisplayName("Should create RegionInfo with builder pattern")
    void shouldCreateRegionInfoWithBuilderPattern() {
      AwsRegionModelService.RegionInfo regionInfo =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-1")
              .displayName("us-east-1 - US East (N. Virginia)")
              .build();

      assertThat(regionInfo).isNotNull();
      assertThat(regionInfo.getRegionId()).isEqualTo("us-east-1");
      assertThat(regionInfo.getDisplayName()).isEqualTo("us-east-1 - US East (N. Virginia)");
    }

    @Test
    @DisplayName("Should create ModelInfo with builder pattern")
    void shouldCreateModelInfoWithBuilderPattern() {
      AwsRegionModelService.ModelInfo modelInfo =
          AwsRegionModelService.ModelInfo.builder()
              .modelId(TEST_CLAUDE_MODEL_ID)
              .modelName("Claude Sonnet 4.0")
              .provider("Anthropic")
              .inputModalities(List.of("TEXT"))
              .outputModalities(List.of("TEXT"))
              .requiresInferenceProfile(true)
              .build();

      assertThat(modelInfo).isNotNull();
      assertThat(modelInfo.getModelId()).isEqualTo(TEST_CLAUDE_MODEL_ID);
      assertThat(modelInfo.getModelName()).isEqualTo("Claude Sonnet 4.0");
      assertThat(modelInfo.getProvider()).isEqualTo("Anthropic");
      assertThat(modelInfo.getInputModalities()).containsExactly("TEXT");
      assertThat(modelInfo.getOutputModalities()).containsExactly("TEXT");
      assertThat(modelInfo.isRequiresInferenceProfile()).isTrue();
    }

    @Test
    @DisplayName("Should handle RegionInfo with all regions")
    void shouldHandleRegionInfoWithAllRegions() {
      AwsRegionModelService.RegionInfo usEast1 =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-1")
              .displayName("us-east-1 - US East (N. Virginia)")
              .build();
      AwsRegionModelService.RegionInfo usEast2 =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-2")
              .displayName("us-east-2 - US East (Ohio)")
              .build();
      assertThat(usEast1.getRegionId()).isEqualTo("us-east-1");
      assertThat(usEast2.getRegionId()).isEqualTo("us-east-2");

      assertThat(usEast1.getDisplayName()).contains("Virginia");
      assertThat(usEast2.getDisplayName()).contains("Ohio");
    }

    @Test
    @DisplayName("Should create ModelInfo with different modalities")
    void shouldCreateModelInfoWithDifferentModalities() {
      AwsRegionModelService.ModelInfo multiModalModel =
          AwsRegionModelService.ModelInfo.builder()
              .modelId("test-multimodal")
              .modelName("Test Multimodal Model")
              .provider("Test Provider")
              .inputModalities(List.of("TEXT", "IMAGE", "AUDIO"))
              .outputModalities(List.of("TEXT", "IMAGE"))
              .requiresInferenceProfile(false)
              .build();

      assertThat(multiModalModel.getInputModalities()).containsExactly("TEXT", "IMAGE", "AUDIO");
      assertThat(multiModalModel.getOutputModalities()).containsExactly("TEXT", "IMAGE");
      assertThat(multiModalModel.isRequiresInferenceProfile()).isFalse();
    }

    @Test
    @DisplayName("Should support RegionInfo equality and hashCode")
    void shouldSupportRegionInfoEqualityAndHashCode() {
      AwsRegionModelService.RegionInfo region1 =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-1")
              .displayName("us-east-1 - US East (N. Virginia)")
              .build();

      AwsRegionModelService.RegionInfo region2 =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-1")
              .displayName("us-east-1 - US East (N. Virginia)")
              .build();

      AwsRegionModelService.RegionInfo region3 =
          AwsRegionModelService.RegionInfo.builder()
              .regionId("us-east-2")
              .displayName("us-east-2 - US East (Ohio)")
              .build();

      assertThat(region1).isEqualTo(region2);
      assertThat(region1).isNotEqualTo(region3);
      assertThat(region1.hashCode()).isEqualTo(region2.hashCode());
      assertThat(region1.toString()).isNotNull().contains("us-east-1");
    }

    @Test
    @DisplayName("Should support ModelInfo equality and hashCode")
    void shouldSupportModelInfoEqualityAndHashCode() {
      AwsRegionModelService.ModelInfo model1 =
          AwsRegionModelService.ModelInfo.builder()
              .modelId(TEST_CLAUDE_MODEL_ID)
              .modelName("Claude Sonnet 4.0")
              .provider("Anthropic")
              .inputModalities(List.of("TEXT"))
              .outputModalities(List.of("TEXT"))
              .requiresInferenceProfile(true)
              .build();

      AwsRegionModelService.ModelInfo model2 =
          AwsRegionModelService.ModelInfo.builder()
              .modelId(TEST_CLAUDE_MODEL_ID)
              .modelName("Claude Sonnet 4.0")
              .provider("Anthropic")
              .inputModalities(List.of("TEXT"))
              .outputModalities(List.of("TEXT"))
              .requiresInferenceProfile(true)
              .build();

      AwsRegionModelService.ModelInfo model3 =
          AwsRegionModelService.ModelInfo.builder()
              .modelId("different-model")
              .modelName("Different Model")
              .provider("Different Provider")
              .inputModalities(List.of("TEXT"))
              .outputModalities(List.of("TEXT"))
              .requiresInferenceProfile(false)
              .build();

      assertThat(model1).isEqualTo(model2);
      assertThat(model1).isNotEqualTo(model3);
      assertThat(model1.hashCode()).isEqualTo(model2.hashCode());
      assertThat(model1.toString()).isNotNull().contains("Claude");
    }

    @Test
    @DisplayName("Should handle ModelInfo with null/empty values")
    void shouldHandleModelInfoWithNullEmptyValues() {
      AwsRegionModelService.ModelInfo modelWithNulls =
          AwsRegionModelService.ModelInfo.builder()
              .modelId(null)
              .modelName("")
              .provider(null)
              .inputModalities(null)
              .outputModalities(List.of())
              .requiresInferenceProfile(false)
              .build();

      assertThat(modelWithNulls.getModelId()).isNull();
      assertThat(modelWithNulls.getModelName()).isEmpty();
      assertThat(modelWithNulls.getProvider()).isNull();
      assertThat(modelWithNulls.getInputModalities()).isNull();
      assertThat(modelWithNulls.getOutputModalities()).isEmpty();
    }

    @Test
    @DisplayName("Should handle RegionInfo with null/empty values")
    void shouldHandleRegionInfoWithNullEmptyValues() {
      AwsRegionModelService.RegionInfo regionWithNulls =
          AwsRegionModelService.RegionInfo.builder().regionId(null).displayName("").build();

      assertThat(regionWithNulls.getRegionId()).isNull();
      assertThat(regionWithNulls.getDisplayName()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle service with null claude model ID")
    void shouldHandleServiceWithNullClaudeModelId() {
      ReflectionTestUtils.setField(awsRegionModelService, "claudeModelId", null);

      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");

      assertThat(models).hasSize(1);
      assertThat(models.get(0).getModelId()).isNull();
    }

    @Test
    @DisplayName("Should handle service with empty claude model ID")
    void shouldHandleServiceWithEmptyClaudeModelId() {
      ReflectionTestUtils.setField(awsRegionModelService, "claudeModelId", "");

      List<AwsRegionModelService.ModelInfo> models =
          awsRegionModelService.getAvailableModels(VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");

      assertThat(models).hasSize(1);
      assertThat(models.get(0).getModelId()).isEmpty();
    }

    @Test
    @DisplayName("Should handle extreme parameter values")
    void shouldHandleExtremeParameterValues() {
      String veryLongString = "a".repeat(10000);

      // Should not throw exceptions
      assertThatCode(
              () -> {
                awsRegionModelService.getAvailableBedrockRegions(veryLongString, veryLongString);
                awsRegionModelService.getAvailableModels(
                    veryLongString, veryLongString, veryLongString);
                awsRegionModelService.validateCredentials(
                    veryLongString, veryLongString, veryLongString);
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle special characters in parameters")
    void shouldHandleSpecialCharactersInParameters() {
      String specialChars = "!@#$%^&*()_+-=[]{}|;':,.<>?";

      // Should not throw exceptions
      assertThatCode(
              () -> {
                awsRegionModelService.getAvailableBedrockRegions(specialChars, specialChars);
                awsRegionModelService.getAvailableModels(specialChars, specialChars, specialChars);
                awsRegionModelService.validateCredentials(specialChars, specialChars, specialChars);
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() {
      // Simulate concurrent access
      Runnable task =
          () -> {
            awsRegionModelService.getAvailableBedrockRegions(VALID_ACCESS_KEY, VALID_SECRET_KEY);
            awsRegionModelService.getAvailableModels(
                VALID_ACCESS_KEY, VALID_SECRET_KEY, "us-east-1");
          };

      // Should not throw exceptions when called concurrently
      assertThatCode(
              () -> {
                Thread t1 = new Thread(task);
                Thread t2 = new Thread(task);
                Thread t3 = new Thread(task);

                t1.start();
                t2.start();
                t3.start();

                t1.join();
                t2.join();
                t3.join();
              })
          .doesNotThrowAnyException();
    }
  }
}

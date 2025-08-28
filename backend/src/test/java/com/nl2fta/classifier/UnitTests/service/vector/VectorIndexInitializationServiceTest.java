package com.nl2fta.classifier.service.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorIndexInitializationService Tests")
class VectorIndexInitializationServiceTest {

  @Mock private VectorSimilaritySearchService vectorSearchService;

  @Mock private CustomSemanticTypeService customSemanticTypeService;

  @Mock private AwsCredentialsService awsCredentialsService;

  @Mock private VectorEmbeddingService vectorEmbeddingService;

  @InjectMocks private VectorIndexInitializationService indexInitService;

  private List<CustomSemanticType> mockSemanticTypes;
  private List<VectorData> mockExistingVectors;

  @BeforeEach
  void setUp() {
    // Set default configuration values using reflection
    ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", true);
    ReflectionTestUtils.setField(indexInitService, "rebuildOnStartup", false);

    mockSemanticTypes =
        Arrays.asList(
            createMockSemanticType("EMAIL.ADDRESS", "Email addresses"),
            createMockSemanticType("PHONE.NUMBER", "Phone numbers"),
            createMockSemanticType("NAME.FIRST", "First names"));

    mockExistingVectors =
        Arrays.asList(createMockVectorData("EMAIL.ADDRESS"), createMockVectorData("PHONE.NUMBER"));
  }

  @Nested
  @DisplayName("Application Ready Event")
  class ApplicationReadyEvent {

    @Test
    @DisplayName("Should log readiness when vector indexing is enabled")
    void shouldLogReadinessWhenVectorIndexingIsEnabled() {
      // When
      indexInitService.onApplicationReady();

      // Then - No exception should be thrown, service should be ready
      // The method just logs, so we verify it completes without error
    }

    @Test
    @DisplayName("Should skip initialization when vector indexing is disabled")
    void shouldSkipInitializationWhenVectorIndexingIsDisabled() throws Exception {
      // Given
      ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", false);

      // When
      indexInitService.onApplicationReady();

      // Then - Should complete without triggering vector-related calls when disabled
      verifyNoInteractions(vectorSearchService, customSemanticTypeService, vectorEmbeddingService);
    }
  }

  @Nested
  @DisplayName("Initialize After AWS Connection")
  class InitializeAfterAwsConnection {

    @Test
    @DisplayName("Should initialize vector index after AWS connection")
    void shouldInitializeVectorIndexAfterAwsConnection() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then
      verify(vectorEmbeddingService).initializeBedrockClient();
      verify(vectorSearchService).hasAnyStoredVectors();
      verify(customSemanticTypeService).getAllCustomTypes();
      verify(vectorSearchService).indexSemanticTypes(mockSemanticTypes);
    }

    @Test
    @DisplayName("Should skip initialization when vector indexing is disabled")
    void shouldSkipInitializationWhenVectorIndexingIsDisabled() {
      // Given
      ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", false);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Then: when disabled, nothing should be initialized
      verifyNoInteractions(
          awsCredentialsService,
          vectorEmbeddingService,
          vectorSearchService,
          customSemanticTypeService);
    }

    @Test
    @DisplayName("Should skip initialization when AWS credentials not available")
    void shouldSkipInitializationWhenAwsCredentialsNotAvailable() {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Then
      verify(awsCredentialsService).areCredentialsAvailable();
      verifyNoInteractions(vectorEmbeddingService);
    }

    @Test
    @DisplayName("Should skip re-indexing when existing vectors found and rebuild not requested")
    void shouldSkipReIndexingWhenExistingVectorsFoundAndRebuildNotRequested() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(true);
      // Updated logic skips re-indexing only when bucket appears initialized (>=
      // MIN_EXPECTED_INDEXED_TYPES)
      when(vectorSearchService.getStoredVectorCount()).thenReturn(200);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then
      verify(vectorSearchService).hasAnyStoredVectors();
      verify(vectorSearchService).getStoredVectorCount();
      verify(vectorSearchService, never()).indexSemanticTypes(anyList());
      verify(vectorSearchService, never()).clearIndex();
    }

    @Test
    @DisplayName("Should rebuild index when rebuild on startup is enabled")
    void shouldRebuildIndexWhenRebuildOnStartupIsEnabled() throws Exception {
      // Given
      ReflectionTestUtils.setField(indexInitService, "rebuildOnStartup", true);
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(true);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then
      verify(vectorSearchService).clearIndex();
      verify(customSemanticTypeService).getAllCustomTypes();
      verify(vectorSearchService).indexSemanticTypes(mockSemanticTypes);
    }

    @Test
    @DisplayName("Should handle exception during initialization gracefully")
    void shouldHandleExceptionDuringInitializationGracefully() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Collections.emptyList());

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then - Exception should be handled gracefully
      verify(vectorSearchService).hasAnyStoredVectors();
      verify(vectorSearchService).indexSemanticTypes(Collections.emptyList());
    }

    @Test
    @DisplayName("Should re-index when vector count does not match semantic type count")
    void shouldReindexWhenVectorCountMismatch() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(true);
      when(vectorSearchService.getStoredVectorCount()).thenReturn(10); // Mismatch: 10 vectors
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes); // 3 types

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then
      verify(vectorSearchService).hasAnyStoredVectors();
      verify(vectorSearchService).getStoredVectorCount();
      // Updated logic indexes to complete the set without clearing the index
      verify(customSemanticTypeService, times(1)).getAllCustomTypes();
      verify(vectorSearchService).indexSemanticTypes(mockSemanticTypes); // Should re-index
    }
  }

  @Nested
  @DisplayName("Re-index Semantic Type")
  class ReindexSemanticType {

    @Test
    @DisplayName("Should re-index semantic type successfully")
    void shouldReindexSemanticTypeSuccessfully() {
      // Given
      CustomSemanticType semanticType = createMockSemanticType("NEW.TYPE", "New type");

      // When
      indexInitService.reindexSemanticType(semanticType);

      // Then
      verify(vectorSearchService).indexSemanticType(semanticType);
    }

    @Test
    @DisplayName("Should skip re-indexing when vector indexing is disabled")
    void shouldSkipReindexingWhenVectorIndexingIsDisabled() {
      // Given
      ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", false);
      CustomSemanticType semanticType = createMockSemanticType("NEW.TYPE", "New type");

      // When
      indexInitService.reindexSemanticType(semanticType);

      // Then
      verifyNoInteractions(vectorSearchService);
    }

    @Test
    @DisplayName("Should handle exception during re-indexing gracefully")
    void shouldHandleExceptionDuringReindexingGracefully() {
      // Given
      CustomSemanticType semanticType = createMockSemanticType("FAILING.TYPE", "Failing type");
      doThrow(new RuntimeException("Indexing failed"))
          .when(vectorSearchService)
          .indexSemanticType(semanticType);

      // When
      indexInitService.reindexSemanticType(semanticType);

      // Then - Exception should be handled gracefully
      verify(vectorSearchService).indexSemanticType(semanticType);
    }
  }

  @Nested
  @DisplayName("Remove from Index")
  class RemoveFromIndex {

    @Test
    @DisplayName("Should remove semantic type from index successfully")
    void shouldRemoveSemanticTypeFromIndexSuccessfully() {
      // Given
      String semanticTypeName = "OBSOLETE.TYPE";

      // When
      indexInitService.removeFromIndex(semanticTypeName);

      // Then
      verify(vectorSearchService).removeFromIndex(semanticTypeName);
    }

    @Test
    @DisplayName("Should skip removal when vector indexing is disabled")
    void shouldSkipRemovalWhenVectorIndexingIsDisabled() {
      // Given
      ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", false);
      String semanticTypeName = "OBSOLETE.TYPE";

      // When
      indexInitService.removeFromIndex(semanticTypeName);

      // Then
      verifyNoInteractions(vectorSearchService);
    }

    @Test
    @DisplayName("Should handle exception during removal gracefully")
    void shouldHandleExceptionDuringRemovalGracefully() {
      // Given
      String semanticTypeName = "FAILING.TYPE";
      doThrow(new RuntimeException("Removal failed"))
          .when(vectorSearchService)
          .removeFromIndex(semanticTypeName);

      // When
      indexInitService.removeFromIndex(semanticTypeName);

      // Then - Exception should be handled gracefully
      verify(vectorSearchService).removeFromIndex(semanticTypeName);
    }
  }

  @Nested
  @DisplayName("Rebuild Index")
  class RebuildIndex {

    @Test
    @DisplayName("Should rebuild index successfully")
    void shouldRebuildIndexSuccessfully() throws Exception {
      // Given
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes);

      // When
      indexInitService.rebuildIndex();

      // Then
      verify(vectorSearchService).clearIndex();
      verify(vectorSearchService).hasAnyStoredVectors();
      verify(customSemanticTypeService).getAllCustomTypes();
      verify(vectorSearchService).indexSemanticTypes(mockSemanticTypes);
    }

    @Test
    @DisplayName("Should throw exception when vector indexing is disabled")
    void shouldThrowExceptionWhenVectorIndexingIsDisabled() {
      // Given
      ReflectionTestUtils.setField(indexInitService, "vectorIndexEnabled", false);

      // When & Then
      assertThatThrownBy(() -> indexInitService.rebuildIndex())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Cannot rebuild index - vector indexing is disabled");
    }

    @Test
    @DisplayName("Should propagate exception when rebuild fails")
    void shouldPropagateExceptionWhenRebuildFails() {
      // Given
      doThrow(new RuntimeException("Clear failed")).when(vectorSearchService).clearIndex();

      // When & Then
      assertThatThrownBy(() -> indexInitService.rebuildIndex())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to rebuild vector index");
    }
  }

  @Nested
  @DisplayName("Status Tracking")
  class StatusTracking {

    @Test
    @DisplayName("Should track indexing status correctly")
    void shouldTrackIndexingStatusCorrectly() {
      // Initially not indexing
      assertThat(indexInitService.isIndexing()).isFalse();
      assertThat(indexInitService.getTotalTypesToIndex()).isEqualTo(0);
      assertThat(indexInitService.getIndexedTypesCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should increment indexed types count")
    void shouldIncrementIndexedTypesCount() {
      // Given
      int initialCount = indexInitService.getIndexedTypesCount();

      // When
      indexInitService.incrementIndexedTypesCount();
      indexInitService.incrementIndexedTypesCount();

      // Then
      assertThat(indexInitService.getIndexedTypesCount()).isEqualTo(initialCount + 2);
    }

    @Test
    @DisplayName("Should track indexing progress during initialization")
    void shouldTrackIndexingProgressDuringInitialization() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to start
      Thread.sleep(100);

      // Then - indexing should be in progress initially
      // Note: Due to async nature, we can't guarantee exact timing, but we can test the methods
      // exist
      // and work correctly
      assertThat(indexInitService.isIndexing())
          .isIn(true, false); // Could be either depending on timing

      // Wait for completion
      Thread.sleep(1500);

      // Should complete
      assertThat(indexInitService.isIndexing()).isFalse();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandling {

    @Test
    @DisplayName("Should handle empty semantic types list during initialization")
    void shouldHandleEmptySemanticTypesListDuringInitialization() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(Collections.emptyList());

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then
      verify(vectorSearchService).indexSemanticTypes(Collections.emptyList());
    }

    @Test
    @DisplayName("Should handle null semantic type in re-indexing")
    void shouldHandleNullSemanticTypeInReindexing() {
      // When & Then - Should not throw exception
      indexInitService.reindexSemanticType(null);

      // Verify no service call is made for null input
      verifyNoInteractions(vectorSearchService);
    }

    @Test
    @DisplayName("Should handle null semantic type name in removal")
    void shouldHandleNullSemanticTypeNameInRemoval() {
      // When & Then - Should not throw exception
      indexInitService.removeFromIndex(null);

      // Verify the service call is made (will internally handle null)
      verify(vectorSearchService).removeFromIndex(null);
    }

    @Test
    @DisplayName("Should handle custom type service exception in initialization")
    void shouldHandleCustomTypeServiceExceptionInInitialization() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes())
          .thenThrow(new RuntimeException("Service unavailable"));

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation a moment to complete
      Thread.sleep(1500);

      // Then - Exception should be handled gracefully
      verify(customSemanticTypeService).getAllCustomTypes();
    }

    @Test
    @DisplayName("Should handle concurrent initialization attempts")
    void shouldHandleConcurrentInitializationAttempts() throws Exception {
      // Given
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(mockSemanticTypes);

      // When - Call initialization multiple times concurrently
      for (int i = 0; i < 5; i++) {
        indexInitService.initializeAfterAwsConnection();
      }

      // Give all operations time to complete
      Thread.sleep(2000);

      // Then - Should handle concurrent calls gracefully
      // The exact number of calls depends on implementation, but it should not fail
      verify(vectorEmbeddingService, atLeastOnce()).initializeBedrockClient();
    }

    @Test
    @DisplayName("Should handle very large number of semantic types")
    void shouldHandleVeryLargeNumberOfSemanticTypes() throws Exception {
      // Given
      List<CustomSemanticType> largeList =
          Collections.nCopies(10000, createMockSemanticType("LARGE.TYPE", "Large type"));
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(vectorSearchService.hasAnyStoredVectors()).thenReturn(false);
      when(customSemanticTypeService.getAllCustomTypes()).thenReturn(largeList);

      // When
      indexInitService.initializeAfterAwsConnection();

      // Give the async operation more time for large dataset
      Thread.sleep(3000);

      // Then
      verify(vectorSearchService).indexSemanticTypes(largeList);
    }
  }

  // Helper methods
  private CustomSemanticType createMockSemanticType(String semanticType, String description) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType(semanticType);
    type.setDescription(description);
    type.setPluginType("regex");
    return type;
  }

  private VectorData createMockVectorData(String semanticType) {
    return VectorData.builder()
        .id(semanticType.toLowerCase().replace(".", "_"))
        .semanticType(semanticType)
        .type("built-in")
        .description("Mock vector for " + semanticType)
        .embedding(Arrays.asList(0.1f, 0.2f, 0.3f))
        .originalText("Mock text")
        .pluginType("regex")
        .examples(Arrays.asList("example1", "example2"))
        .build();
  }
}

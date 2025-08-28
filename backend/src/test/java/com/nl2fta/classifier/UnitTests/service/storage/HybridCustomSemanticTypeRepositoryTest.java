package com.nl2fta.classifier.UnitTests.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.storage.FileBasedCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.storage.S3CustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.S3VectorStorageService;

@ExtendWith(MockitoExtension.class)
class HybridCustomSemanticTypeRepositoryTest {

  @Mock private FileBasedCustomSemanticTypeRepository fileBasedRepository;

  @Mock private AwsCredentialsService awsCredentialsService;

  @Mock private ObjectMapper objectMapper;

  @Mock private S3VectorStorageService s3VectorStorageService;

  @Mock private S3CustomSemanticTypeRepository s3Repository;

  @InjectMocks private HybridCustomSemanticTypeRepository hybridRepository;

  private CustomSemanticType sampleType;

  @BeforeEach
  void setUp() {
    sampleType = createSampleCustomType("TEST.TYPE", "Test description");
  }

  @Test
  void shouldInitializeWithFileBasedStorageByDefault() {
    hybridRepository.init();

    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
    assertThat(hybridRepository.isUsingS3Storage()).isFalse();
  }

  @Test
  void shouldSwitchToS3StorageWhenCredentialsAvailable() {
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);

    hybridRepository.initializeS3Repository();

    verify(s3Repository).initializeWithCredentials();
    verify(s3VectorStorageService).initializeS3Client();
    assertThat(hybridRepository.isUsingS3Storage()).isTrue();
    assertThat(hybridRepository.isUsingFileStorage()).isFalse();
  }

  @Test
  void shouldFallbackToFileStorageWhenS3InitializationFails() {
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    doThrow(new RuntimeException("S3 init failed")).when(s3Repository).initializeWithCredentials();

    hybridRepository.initializeS3Repository();

    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
    assertThat(hybridRepository.isUsingS3Storage()).isFalse();
  }

  @Test
  void shouldDisconnectS3AndClearLocalTypes() {
    // First initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    // Mock file repository having some types
    CustomSemanticType type1 = createSampleCustomType("TYPE1", "Type 1");
    CustomSemanticType type2 = createSampleCustomType("TYPE2", "Type 2");
    when(fileBasedRepository.findAll()).thenReturn(List.of(type1, type2));

    // Disconnect S3
    hybridRepository.disconnectS3();

    verify(s3Repository).cleanup();
    verify(s3VectorStorageService).disconnectS3();
    verify(fileBasedRepository).deleteBySemanticType("TYPE1");
    verify(fileBasedRepository).deleteBySemanticType("TYPE2");
    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
  }

  @Test
  void shouldSaveToFileWhenAwsNotConnected() {
    when(fileBasedRepository.save(any(CustomSemanticType.class))).thenReturn(sampleType);

    CustomSemanticType saved = hybridRepository.save(sampleType);

    assertThat(saved).isEqualTo(sampleType);
    verify(fileBasedRepository).save(sampleType);
    verify(s3Repository, never()).save(any());
  }

  @Test
  void shouldSaveToS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    when(s3Repository.save(any(CustomSemanticType.class))).thenReturn(sampleType);

    CustomSemanticType saved = hybridRepository.save(sampleType);

    assertThat(saved).isEqualTo(sampleType);
    verify(s3Repository).save(sampleType);
    verify(fileBasedRepository, never()).save(any());
  }

  @Test
  void shouldFindFromFileWhenAwsNotConnected() {
    when(fileBasedRepository.findBySemanticType("TEST.TYPE")).thenReturn(Optional.of(sampleType));

    Optional<CustomSemanticType> result = hybridRepository.findBySemanticType("TEST.TYPE");

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(sampleType);
    verify(fileBasedRepository).findBySemanticType("TEST.TYPE");
    verify(s3Repository, never()).findBySemanticType(anyString());
  }

  @Test
  void shouldFindFromS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    when(s3Repository.findBySemanticType("TEST.TYPE")).thenReturn(Optional.of(sampleType));

    Optional<CustomSemanticType> result = hybridRepository.findBySemanticType("TEST.TYPE");

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(sampleType);
    verify(s3Repository).findBySemanticType("TEST.TYPE");
    verify(fileBasedRepository, never()).findBySemanticType(anyString());
  }

  @Test
  void shouldCheckExistenceInFileWhenAwsNotConnected() {
    when(fileBasedRepository.existsBySemanticType("TEST.TYPE")).thenReturn(false);

    boolean exists = hybridRepository.existsBySemanticType("TEST.TYPE");

    assertThat(exists).isFalse();
    verify(fileBasedRepository).existsBySemanticType("TEST.TYPE");
    verify(s3Repository, never()).existsBySemanticType(anyString());
  }

  @Test
  void shouldCheckExistenceInS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    when(s3Repository.existsBySemanticType("TEST.TYPE")).thenReturn(true);

    boolean exists = hybridRepository.existsBySemanticType("TEST.TYPE");

    assertThat(exists).isTrue();
    verify(s3Repository).existsBySemanticType("TEST.TYPE");
    verify(fileBasedRepository, never()).existsBySemanticType(anyString());
  }

  @Test
  void shouldDeleteUsingFileWhenAwsNotConnected() {
    when(fileBasedRepository.deleteBySemanticType("TEST.TYPE")).thenReturn(false);

    boolean deleted = hybridRepository.deleteBySemanticType("TEST.TYPE");

    assertThat(deleted).isFalse();
    verify(fileBasedRepository).deleteBySemanticType("TEST.TYPE");
    verify(s3Repository, never()).deleteBySemanticType(anyString());
  }

  @Test
  void shouldDeleteFromS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    when(s3Repository.deleteBySemanticType("TEST.TYPE")).thenReturn(true);

    boolean deleted = hybridRepository.deleteBySemanticType("TEST.TYPE");

    assertThat(deleted).isTrue();
    verify(s3Repository).deleteBySemanticType("TEST.TYPE");
    verify(fileBasedRepository, never()).deleteBySemanticType(anyString());
  }

  @Test
  void shouldUpdateUsingFileRepoWhenAwsNotConnected() {
    CustomSemanticType updated = createSampleCustomType("OLD.TYPE", "Updated");
    when(fileBasedRepository.update("OLD.TYPE", sampleType)).thenReturn(updated);

    CustomSemanticType result = hybridRepository.update("OLD.TYPE", sampleType);

    assertThat(result).isEqualTo(updated);
    verify(fileBasedRepository).update("OLD.TYPE", sampleType);
    verify(s3Repository, never()).update(anyString(), any());
  }

  @Test
  void shouldUpdateInS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    CustomSemanticType updatedType = createSampleCustomType("NEW.TYPE", "Updated description");
    when(s3Repository.update("OLD.TYPE", updatedType)).thenReturn(updatedType);

    CustomSemanticType result = hybridRepository.update("OLD.TYPE", updatedType);

    assertThat(result).isEqualTo(updatedType);
    verify(s3Repository).update("OLD.TYPE", updatedType);
    verify(fileBasedRepository, never()).update(anyString(), any());
  }

  @Test
  void shouldFindAllFromFileWhenAwsNotConnected() {
    when(fileBasedRepository.findAll()).thenReturn(List.of());

    List<CustomSemanticType> all = hybridRepository.findAll();

    assertThat(all).isEmpty();
    verify(fileBasedRepository).findAll();
    verify(s3Repository, never()).findAll();
  }

  @Test
  void shouldFindAllFromS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    List<CustomSemanticType> expectedTypes =
        List.of(
            createSampleCustomType("TYPE1", "Type 1"), createSampleCustomType("TYPE2", "Type 2"));
    when(s3Repository.findAll()).thenReturn(expectedTypes);

    List<CustomSemanticType> all = hybridRepository.findAll();

    assertThat(all).hasSize(2);
    assertThat(all).isEqualTo(expectedTypes);
    // initializeS3Repository() triggers a probe; allow one or more invocations
    verify(s3Repository, atLeast(1)).findAll();
    verify(fileBasedRepository, never()).findAll();
  }

  @Test
  void shouldReloadFromFileWhenAwsNotConnected() {
    hybridRepository.reload();

    verify(fileBasedRepository).reload();
    verify(s3Repository, never()).reload();
  }

  @Test
  void shouldReloadFromS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    hybridRepository.reload();

    verify(s3Repository).reload();
    verify(fileBasedRepository, never()).reload();
  }

  @Test
  void shouldGetInternalMapFromFileWhenAwsNotConnected() {
    Map<String, CustomSemanticType> expectedMap = Map.of("TEST.TYPE", sampleType);
    when(fileBasedRepository.getInternalMap()).thenReturn(expectedMap);

    Map<String, CustomSemanticType> map = hybridRepository.getInternalMap();

    assertThat(map).isEqualTo(expectedMap);
    verify(fileBasedRepository).getInternalMap();
    verify(s3Repository, never()).getInternalMap();
  }

  @Test
  void shouldGetInternalMapFromS3WhenConnected() {
    // Initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    Map<String, CustomSemanticType> expectedMap =
        Map.of(
            "TYPE1", createSampleCustomType("TYPE1", "Type 1"),
            "TYPE2", createSampleCustomType("TYPE2", "Type 2"));
    when(s3Repository.getInternalMap()).thenReturn(expectedMap);

    Map<String, CustomSemanticType> map = hybridRepository.getInternalMap();

    assertThat(map).isEqualTo(expectedMap);
    verify(s3Repository).getInternalMap();
    verify(fileBasedRepository, never()).getInternalMap();
  }

  @Test
  void shouldProvideCorrectStorageStatus() {
    // Initial state - no AWS credentials
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);
    assertThat(hybridRepository.getStorageStatus()).isEqualTo("Awaiting AWS credentials");

    // AWS credentials available but S3 not initialized
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    assertThat(hybridRepository.getStorageStatus())
        .isEqualTo("AWS credentials available but S3 repository not yet initialized");

    // S3 initialized
    hybridRepository.initializeS3Repository();
    assertThat(hybridRepository.getStorageStatus()).isEqualTo("Using S3 storage");
  }

  @Test
  void shouldHandleS3InitializationWithoutCredentials() {
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);

    hybridRepository.initializeS3Repository();

    verify(s3Repository, never()).initializeWithCredentials();
    verify(s3VectorStorageService, never()).initializeS3Client();
    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
  }

  @Test
  void shouldHandleExceptionDuringS3Disconnect() {
    // First initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    // Make cleanup throw exception
    doThrow(new RuntimeException("Cleanup failed")).when(s3Repository).cleanup();

    // Should not throw exception
    hybridRepository.disconnectS3();

    // Should still complete disconnect process
    verify(s3Repository).cleanup();
    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
  }

  @Test
  void shouldHandleExceptionWhenClearingLocalTypes() {
    // First initialize S3
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
    hybridRepository.initializeS3Repository();

    // Mock file repository throwing exception when listing types
    when(fileBasedRepository.findAll()).thenThrow(new RuntimeException("List failed"));

    // Should not throw exception
    hybridRepository.disconnectS3();

    // Should still complete disconnect
    verify(s3Repository).cleanup();
    assertThat(hybridRepository.isUsingFileStorage()).isTrue();
  }

  private CustomSemanticType createSampleCustomType(String semanticType, String description) {
    CustomSemanticType customType = new CustomSemanticType();
    customType.setSemanticType(semanticType);
    customType.setDescription(description);
    customType.setPluginType("REGEX");
    customType.setPriority(2000);
    return customType;
  }
}

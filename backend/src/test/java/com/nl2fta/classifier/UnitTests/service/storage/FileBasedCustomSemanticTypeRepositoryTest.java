package com.nl2fta.classifier.UnitTests.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.config.ApplicationProperties;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.storage.FileBasedCustomSemanticTypeRepository;

class FileBasedCustomSemanticTypeRepositoryTest {

  @Mock private ApplicationProperties applicationProperties;

  private ObjectMapper objectMapper;
  private FileBasedCustomSemanticTypeRepository repository;

  @TempDir Path tempDir;

  private Path customTypesFile;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    customTypesFile = tempDir.resolve("custom-types.json");

    when(applicationProperties.getCustomTypesFile()).thenReturn(customTypesFile.toString());

    repository = new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    repository.init();
  }

  @Test
  void shouldSaveAndRetrieveCustomSemanticType() {
    CustomSemanticType customType = createSampleCustomType("USER.ID", "User identifier");

    CustomSemanticType saved = repository.save(customType);
    Optional<CustomSemanticType> found = repository.findBySemanticType("USER.ID");

    assertThat(saved).isEqualTo(customType);
    assertThat(found).isPresent();
    assertThat(found.get().getSemanticType()).isEqualTo("USER.ID");
    assertThat(found.get().getDescription()).isEqualTo("User identifier");
  }

  @Test
  void shouldPersistCustomTypesToFileAfterSave() throws IOException {
    CustomSemanticType customType = createSampleCustomType("ORDER.ID", "Order number");

    repository.save(customType);

    assertThat(Files.exists(customTypesFile)).isTrue();
    String fileContent = Files.readString(customTypesFile);
    assertThat(fileContent).contains("ORDER.ID");
    assertThat(fileContent).contains("Order number");
  }

  @Test
  void shouldUpdateExistingCustomType() {
    CustomSemanticType original = createSampleCustomType("PRODUCT.CODE", "Original description");
    repository.save(original);

    CustomSemanticType updated = createSampleCustomType("PRODUCT.NEW_CODE", "Updated description");
    CustomSemanticType result = repository.update("PRODUCT.CODE", updated);

    assertThat(result.getSemanticType()).isEqualTo("PRODUCT.NEW_CODE");
    assertThat(result.getDescription()).isEqualTo("Updated description");
    assertThat(repository.existsBySemanticType("PRODUCT.CODE")).isFalse();
    assertThat(repository.existsBySemanticType("PRODUCT.NEW_CODE")).isTrue();
  }

  @Test
  void shouldDeleteCustomType() {
    CustomSemanticType customType = createSampleCustomType("TEMP.TYPE", "Temporary type");
    repository.save(customType);

    boolean deleted = repository.deleteBySemanticType("TEMP.TYPE");

    assertThat(deleted).isTrue();
    assertThat(repository.existsBySemanticType("TEMP.TYPE")).isFalse();
    assertThat(repository.findBySemanticType("TEMP.TYPE")).isEmpty();
  }

  @Test
  void shouldReturnFalseWhenDeletingNonExistentType() {
    boolean deleted = repository.deleteBySemanticType("NON.EXISTENT");

    assertThat(deleted).isFalse();
  }

  @Test
  void shouldReturnAllCustomTypes() {
    CustomSemanticType type1 = createSampleCustomType("TYPE.ONE", "First type");
    CustomSemanticType type2 = createSampleCustomType("TYPE.TWO", "Second type");
    CustomSemanticType type3 = createSampleCustomType("TYPE.THREE", "Third type");

    repository.save(type1);
    repository.save(type2);
    repository.save(type3);

    List<CustomSemanticType> allTypes = repository.findAll();

    assertThat(allTypes).hasSize(3);
    assertThat(allTypes)
        .extracting(CustomSemanticType::getSemanticType)
        .containsExactlyInAnyOrder("TYPE.ONE", "TYPE.TWO", "TYPE.THREE");
  }

  @Test
  void shouldReturnCorrectCount() {
    assertThat(repository.count()).isEqualTo(0);

    repository.save(createSampleCustomType("TYPE.A", "Type A"));
    assertThat(repository.count()).isEqualTo(1);

    repository.save(createSampleCustomType("TYPE.B", "Type B"));
    assertThat(repository.count()).isEqualTo(2);

    repository.deleteBySemanticType("TYPE.A");
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void shouldLoadExistingCustomTypesFromFileOnInit() throws IOException {
    String existingJson =
        """
            [
                {
                    "semanticType": "EXISTING.TYPE",
                    "description": "Pre-existing type",
                    "pluginType": "REGEX",
                    "priority": 2000
                }
            ]
            """;
    Files.writeString(customTypesFile, existingJson);

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    Optional<CustomSemanticType> loaded = newRepository.findBySemanticType("EXISTING.TYPE");
    assertThat(loaded).isPresent();
    assertThat(loaded.get().getDescription()).isEqualTo("Pre-existing type");
    // Validate the structure instead of non-existent fields
    assertThat(loaded.get().getPluginType()).isEqualTo("REGEX");
  }

  @Test
  void shouldFixInvalidPriorityWhenLoadingFromFile() throws IOException {
    String jsonWithInvalidPriority =
        """
            [
                {
                    "semanticType": "INVALID.PRIORITY",
                    "description": "Type with invalid priority",
                    "pluginType": "REGEX",
                    "priority": 1500
                }
            ]
            """;
    Files.writeString(customTypesFile, jsonWithInvalidPriority);

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    Optional<CustomSemanticType> loaded = newRepository.findBySemanticType("INVALID.PRIORITY");
    assertThat(loaded).isPresent();
    assertThat(loaded.get().getPriority()).isEqualTo(2000);
  }

  @Test
  void shouldHandleNullPriorityWhenLoadingFromFile() throws IOException {
    String jsonWithNullPriority =
        """
            [
                {
                    "semanticType": "NULL.PRIORITY",
                    "description": "Type with null priority",
                    "pluginType": "REGEX",
                    "priority": null
                }
            ]
            """;
    Files.writeString(customTypesFile, jsonWithNullPriority);

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    Optional<CustomSemanticType> loaded = newRepository.findBySemanticType("NULL.PRIORITY");
    assertThat(loaded).isPresent();
    assertThat(loaded.get().getPriority()).isEqualTo(2000);
  }

  @Test
  void shouldClearAndReloadCustomTypes() throws IOException {
    repository.save(createSampleCustomType("TEMP.TYPE", "Temporary"));
    assertThat(repository.count()).isEqualTo(1);

    String newJson =
        """
            [
                {
                    "semanticType": "RELOADED.TYPE",
                    "description": "Reloaded type",
                    "pluginType": "REGEX",
                    "priority": 2000
                }
            ]
            """;
    Files.writeString(customTypesFile, newJson);

    repository.reload();

    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.existsBySemanticType("TEMP.TYPE")).isFalse();
    assertThat(repository.existsBySemanticType("RELOADED.TYPE")).isTrue();
  }

  @Test
  void shouldProvideDefensiveCopyOfInternalMap() {
    CustomSemanticType type1 = createSampleCustomType("MAP.TEST1", "First");
    CustomSemanticType type2 = createSampleCustomType("MAP.TEST2", "Second");
    repository.save(type1);
    repository.save(type2);

    var mapCopy = repository.getInternalMap();
    mapCopy.clear();

    assertThat(repository.count()).isEqualTo(2);
    assertThat(repository.existsBySemanticType("MAP.TEST1")).isTrue();
    assertThat(repository.existsBySemanticType("MAP.TEST2")).isTrue();
  }

  @Test
  void shouldHandleNonExistentFileGracefully() {
    when(applicationProperties.getCustomTypesFile()).thenReturn("/non/existent/path/file.json");

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    assertThat(newRepository.count()).isEqualTo(0);
    assertThat(newRepository.findAll()).isEmpty();
  }

  @Test
  void shouldHandleIOExceptionDuringSave() throws IOException {
    // Create a file that can't be written to
    Path readOnlyFile = tempDir.resolve("readonly.json");
    Files.writeString(readOnlyFile, "[]");
    readOnlyFile.toFile().setReadOnly();

    when(applicationProperties.getCustomTypesFile()).thenReturn(readOnlyFile.toString());

    repository = new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    repository.init();

    // Saving should not throw exception but log error
    CustomSemanticType customType = createSampleCustomType("FAIL.SAVE", "Should fail to save");
    CustomSemanticType result = repository.save(customType);

    // The type should still be in memory
    assertThat(result).isEqualTo(customType);
    assertThat(repository.existsBySemanticType("FAIL.SAVE")).isTrue();

    // Clean up
    readOnlyFile.toFile().setWritable(true);
  }

  @Test
  void shouldHandleCorruptedJsonFile() throws IOException {
    String corruptedJson = "{ this is not valid json [}";
    Files.writeString(customTypesFile, corruptedJson);

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    // Should start with empty repository when file is corrupted
    assertThat(newRepository.count()).isEqualTo(0);
    assertThat(newRepository.findAll()).isEmpty();
  }

  @Test
  void shouldPersistMultipleTypesCorrectly() throws IOException {
    CustomSemanticType type1 = createSampleCustomType("PERSIST.ONE", "First to persist");
    CustomSemanticType type2 = createSampleCustomType("PERSIST.TWO", "Second to persist");
    CustomSemanticType type3 = createSampleCustomType("PERSIST.THREE", "Third to persist");

    repository.save(type1);
    repository.save(type2);
    repository.save(type3);

    // Read the file and verify all types are persisted
    String fileContent = Files.readString(customTypesFile);
    assertThat(fileContent).contains("PERSIST.ONE", "PERSIST.TWO", "PERSIST.THREE");
    assertThat(fileContent).contains("First to persist", "Second to persist", "Third to persist");

    // Create new repository and verify it loads all types
    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    assertThat(newRepository.count()).isEqualTo(3);
    assertThat(newRepository.findAll())
        .extracting(CustomSemanticType::getSemanticType)
        .containsExactlyInAnyOrder("PERSIST.ONE", "PERSIST.TWO", "PERSIST.THREE");
  }

  @Test
  void shouldMaintainTypeIntegrityDuringConcurrentOperations() throws InterruptedException {
    // Test thread safety with concurrent operations
    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads[i] =
          new Thread(
              () -> {
                CustomSemanticType type =
                    createSampleCustomType("CONCURRENT." + index, "Thread " + index);
                repository.save(type);
              });
    }

    // Start all threads
    for (Thread thread : threads) {
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }

    // Verify all types were saved
    assertThat(repository.count()).isEqualTo(threadCount);
    for (int i = 0; i < threadCount; i++) {
      assertThat(repository.existsBySemanticType("CONCURRENT." + i)).isTrue();
    }
  }

  @Test
  void shouldUpdateTypeAndPersistChanges() throws IOException {
    CustomSemanticType original = createSampleCustomType("UPDATE.TEST", "Original description");
    repository.save(original);

    // Update with different semantic type (rename)
    CustomSemanticType updated = createSampleCustomType("UPDATE.RENAMED", "Updated description");
    updated.setPriority(3000); // Also change priority

    repository.update("UPDATE.TEST", updated);

    // Verify in-memory state
    assertThat(repository.existsBySemanticType("UPDATE.TEST")).isFalse();
    assertThat(repository.existsBySemanticType("UPDATE.RENAMED")).isTrue();

    Optional<CustomSemanticType> found = repository.findBySemanticType("UPDATE.RENAMED");
    assertThat(found).isPresent();
    assertThat(found.get().getDescription()).isEqualTo("Updated description");
    assertThat(found.get().getPriority()).isEqualTo(3000);

    // Verify persistence
    String fileContent = Files.readString(customTypesFile);
    assertThat(fileContent).contains("UPDATE.RENAMED");
    assertThat(fileContent).doesNotContain("UPDATE.TEST");
  }

  @Test
  void shouldHandleEmptyJsonArray() throws IOException {
    String emptyJson = "[]";
    Files.writeString(customTypesFile, emptyJson);

    FileBasedCustomSemanticTypeRepository newRepository =
        new FileBasedCustomSemanticTypeRepository(objectMapper, applicationProperties);
    newRepository.init();

    assertThat(newRepository.count()).isEqualTo(0);
    assertThat(newRepository.findAll()).isEmpty();

    // Should be able to add types to empty repository
    CustomSemanticType newType = createSampleCustomType("NEW.TYPE", "Added to empty");
    newRepository.save(newType);

    assertThat(newRepository.count()).isEqualTo(1);
    assertThat(newRepository.existsBySemanticType("NEW.TYPE")).isTrue();
  }

  @Test
  void shouldPreservePrettyPrintingInFile() throws IOException {
    CustomSemanticType type = createSampleCustomType("PRETTY.PRINT", "Test pretty printing");
    repository.save(type);

    String fileContent = Files.readString(customTypesFile);

    // Verify the file is pretty printed (contains newlines and indentation)
    assertThat(fileContent).contains("\n");
    assertThat(fileContent).contains("  "); // Contains indentation
    assertThat(fileContent).startsWith("[");
    assertThat(fileContent.trim()).endsWith("]");
  }

  @Test
  void shouldHandleComplexSemanticTypeStructure() {
    CustomSemanticType complexType = createSampleCustomType("COMPLEX.TYPE", "Complex structure");

    // Add locale configuration
    CustomSemanticType.LocaleConfig localeConfig =
        CustomSemanticType.LocaleConfig.builder()
            .localeTag("en-US")
            .matchEntries(
                Arrays.asList(
                    CustomSemanticType.MatchEntry.builder()
                        .regExpReturned("^[A-Z]{2}-\\d{6}$")
                        .isRegExpComplete(true)
                        .build()))
            .build();
    complexType.setValidLocales(Arrays.asList(localeConfig));

    // Add invalid list
    complexType.setInvalidList(Arrays.asList("XX-000000", "ZZ-999999"));

    repository.save(complexType);

    Optional<CustomSemanticType> loaded = repository.findBySemanticType("COMPLEX.TYPE");
    assertThat(loaded).isPresent();
    assertThat(loaded.get().getValidLocales()).hasSize(1);
    assertThat(loaded.get().getValidLocales().get(0).getLocaleTag()).isEqualTo("en-US");
    assertThat(loaded.get().getInvalidList()).hasSize(2);
  }

  @Test
  void shouldReturnEmptyOptionalForNullSemanticType() {
    Optional<CustomSemanticType> result = repository.findBySemanticType(null);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnFalseForNullSemanticTypeExists() {
    boolean exists = repository.existsBySemanticType(null);
    assertThat(exists).isFalse();
  }

  @Test
  void shouldReturnFalseWhenDeletingNullSemanticType() {
    boolean deleted = repository.deleteBySemanticType(null);
    assertThat(deleted).isFalse();
  }

  private CustomSemanticType createSampleCustomType(String semanticType, String description) {
    CustomSemanticType customType = new CustomSemanticType();
    customType.setSemanticType(semanticType);
    customType.setDescription(description);
    customType.setPluginType("REGEX");
    // Set up basic type structure without non-existent fields
    customType.setPriority(2000);
    return customType;
  }
}

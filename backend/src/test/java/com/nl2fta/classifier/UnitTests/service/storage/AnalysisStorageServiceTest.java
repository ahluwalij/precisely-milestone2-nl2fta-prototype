package com.nl2fta.classifier.UnitTests.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

class AnalysisStorageServiceTest {

  private AnalysisStorageService analysisStorageService;

  @BeforeEach
  void setUp() {
    analysisStorageService = new AnalysisStorageService();
  }

  @Test
  void shouldStoreAndRetrieveAnalysisSuccessfully() {
    String fileName = "test_table.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("test-analysis-123");
    response.setTableName("test_table");

    // Create sample data with LinkedHashMap to preserve order
    LinkedHashMap<String, Object> row1 = new LinkedHashMap<>();
    row1.put("id", "1");
    row1.put("name", "John");
    row1.put("email", "john@example.com");

    LinkedHashMap<String, Object> row2 = new LinkedHashMap<>();
    row2.put("id", "2");
    row2.put("name", "Jane");
    row2.put("email", "jane@example.com");

    List<Map<String, Object>> data = List.of(row1, row2);
    response.setData(data);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getAnalysisId()).isEqualTo(analysisId);
    assertThat(storedAnalysis.getFileName()).isEqualTo(fileName);
    assertThat(storedAnalysis.getResponse()).isEqualTo(response);
    assertThat(storedAnalysis.getColumns()).containsExactly("id", "name", "email");
    assertThat(storedAnalysis.getData()).hasSize(2);
    assertThat(storedAnalysis.getTimestamp()).isNotNull();
    assertThat(storedAnalysis.getLocale()).isEqualTo("en-US");
  }

  @Test
  void shouldReturnNullForNonExistentAnalysis() {
    String nonExistentId = "non-existent-id";

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(nonExistentId);

    assertThat(storedAnalysis).isNull();
  }

  @Test
  void shouldDeleteAnalysisSuccessfully() {
    String fileName = "temp_table.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("test-analysis-456");

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    assertThat(analysisStorageService.getAnalysis(analysisId)).isNotNull();

    boolean deleted = analysisStorageService.deleteAnalysis(analysisId);

    assertThat(deleted).isTrue();
    assertThat(analysisStorageService.getAnalysis(analysisId)).isNull();
  }

  @Test
  void shouldReturnFalseWhenDeletingNonExistentAnalysis() {
    boolean deleted = analysisStorageService.deleteAnalysis("non-existent-id");
    assertThat(deleted).isFalse();
  }

  @Test
  void shouldReturnAllStoredAnalyses() {
    String fileName1 = "file1.csv";
    String fileName2 = "file2.csv";
    String fileName3 = "file3.csv";

    TableClassificationResponse response1 = new TableClassificationResponse();
    response1.setAnalysisId("analysis-1");

    TableClassificationResponse response2 = new TableClassificationResponse();
    response2.setAnalysisId("analysis-2");

    TableClassificationResponse response3 = new TableClassificationResponse();
    response3.setAnalysisId("analysis-3");

    String id1 = analysisStorageService.storeAnalysis(fileName1, response1);
    String id2 = analysisStorageService.storeAnalysis(fileName2, response2);
    String id3 = analysisStorageService.storeAnalysis(fileName3, response3);

    List<AnalysisStorageService.StoredAnalysis> allAnalyses =
        analysisStorageService.getAllAnalyses();

    assertThat(allAnalyses).hasSize(3);
    assertThat(allAnalyses.stream().map(AnalysisStorageService.StoredAnalysis::getAnalysisId))
        .containsExactlyInAnyOrder(id1, id2, id3);
  }

  @Test
  void shouldReturnEmptyListWhenNoAnalysesStored() {
    List<AnalysisStorageService.StoredAnalysis> allAnalyses =
        analysisStorageService.getAllAnalyses();
    assertThat(allAnalyses).isEmpty();
  }

  @Test
  void shouldClearAllAnalysesSuccessfully() {
    String fileName1 = "file1.csv";
    String fileName2 = "file2.csv";

    TableClassificationResponse response1 = new TableClassificationResponse();
    response1.setAnalysisId("analysis-1");

    TableClassificationResponse response2 = new TableClassificationResponse();
    response2.setAnalysisId("analysis-2");

    analysisStorageService.storeAnalysis(fileName1, response1);
    analysisStorageService.storeAnalysis(fileName2, response2);
    assertThat(analysisStorageService.getAllAnalyses()).hasSize(2);

    analysisStorageService.clearAnalyses();

    assertThat(analysisStorageService.getAllAnalyses()).isEmpty();
  }

  @Test
  void shouldUpdateExistingAnalysisSuccessfully() {
    String fileName = "updateable.csv";

    TableClassificationResponse initialResponse = new TableClassificationResponse();
    initialResponse.setAnalysisId("initial-analysis");
    initialResponse.setTableName("initial_table");

    String analysisId = analysisStorageService.storeAnalysis(fileName, initialResponse);
    LocalDateTime initialTimestamp = analysisStorageService.getAnalysis(analysisId).getTimestamp();

    // Wait a bit to ensure timestamp difference
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
    }

    TableClassificationResponse updatedResponse = new TableClassificationResponse();
    updatedResponse.setAnalysisId("updated-analysis");
    updatedResponse.setTableName("updated_table");

    analysisStorageService.updateAnalysis(analysisId, updatedResponse);

    AnalysisStorageService.StoredAnalysis updatedAnalysis =
        analysisStorageService.getAnalysis(analysisId);
    assertThat(updatedAnalysis.getResponse().getTableName()).isEqualTo("updated_table");
    assertThat(updatedAnalysis.getTimestamp()).isAfter(initialTimestamp);
  }

  @Test
  void shouldHandleAnalysisTimestampMetadata() {
    String fileName = "timestamp_test.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("timestamp-test");

    LocalDateTime beforeStore = LocalDateTime.now().minusSeconds(1);
    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    LocalDateTime afterStore = LocalDateTime.now().plusSeconds(1);

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);
    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getTimestamp()).isAfter(beforeStore).isBefore(afterStore);
  }

  @Test
  void shouldPreserveColumnOrderFromLinkedHashMap() {
    String fileName = "ordered_columns.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("order-test");

    // Use LinkedHashMap to preserve insertion order
    LinkedHashMap<String, Object> row1 = new LinkedHashMap<>();
    row1.put("third", "c");
    row1.put("first", "a");
    row1.put("second", "b");

    response.setData(List.of(row1));

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis.getColumns()).containsExactly("third", "first", "second");
  }

  @Test
  void shouldHandleEmptyDataGracefully() {
    String fileName = "empty_data.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("empty-test");
    response.setData(List.of()); // Empty data

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getColumns()).isEmpty();
    assertThat(storedAnalysis.getData()).isEmpty();
  }

  @Test
  void shouldExtractColumnsFromColumnClassificationsWhenDataIsNull() {
    String fileName = "columns_only.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("columns-test");
    response.setData(null); // No data

    // But has column classifications
    Map<String, TableClassificationResponse.ColumnClassification> columnClassifications =
        new LinkedHashMap<>();
    columnClassifications.put("col1", new TableClassificationResponse.ColumnClassification());
    columnClassifications.put("col2", new TableClassificationResponse.ColumnClassification());
    columnClassifications.put("col3", new TableClassificationResponse.ColumnClassification());
    response.setColumnClassifications(columnClassifications);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getColumns()).containsExactly("col1", "col2", "col3");
    assertThat(storedAnalysis.getData()).isEmpty(); // Data is initialized as empty list, not null
  }

  @Test
  void shouldStoreLocaleFromProcessingMetadata() {
    String fileName = "locale_test.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("locale-test");

    // Set processing metadata with locale
    TableClassificationResponse.ProcessingMetadata metadata =
        new TableClassificationResponse.ProcessingMetadata();
    metadata.setLocaleUsed("fr-FR");
    response.setProcessingMetadata(metadata);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getLocale()).isEqualTo("fr-FR");
  }

  @Test
  void shouldExtractFieldDataWithShapeDetails() {
    String fileName = "shape_details.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("shape-test");

    // Create column classification with shape details
    Map<String, TableClassificationResponse.ColumnClassification> columnClassifications =
        new LinkedHashMap<>();

    TableClassificationResponse.ColumnClassification classification =
        new TableClassificationResponse.ColumnClassification();
    classification.setSemanticType("EMAIL.ADDRESS");
    classification.setConfidence(0.95);

    // Add shape details with examples
    TableClassificationResponse.ShapeDetail shape1 = new TableClassificationResponse.ShapeDetail();
    shape1.setExamples(List.of("user@example.com", "admin@test.org"));

    TableClassificationResponse.ShapeDetail shape2 = new TableClassificationResponse.ShapeDetail();
    shape2.setExamples(List.of("info@company.net"));

    classification.setShapeDetails(List.of(shape1, shape2));
    columnClassifications.put("email_column", classification);
    response.setColumnClassifications(columnClassifications);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis.getFields()).hasSize(1);
    AnalysisStorageService.FieldData fieldData = storedAnalysis.getFields().get(0);
    assertThat(fieldData.getFieldName()).isEqualTo("email_column");
    assertThat(fieldData.getCurrentSemanticType()).isEqualTo("EMAIL.ADDRESS");
    assertThat(fieldData.getCurrentConfidence()).isEqualTo(0.95);
    assertThat(fieldData.getSampleValues())
        .containsExactly("user@example.com", "admin@test.org", "info@company.net");
  }

  @Test
  void shouldHandleNullConfidenceInClassification() {
    String fileName = "null_confidence.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("null-confidence-test");

    Map<String, TableClassificationResponse.ColumnClassification> columnClassifications =
        new LinkedHashMap<>();
    TableClassificationResponse.ColumnClassification classification =
        new TableClassificationResponse.ColumnClassification();
    classification.setSemanticType("UNKNOWN");
    classification.setConfidence(null); // null confidence
    columnClassifications.put("unknown_column", classification);
    response.setColumnClassifications(columnClassifications);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis.getFields()).hasSize(1);
    assertThat(storedAnalysis.getFields().get(0).getCurrentConfidence()).isEqualTo(0.0);
  }

  @Test
  void shouldUpdateLocaleWhenUpdatingAnalysis() {
    String fileName = "update_locale.csv";

    TableClassificationResponse initialResponse = new TableClassificationResponse();
    initialResponse.setAnalysisId("initial");

    String analysisId = analysisStorageService.storeAnalysis(fileName, initialResponse);

    // Update with new locale
    TableClassificationResponse updatedResponse = new TableClassificationResponse();
    updatedResponse.setAnalysisId("updated");
    TableClassificationResponse.ProcessingMetadata metadata =
        new TableClassificationResponse.ProcessingMetadata();
    metadata.setLocaleUsed("de-DE");
    updatedResponse.setProcessingMetadata(metadata);

    analysisStorageService.updateAnalysis(analysisId, updatedResponse);

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);
    assertThat(storedAnalysis.getLocale()).isEqualTo("de-DE");
  }

  @Test
  void shouldNotUpdateNonExistentAnalysis() {
    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("new-response");

    // Try to update non-existent analysis
    analysisStorageService.updateAnalysis("non-existent-id", response);

    // Should not create new analysis
    assertThat(analysisStorageService.getAnalysis("non-existent-id")).isNull();
  }

  @Test
  void shouldExtractMultipleFieldsCorrectly() {
    String fileName = "multiple_fields.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("multi-field-test");

    Map<String, TableClassificationResponse.ColumnClassification> columnClassifications =
        new LinkedHashMap<>();

    // Field 1
    TableClassificationResponse.ColumnClassification class1 =
        new TableClassificationResponse.ColumnClassification();
    class1.setSemanticType("PERSON.NAME");
    class1.setConfidence(0.9);
    columnClassifications.put("name", class1);

    // Field 2
    TableClassificationResponse.ColumnClassification class2 =
        new TableClassificationResponse.ColumnClassification();
    class2.setSemanticType("PHONE.US");
    class2.setConfidence(0.85);
    columnClassifications.put("phone", class2);

    // Field 3
    TableClassificationResponse.ColumnClassification class3 =
        new TableClassificationResponse.ColumnClassification();
    class3.setSemanticType("ADDRESS.STREET");
    class3.setConfidence(0.75);
    columnClassifications.put("address", class3);

    response.setColumnClassifications(columnClassifications);

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis.getFields()).hasSize(3);
    assertThat(storedAnalysis.getFields())
        .extracting(AnalysisStorageService.FieldData::getFieldName)
        .containsExactlyInAnyOrder("name", "phone", "address");
    assertThat(storedAnalysis.getFields())
        .extracting(AnalysisStorageService.FieldData::getCurrentSemanticType)
        .containsExactlyInAnyOrder("PERSON.NAME", "PHONE.US", "ADDRESS.STREET");
  }

  @Test
  void shouldHandleDataWithNonLinkedHashMap() {
    String fileName = "regular_map.csv";

    TableClassificationResponse response = new TableClassificationResponse();
    response.setAnalysisId("map-test");

    // Use regular HashMap instead of LinkedHashMap
    Map<String, Object> row1 = new HashMap<>();
    row1.put("key1", "value1");
    row1.put("key2", "value2");
    row1.put("key3", "value3");

    response.setData(List.of(row1));

    String analysisId = analysisStorageService.storeAnalysis(fileName, response);
    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);

    assertThat(storedAnalysis).isNotNull();
    assertThat(storedAnalysis.getColumns()).hasSize(3);
    assertThat(storedAnalysis.getColumns()).containsExactlyInAnyOrder("key1", "key2", "key3");
  }

  @Test
  void shouldClearFieldsWhenUpdatingAnalysis() {
    String fileName = "clear_fields.csv";

    // Initial response with fields
    TableClassificationResponse initialResponse = new TableClassificationResponse();
    initialResponse.setAnalysisId("initial");
    Map<String, TableClassificationResponse.ColumnClassification> initialClassifications =
        new LinkedHashMap<>();
    TableClassificationResponse.ColumnClassification class1 =
        new TableClassificationResponse.ColumnClassification();
    class1.setSemanticType("OLD.TYPE");
    initialClassifications.put("field1", class1);
    initialResponse.setColumnClassifications(initialClassifications);

    String analysisId = analysisStorageService.storeAnalysis(fileName, initialResponse);
    assertThat(analysisStorageService.getAnalysis(analysisId).getFields()).hasSize(1);

    // Update with new fields
    TableClassificationResponse updatedResponse = new TableClassificationResponse();
    updatedResponse.setAnalysisId("updated");
    Map<String, TableClassificationResponse.ColumnClassification> updatedClassifications =
        new LinkedHashMap<>();
    TableClassificationResponse.ColumnClassification class2 =
        new TableClassificationResponse.ColumnClassification();
    class2.setSemanticType("NEW.TYPE");
    updatedClassifications.put("field2", class2);
    updatedResponse.setColumnClassifications(updatedClassifications);

    analysisStorageService.updateAnalysis(analysisId, updatedResponse);

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);
    assertThat(storedAnalysis.getFields()).hasSize(1);
    assertThat(storedAnalysis.getFields().get(0).getFieldName()).isEqualTo("field2");
    assertThat(storedAnalysis.getFields().get(0).getCurrentSemanticType()).isEqualTo("NEW.TYPE");
  }
}

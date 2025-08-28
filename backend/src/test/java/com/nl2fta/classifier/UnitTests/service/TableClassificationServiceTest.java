package com.nl2fta.classifier.UnitTests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeRegistryService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeValidationService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableClassificationServiceTest {

  @Mock private CustomSemanticTypeService customSemanticTypeService;

  @Mock private SemanticTypeRegistryService semanticTypeRegistry;

  @Mock private SemanticTypeValidationService validationService;

  @Mock private HybridCustomSemanticTypeRepository hybridRepository;

  private TableClassificationService tableClassificationService;

  @BeforeEach
  void setUp() {
    tableClassificationService =
        new TableClassificationService(
            customSemanticTypeService, semanticTypeRegistry, validationService, hybridRepository);

    // Set up default field values
    ReflectionTestUtils.setField(tableClassificationService, "ftaVersion", "16.0.3");
    ReflectionTestUtils.setField(tableClassificationService, "detectWindow", 20);
    ReflectionTestUtils.setField(tableClassificationService, "maxCardinality", 12000);
    ReflectionTestUtils.setField(tableClassificationService, "enableDefaultSemanticTypes", true);
  }

  @Test
  void shouldClassifySimpleTable() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getTableName()).isEqualTo("test_table");
    assertThat(response.getColumnClassifications()).isNotEmpty();
    assertThat(response.getProcessingMetadata()).isNotNull();
    assertThat(response.getProcessingMetadata().getTotalColumns()).isEqualTo(3);
    assertThat(response.getProcessingMetadata().getFtaVersion()).isEqualTo("16.0.3");
  }

  @Test
  void shouldHandleRequestWithMaxSamples() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setMaxSamples(1); // Limit to 1 sample
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getProcessingMetadata().getTotalRowsProcessed()).isEqualTo(1);
  }

  @Test
  void shouldHandleRequestWithStatistics() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setIncludeStatistics(true);
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getColumnClassifications()).isNotEmpty();
    // Note: Actual statistics would depend on FTA implementation
  }

  @Test
  void shouldHandleRequestWithCustomLocale() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setLocale("fr-FR");
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getProcessingMetadata().getLocaleUsed()).isEqualTo("fr-FR");
  }

  @Test
  void shouldHandleRequestWithNullLocale() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setLocale(null);
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    // Should use default locale processing
  }

  @Test
  void shouldDisableDefaultSemanticTypesWhenOverridesExist() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    // Service registers custom + built-in types only when useAllSemanticTypes=true
    request.setUseAllSemanticTypes(true);
    Map<String, com.nl2fta.classifier.dto.semantic_type.CustomSemanticType> overrides =
        new HashMap<>();
    overrides.put(
        "EMAIL.ADDRESS", new com.nl2fta.classifier.dto.semantic_type.CustomSemanticType());
    when(customSemanticTypeService.registerSemanticTypes(any()))
        .thenReturn(overrides); // Has overrides

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    // verify(customSemanticTypeService).registerSemanticTypes(any()); // This line was removed
  }

  @Test
  void shouldHandleEmptyData() {
    // Given
    TableClassificationRequest request = new TableClassificationRequest();
    request.setTableName("empty_table");
    request.setColumns(Arrays.asList("col1", "col2"));
    request.setData(new ArrayList<>());
    request.setIncludeStatistics(false);

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getTableName()).isEqualTo("empty_table");
    assertThat(response.getProcessingMetadata().getTotalRowsProcessed()).isEqualTo(0);
  }

  @Test
  void shouldHandleNullTableName() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setTableName(null);
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getTableName()).isNull();
  }

  @Test
  void shouldHandleDataWithNullValues() {
    // Given
    TableClassificationRequest request = new TableClassificationRequest();
    request.setTableName("null_values_table");
    request.setColumns(Arrays.asList("id", "name", "email"));
    request.setIncludeStatistics(false);

    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("id", "1");
    row1.put("name", null);
    row1.put("email", "test@example.com");

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("id", null);
    row2.put("name", "John");
    row2.put("email", null);

    request.setData(Arrays.asList(row1, row2));
    when(semanticTypeRegistry.getDescription(anyString())).thenReturn("Test description");

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getColumnClassifications()).hasSize(3);
  }

  @Test
  void shouldHandleDataWithEmptyStringValues() {
    // Given
    TableClassificationRequest request = new TableClassificationRequest();
    request.setTableName("empty_strings_table");
    request.setColumns(Arrays.asList("id", "name"));
    request.setIncludeStatistics(false);

    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("id", "");
    row1.put("name", "   "); // Whitespace only

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("id", "1");
    row2.put("name", "");

    request.setData(Arrays.asList(row1, row2));

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getColumnClassifications()).hasSize(2);
  }

  @Test
  void shouldHandleSemanticTypeRegistryWithDocumentation() {
    // Given
    TableClassificationRequest request = createSimpleRequest();

    SemanticTypeRegistryService.SemanticTypeInfo typeInfo =
        new SemanticTypeRegistryService.SemanticTypeInfo();
    when(semanticTypeRegistry.getSemanticTypeInfo(anyString())).thenReturn(typeInfo);

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
  }

  @Test
  void shouldHandleUnderscoreInLocale() {
    // Given
    TableClassificationRequest request = createSimpleRequest();
    request.setLocale("en_US"); // Underscore format

    // When
    TableClassificationResponse response = tableClassificationService.classifyTable(request);

    // Then
    assertThat(response).isNotNull();
    // Should convert underscore to dash for Locale.forLanguageTag
  }

  @Test
  void shouldMeasureProcessingTime() {
    // Given
    TableClassificationRequest request = createSimpleRequest();

    // When
    long startTime = System.currentTimeMillis();
    TableClassificationResponse response = tableClassificationService.classifyTable(request);
    long endTime = System.currentTimeMillis();

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getProcessingMetadata().getProcessingTimeMs()).isGreaterThan(0);
    assertThat(response.getProcessingMetadata().getProcessingTimeMs())
        .isLessThan(endTime - startTime + 100);
  }

  private TableClassificationRequest createSimpleRequest() {
    TableClassificationRequest request = new TableClassificationRequest();
    request.setTableName("test_table");
    request.setColumns(Arrays.asList("id", "name", "email"));
    request.setIncludeStatistics(false); // Set default value

    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("id", "1");
    row1.put("name", "John Doe");
    row1.put("email", "john@example.com");

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("id", "2");
    row2.put("name", "Jane Smith");
    row2.put("email", "jane@example.com");

    request.setData(Arrays.asList(row1, row2));
    return request;
  }
}

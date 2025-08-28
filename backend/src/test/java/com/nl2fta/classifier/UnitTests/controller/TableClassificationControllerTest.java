package com.nl2fta.classifier.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.nl2fta.classifier.config.ApplicationProperties;
import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.exception.GlobalExceptionHandler;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

@ExtendWith(MockitoExtension.class)
@DisplayName("TableClassificationController Tests")
class TableClassificationControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private TableClassificationService classificationService;

  @Mock private AnalysisStorageService analysisStorageService;

  @Mock private ApplicationProperties applicationProperties;

  @Captor private ArgumentCaptor<TableClassificationRequest> requestCaptor;

  @Captor private ArgumentCaptor<TableClassificationResponse> responseCaptor;

  private TableClassificationController controller;

  @BeforeEach
  void setUp() {
    controller =
        new TableClassificationController(
            classificationService, analysisStorageService, applicationProperties);
    ReflectionTestUtils.setField(controller, "defaultMaxSamples", 1000);
    objectMapper = new ObjectMapper();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  @DisplayName("POST /api/classify/table - Table Classification")
  class ClassifyTable {

    @Test
    @DisplayName("Should successfully classify table with all required fields")
    void shouldClassifyTableSuccessfully() throws Exception {
      // Given
      List<String> columns = Arrays.asList("email", "age", "city");
      List<Map<String, Object>> data = createSampleData();

      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("users")
              .columns(columns)
              .data(data)
              .maxSamples(500)
              .locale("en-US")
              .includeStatistics(true)
              .build();

      TableClassificationResponse response = createMockResponse("users", columns);
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-123");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("users"))
          .andExpect(jsonPath("$.analysis_id").value("analysis-123"))
          .andExpect(
              jsonPath("$.column_classifications.email.semantic_type").value("EMAIL.ADDRESS"))
          .andExpect(jsonPath("$.column_classifications.age.semantic_type").value("AGE"));

      // Verify service interactions
      verify(classificationService).classifyTable(requestCaptor.capture());
      TableClassificationRequest capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.getTableName()).isEqualTo("users");
      assertThat(capturedRequest.getMaxSamples()).isEqualTo(500);
      assertThat(capturedRequest.getData()).hasSize(3);

      verify(analysisStorageService).storeAnalysis(anyString(), responseCaptor.capture());
      TableClassificationResponse storedResponse = responseCaptor.getValue();
      assertThat(storedResponse.getData()).isEqualTo(data);
      assertThat(storedResponse.getAnalysisId()).isEqualTo("analysis-123");
    }

    @Test
    @DisplayName("Should use default max samples when not provided")
    void shouldUseDefaultMaxSamples() throws Exception {
      // Given
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("products")
              .columns(Arrays.asList("name", "price"))
              .data(createSimpleData())
              .build();

      TableClassificationResponse response =
          createMockResponse("products", Arrays.asList("name", "price"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-456");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());

      verify(classificationService).classifyTable(requestCaptor.capture());
      assertThat(requestCaptor.getValue().getMaxSamples()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle validation errors for missing required fields")
    void shouldHandleValidationErrors() throws Exception {
      // Given - request with missing columns
      String invalidRequest =
          """
                {
                    "table_name": "test",
                    "data": []
                }
                """;

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty data gracefully")
    void shouldHandleEmptyData() throws Exception {
      // Given
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("empty_table")
              .columns(Arrays.asList("col1", "col2"))
              .data(new ArrayList<>())
              .build();

      TableClassificationResponse response =
          createMockResponse("empty_table", Arrays.asList("col1", "col2"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-789");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("empty_table"));
    }

    @Test
    @DisplayName("Should propagate service exceptions")
    void shouldPropagateServiceExceptions() throws Exception {
      // Given
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("error_table")
              .columns(Arrays.asList("col1"))
              .data(createSimpleData())
              .build();

      when(classificationService.classifyTable(any()))
          .thenThrow(new RuntimeException("Classification failed"));

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should handle very large table names")
    void shouldHandleVeryLargeTableNames() throws Exception {
      // Given
      StringBuilder largeTableName = new StringBuilder();
      for (int i = 0; i < 1000; i++) {
        largeTableName.append("VeryLongTableName");
      }

      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName(largeTableName.toString())
              .columns(Arrays.asList("col1"))
              .data(createSimpleData())
              .build();

      TableClassificationResponse response =
          createMockResponse(largeTableName.toString(), Arrays.asList("col1"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-large");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value(largeTableName.toString()));
    }

    @Test
    @DisplayName("Should handle table with special characters in name")
    void shouldHandleTableWithSpecialCharactersInName() throws Exception {
      // Given
      String specialTableName = "table_with_spëcîál_çhärs@#$%^&*()";
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName(specialTableName)
              .columns(Arrays.asList("cöl1", "çöl2"))
              .data(createSimpleData())
              .build();

      TableClassificationResponse response =
          createMockResponse(specialTableName, Arrays.asList("cöl1", "çöl2"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-special");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value(specialTableName));
    }

    @Test
    @DisplayName("Should handle table with many columns")
    void shouldHandleTableWithManyColumns() throws Exception {
      // Given
      List<String> manyColumns = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        manyColumns.add("col_" + i);
      }

      List<Map<String, Object>> manyColumnsData = new ArrayList<>();
      Map<String, Object> row = new HashMap<>();
      for (int i = 0; i < 100; i++) {
        row.put("col_" + i, "value_" + i);
      }
      manyColumnsData.add(row);

      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("many_columns_table")
              .columns(manyColumns)
              .data(manyColumnsData)
              .build();

      TableClassificationResponse response = createMockResponse("many_columns_table", manyColumns);
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any()))
          .thenReturn("analysis-many-cols");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("many_columns_table"));

      verify(classificationService).classifyTable(requestCaptor.capture());
      assertThat(requestCaptor.getValue().getColumns()).hasSize(100);
    }

    @Test
    @DisplayName("Should handle negative max samples value")
    void shouldHandleNegativeMaxSamplesValue() throws Exception {
      // Given
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("test_table")
              .columns(Arrays.asList("col1"))
              .data(createSimpleData())
              .maxSamples(-100)
              .build();

      TableClassificationResponse response =
          createMockResponse("test_table", Arrays.asList("col1"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any()))
          .thenReturn("analysis-negative");

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());

      verify(classificationService).classifyTable(requestCaptor.capture());
      assertThat(requestCaptor.getValue().getMaxSamples()).isEqualTo(-100);
    }

    @Test
    @DisplayName("Should handle storage service exceptions during analysis storage")
    void shouldHandleStorageServiceExceptionsDuringAnalysisStorage() throws Exception {
      // Given
      TableClassificationRequest request =
          TableClassificationRequest.builder()
              .tableName("storage_error_table")
              .columns(Arrays.asList("col1"))
              .data(createSimpleData())
              .build();

      TableClassificationResponse response =
          createMockResponse("storage_error_table", Arrays.asList("col1"));
      when(classificationService.classifyTable(any())).thenReturn(response);
      when(analysisStorageService.storeAnalysis(anyString(), any()))
          .thenThrow(new RuntimeException("Storage service unavailable"));

      // When & Then
      mockMvc
          .perform(
              post("/api/classify/table")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isInternalServerError());
    }
  }

  @Nested
  @DisplayName("GET /api/health - Health Check")
  class HealthCheck {

    @Test
    @DisplayName("Should return UP status with timestamp")
    void shouldReturnHealthStatus() throws Exception {
      // When & Then
      mockMvc
          .perform(get("/api/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.timestamp").exists());
    }
  }

  @Nested
  @DisplayName("GET /api/analyses - Get All Analyses")
  class GetAllAnalyses {

    @Test
    @DisplayName("Should return all stored analyses")
    void shouldReturnAllAnalyses() throws Exception {
      // Given
      List<AnalysisStorageService.StoredAnalysis> storedAnalyses = createStoredAnalyses();
      when(analysisStorageService.getAllAnalyses()).thenReturn(storedAnalyses);

      // When & Then
      mockMvc
          .perform(get("/api/analyses"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$[0].analysisId").value("analysis-1"))
          .andExpect(jsonPath("$[0].fileName").value("users.csv"))
          .andExpect(jsonPath("$[1].analysisId").value("analysis-2"))
          .andExpect(jsonPath("$[1].fileName").value("products.csv"));
    }

    @Test
    @DisplayName("Should handle empty analyses list")
    void shouldHandleEmptyAnalysesList() throws Exception {
      // Given
      when(analysisStorageService.getAllAnalyses()).thenReturn(new ArrayList<>());

      // When & Then
      mockMvc
          .perform(get("/api/analyses"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$").isEmpty());
    }
  }

  @Nested
  @DisplayName("DELETE /api/analyses - Delete Operations")
  class DeleteOperations {

    @Test
    @DisplayName("Should delete all analyses successfully")
    void shouldDeleteAllAnalyses() throws Exception {
      // When & Then
      mockMvc.perform(delete("/api/analyses")).andExpect(status().isNoContent());

      verify(analysisStorageService).clearAnalyses();
    }

    @Test
    @DisplayName("Should delete specific analysis when exists")
    void shouldDeleteSpecificAnalysis() throws Exception {
      // Given
      when(analysisStorageService.deleteAnalysis("analysis-123")).thenReturn(true);

      // When & Then
      mockMvc.perform(delete("/api/analyses/analysis-123")).andExpect(status().isNoContent());

      verify(analysisStorageService).deleteAnalysis("analysis-123");
    }

    @Test
    @DisplayName("Should return 404 when analysis not found")
    void shouldReturn404WhenAnalysisNotFound() throws Exception {
      // Given
      when(analysisStorageService.deleteAnalysis("unknown-id")).thenReturn(false);

      // When & Then
      mockMvc.perform(delete("/api/analyses/unknown-id")).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("POST /api/analyses/{id}/reanalyze - Reanalysis")
  class Reanalysis {

    @Test
    @DisplayName("Should reanalyze stored analysis with current semantic types")
    void shouldReanalyzeSuccessfully() throws Exception {
      // Given
      AnalysisStorageService.StoredAnalysis storedAnalysis = createStoredAnalysisWithData();
      when(analysisStorageService.getAnalysis("analysis-123")).thenReturn(storedAnalysis);

      TableClassificationResponse newResponse =
          createMockResponse("users.csv", Arrays.asList("email", "age"));
      when(classificationService.classifyTable(any())).thenReturn(newResponse);

      // When & Then
      mockMvc
          .perform(post("/api/analyses/analysis-123/reanalyze"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("users.csv"))
          .andExpect(jsonPath("$.analysis_id").value("analysis-123"));

      verify(classificationService).classifyTable(requestCaptor.capture());
      TableClassificationRequest reanalysisRequest = requestCaptor.getValue();
      assertThat(reanalysisRequest.getTableName()).isEqualTo("users.csv");
      assertThat(reanalysisRequest.getColumns()).containsExactly("email", "age");
      assertThat(reanalysisRequest.getData()).hasSize(2);
    }

    @Test
    @DisplayName("Should return 404 when analysis not found for reanalysis")
    void shouldReturn404ForUnknownAnalysis() throws Exception {
      // Given
      when(analysisStorageService.getAnalysis("unknown-id")).thenReturn(null);

      // When & Then
      mockMvc.perform(post("/api/analyses/unknown-id/reanalyze")).andExpect(status().isNotFound());

      verify(classificationService, times(0)).classifyTable(any());
    }

    @Test
    @DisplayName("Should return 400 when no data available for reanalysis")
    void shouldReturn400WhenNoDataAvailable() throws Exception {
      // Given
      AnalysisStorageService.StoredAnalysis storedAnalysis =
          new AnalysisStorageService.StoredAnalysis();
      storedAnalysis.setAnalysisId("analysis-123");
      storedAnalysis.setFileName("empty.csv");
      storedAnalysis.setData(null); // No data available

      when(analysisStorageService.getAnalysis("analysis-123")).thenReturn(storedAnalysis);

      // When & Then
      mockMvc
          .perform(post("/api/analyses/analysis-123/reanalyze"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle reanalysis errors gracefully")
    void shouldHandleReanalysisErrors() throws Exception {
      // Given
      AnalysisStorageService.StoredAnalysis storedAnalysis = createStoredAnalysisWithData();
      when(analysisStorageService.getAnalysis("analysis-123")).thenReturn(storedAnalysis);
      when(classificationService.classifyTable(any()))
          .thenThrow(new RuntimeException("Reanalysis failed"));

      // When & Then
      mockMvc
          .perform(post("/api/analyses/analysis-123/reanalyze"))
          .andExpect(status().isInternalServerError());
    }
  }

  // Helper methods
  private List<Map<String, Object>> createSampleData() {
    List<Map<String, Object>> data = new ArrayList<>();

    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("email", "john@example.com");
    row1.put("age", 25);
    row1.put("city", "New York");
    data.add(row1);

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("email", "jane@example.com");
    row2.put("age", 30);
    row2.put("city", "Los Angeles");
    data.add(row2);

    Map<String, Object> row3 = new LinkedHashMap<>();
    row3.put("email", "bob@example.com");
    row3.put("age", 35);
    row3.put("city", "Chicago");
    data.add(row3);

    return data;
  }

  private List<Map<String, Object>> createSimpleData() {
    List<Map<String, Object>> data = new ArrayList<>();
    Map<String, Object> row = new HashMap<>();
    row.put("name", "Product 1");
    row.put("price", 19.99);
    data.add(row);
    return data;
  }

  private TableClassificationResponse createMockResponse(String tableName, List<String> columns) {
    Map<String, TableClassificationResponse.ColumnClassification> classifications = new HashMap<>();

    for (String column : columns) {
      TableClassificationResponse.ColumnClassification classification =
          TableClassificationResponse.ColumnClassification.builder()
              .columnName(column)
              .baseType("STRING")
              .semanticType(getSemanticTypeForColumn(column))
              .confidence(0.95)
              .build();
      classifications.put(column, classification);
    }

    TableClassificationResponse.ProcessingMetadata metadata =
        TableClassificationResponse.ProcessingMetadata.builder()
            .totalColumns(columns.size())
            .analyzedColumns(columns.size())
            .totalRowsProcessed(100)
            .processingTimeMs(250L)
            .ftaVersion("5.0.0")
            .localeUsed("en-US")
            .build();

    return TableClassificationResponse.builder()
        .tableName(tableName)
        .columnClassifications(classifications)
        .processingMetadata(metadata)
        .build();
  }

  private String getSemanticTypeForColumn(String column) {
    return switch (column.toLowerCase()) {
      case "email" -> "EMAIL.ADDRESS";
      case "age" -> "AGE";
      case "city" -> "CITY";
      case "name" -> "NAME.FIRST_LAST";
      case "price" -> "PRICE";
      default -> "STRING";
    };
  }

  private List<AnalysisStorageService.StoredAnalysis> createStoredAnalyses() {
    List<AnalysisStorageService.StoredAnalysis> analyses = new ArrayList<>();

    AnalysisStorageService.StoredAnalysis analysis1 = new AnalysisStorageService.StoredAnalysis();
    analysis1.setAnalysisId("analysis-1");
    analysis1.setFileName("users.csv");
    analysis1.setTimestamp(LocalDateTime.now().minusHours(1));
    analyses.add(analysis1);

    AnalysisStorageService.StoredAnalysis analysis2 = new AnalysisStorageService.StoredAnalysis();
    analysis2.setAnalysisId("analysis-2");
    analysis2.setFileName("products.csv");
    analysis2.setTimestamp(LocalDateTime.now().minusMinutes(30));
    analyses.add(analysis2);

    return analyses;
  }

  private AnalysisStorageService.StoredAnalysis createStoredAnalysisWithData() {
    AnalysisStorageService.StoredAnalysis analysis = new AnalysisStorageService.StoredAnalysis();
    analysis.setAnalysisId("analysis-123");
    analysis.setFileName("users.csv");
    analysis.setTimestamp(LocalDateTime.now());

    List<Map<String, Object>> data = new ArrayList<>();
    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("email", "test@example.com");
    row1.put("age", 28);
    data.add(row1);

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("email", "user@example.com");
    row2.put("age", 32);
    data.add(row2);

    analysis.setData(data);
    analysis.setColumns(Arrays.asList("email", "age"));
    analysis.setLocale("en-US");

    return analysis;
  }
}

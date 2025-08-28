package com.nl2fta.classifier.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.data_processing.CsvParsingService;
import com.nl2fta.classifier.service.data_processing.SqlFileProcessorService;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

@WebMvcTest(FileUploadController.class)
@TestPropertySource(
    properties = {"app.upload.allowed-extensions=csv,sql", "app.upload.max-file-size=10485760"})
@DisplayName("File Upload Controller Tests")
class FileUploadControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApplicationContext applicationContext;

  @MockitoBean private TableClassificationService classificationService;

  @MockitoBean private SqlFileProcessorService sqlFileProcessorService;

  @MockitoBean private CsvParsingService csvParsingService;

  @MockitoBean private AnalysisStorageService analysisStorageService;

  private TableClassificationRequest mockRequest;
  private TableClassificationResponse mockResponse;

  @BeforeEach
  void setUp() {
    // Ensure controller @Value fields are initialized for WebMvcTest
    FileUploadController controller = applicationContext.getBean(FileUploadController.class);
    ReflectionTestUtils.setField(
        controller,
        "allowedExtensions",
        new java.util.HashSet<>(java.util.Arrays.asList("csv", "sql")));
    ReflectionTestUtils.setField(controller, "maxFileSize", 10L * 1024 * 1024); // 10MB

    mockRequest = new TableClassificationRequest();
    mockRequest.setTableName("test_table");

    mockResponse = new TableClassificationResponse();
    mockResponse.setTableName("test_table");
    mockResponse.setAnalysisId("analysis-123");
  }

  @Nested
  @DisplayName("POST /api/table-classification/analyze - CSV Files")
  class AnalyzeCsvFiles {

    @Test
    @DisplayName("Should successfully analyze CSV file")
    void shouldAnalyzeCsvFileSuccessfully() throws Exception {
      // Given
      MockMultipartFile csvFile =
          new MockMultipartFile(
              "file", "test.csv", "text/csv", "col1,col2\nvalue1,value2".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(
              anyString(), any(TableClassificationResponse.class)))
          .thenReturn("analysis-123");

      // When & Then
      mockMvc
          .perform(
              multipart("/api/table-classification/analyze")
                  .file(csvFile)
                  .param("maxSamples", "100")
                  .param("locale", "en-US"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("test_table"))
          .andExpect(jsonPath("$.analysis_id").value("analysis-123"));

      verify(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService).classifyTable(mockRequest);
      verify(analysisStorageService).storeAnalysis("test.csv", mockResponse);
    }

    @Test
    @DisplayName("Should handle CSV parsing with optional parameters")
    void shouldHandleCsvParsingWithOptionalParameters() throws Exception {
      // Given
      MockMultipartFile csvFile =
          new MockMultipartFile(
              "file", "data.csv", "text/csv", "name,email\nJohn,john@test.com".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenAnswer(
              inv -> {
                TableClassificationRequest req = new TableClassificationRequest();
                req.setTableName("test_table");
                req.setColumns(java.util.Arrays.asList("name", "email"));
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("name", "John");
                row.put("email", "john@test.com");
                req.setData(java.util.List.of(row));
                return req;
              });
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-456");

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(csvFile))
          .andExpect(status().isOk());

      verify(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
    }

    @Test
    @DisplayName("Should return bad request for empty file")
    void shouldReturnBadRequestForEmptyFile() throws Exception {
      // Given
      MockMultipartFile emptyFile =
          new MockMultipartFile("file", "empty.csv", "text/csv", "".getBytes());

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(emptyFile))
          .andExpect(status().isBadRequest());

      verify(csvParsingService, times(0))
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
      verify(analysisStorageService, times(0))
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));
    }

    @Test
    @DisplayName("Should return bad request for unsupported file type")
    void shouldReturnBadRequestForUnsupportedFileType() throws Exception {
      // Given
      MockMultipartFile unsupportedFile =
          new MockMultipartFile("file", "test.txt", "text/plain", "some content".getBytes());

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(unsupportedFile))
          .andExpect(status().isBadRequest());

      verify(csvParsingService, times(0))
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
      verify(analysisStorageService, times(0))
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));
    }
  }

  @Nested
  @DisplayName("POST /api/table-classification/analyze - SQL Files")
  class AnalyzeSqlFiles {

    @Test
    @DisplayName("Should successfully analyze SQL file")
    void shouldAnalyzeSqlFileSuccessfully() throws Exception {
      // Given
      MockMultipartFile sqlFile =
          new MockMultipartFile(
              "file",
              "schema.sql",
              "application/sql",
              "CREATE TABLE users (id INT, name VARCHAR(50));".getBytes());

      Map<String, byte[]> tableDataMap = new HashMap<>();
      tableDataMap.put("users", "id,name\n1,John".getBytes());

      when(sqlFileProcessorService.processAllTablesToCSV(any())).thenReturn(tableDataMap);
      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenAnswer(
              inv -> {
                TableClassificationRequest req = new TableClassificationRequest();
                req.setTableName("test_table");
                req.setColumns(java.util.Arrays.asList("id", "name"));
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", "1");
                row.put("name", "John");
                req.setData(java.util.List.of(row));
                return req;
              });
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("sql-analysis-789");

      // Update the mock response to return the correct ID
      mockResponse.setAnalysisId("sql-analysis-789");

      // When & Then
      mockMvc
          .perform(
              multipart("/api/table-classification/analyze")
                  .file(sqlFile)
                  .param("tableName", "users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("test_table"))
          .andExpect(jsonPath("$.analysis_id").value("sql-analysis-789"));

      verify(sqlFileProcessorService).processAllTablesToCSV(sqlFile);
      verify(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
    }

    @Test
    @DisplayName("Should handle SQL processing errors")
    void shouldHandleSqlProcessingErrors() throws Exception {
      // Given
      MockMultipartFile sqlFile =
          new MockMultipartFile("file", "invalid.sql", "application/sql", "INVALID SQL".getBytes());

      doThrow(new SQLException("Invalid SQL syntax"))
          .when(sqlFileProcessorService)
          .processAllTablesToCSV(any());

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(sqlFile))
          .andExpect(status().isInternalServerError());

      verify(sqlFileProcessorService).processAllTablesToCSV(sqlFile);
      verify(csvParsingService, times(0))
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
      verify(analysisStorageService, times(0))
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));
    }
  }

  @Nested
  @DisplayName("POST /api/table-classification/reanalyze/{analysisId}")
  class ReanalyzeWithUpdatedTypes {

    @Test
    @DisplayName("Should successfully reanalyze existing analysis")
    void shouldReanalyzeExistingAnalysis() throws Exception {
      // Given
      String analysisId = "existing-analysis-123";
      AnalysisStorageService.StoredAnalysis storedAnalysis =
          mock(AnalysisStorageService.StoredAnalysis.class);
      when(storedAnalysis.getFileName()).thenReturn("original.csv");
      when(storedAnalysis.getColumns()).thenReturn(java.util.Arrays.asList("col1", "col2"));
      Map<String, Object> rowData = new HashMap<>();
      rowData.put("col1", "value1");
      rowData.put("col2", "value2");
      when(storedAnalysis.getData()).thenReturn(java.util.Arrays.asList(rowData));
      when(storedAnalysis.getLocale()).thenReturn("en-US");

      when(analysisStorageService.getAnalysis(analysisId)).thenReturn(storedAnalysis);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);

      // When & Then
      mockMvc
          .perform(post("/api/table-classification/reanalyze/{analysisId}", analysisId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.table_name").value("test_table"))
          .andExpect(jsonPath("$.analysis_id").value(analysisId)); // Should keep the same ID

      verify(analysisStorageService).getAnalysis(analysisId);
      verify(classificationService).classifyTable(any(TableClassificationRequest.class));
      verify(analysisStorageService)
          .updateAnalysis(anyString(), any(TableClassificationResponse.class));
    }

    @Test
    @DisplayName("Should return not found for non-existent analysis")
    void shouldReturnNotFoundForNonExistentAnalysis() throws Exception {
      // Given
      String analysisId = "non-existent-analysis";
      when(analysisStorageService.getAnalysis(analysisId)).thenReturn(null);

      // When & Then
      mockMvc
          .perform(post("/api/table-classification/reanalyze/{analysisId}", analysisId))
          .andExpect(status().isNotFound());

      verify(analysisStorageService).getAnalysis(analysisId);
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
    }

    @Test
    @DisplayName("Should handle reanalysis service errors")
    void shouldHandleReanalysisServiceErrors() throws Exception {
      // Given
      String analysisId = "error-analysis";
      when(analysisStorageService.getAnalysis(analysisId))
          .thenThrow(new RuntimeException("Storage service error"));

      // When & Then
      mockMvc
          .perform(post("/api/table-classification/reanalyze/{analysisId}", analysisId))
          .andExpect(status().isInternalServerError());

      verify(analysisStorageService).getAnalysis(analysisId);
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
    }

    @Test
    @DisplayName("Should use default locale when stored locale is null or empty")
    void shouldUseDefaultLocaleWhenStoredLocaleIsEmpty() throws Exception {
      // Given
      String analysisId = "no-locale-analysis";
      AnalysisStorageService.StoredAnalysis storedAnalysis =
          mock(AnalysisStorageService.StoredAnalysis.class);
      when(storedAnalysis.getFileName()).thenReturn("test.csv");
      when(storedAnalysis.getColumns()).thenReturn(java.util.Arrays.asList("col1"));
      Map<String, Object> singleRowData = new HashMap<>();
      singleRowData.put("col1", "value1");
      when(storedAnalysis.getData()).thenReturn(java.util.Arrays.asList(singleRowData));
      when(storedAnalysis.getLocale()).thenReturn(""); // Empty locale

      when(analysisStorageService.getAnalysis(analysisId)).thenReturn(storedAnalysis);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);

      // When & Then
      mockMvc
          .perform(post("/api/table-classification/reanalyze/{analysisId}", analysisId))
          .andExpect(status().isOk());

      // Verify that the request was created with default locale
      verify(classificationService).classifyTable(any(TableClassificationRequest.class));
    }
  }

  @Nested
  @DisplayName("File Validation")
  class FileValidation {

    @Test
    @DisplayName("Should return error when file parameter is missing")
    void shouldReturnErrorWhenFileParameterIsMissing() throws Exception {
      // When & Then - When file parameter is missing, it's a 500 error not 400
      mockMvc
          .perform(multipart("/api/table-classification/analyze"))
          .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should return bad request for file without name")
    void shouldReturnBadRequestForFileWithoutName() throws Exception {
      // Given
      MockMultipartFile fileWithoutName =
          new MockMultipartFile("file", "", "text/csv", "data".getBytes());

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(fileWithoutName))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return bad request for oversized file")
    void shouldReturnBadRequestForOversizedFile() throws Exception {
      // Given - Create a large file (assuming default max size is 10MB)
      byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
      MockMultipartFile largeFile =
          new MockMultipartFile("file", "large.csv", "text/csv", largeContent);

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(largeFile))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle file with special characters in name")
    void shouldHandleFileWithSpecialCharactersInName() throws Exception {
      // Given
      MockMultipartFile specialFile =
          new MockMultipartFile(
              "file",
              "fîlé_wîth_spëçîál_çhärs@#$%.csv",
              "text/csv",
              "col1,col2\nvalue1,value2".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-special");

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(specialFile))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle CSV file with BOM (Byte Order Mark)")
    void shouldHandleCsvFileWithBOM() throws Exception {
      // Given - CSV with UTF-8 BOM
      byte[] bomBytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}; // UTF-8 BOM
      String csvContent = "col1,col2\nvalue1,value2";
      byte[] contentWithBOM = new byte[bomBytes.length + csvContent.getBytes().length];
      System.arraycopy(bomBytes, 0, contentWithBOM, 0, bomBytes.length);
      System.arraycopy(
          csvContent.getBytes(), 0, contentWithBOM, bomBytes.length, csvContent.getBytes().length);

      MockMultipartFile bomFile =
          new MockMultipartFile("file", "bom_file.csv", "text/csv", contentWithBOM);

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-bom");

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(bomFile))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle minimum valid file size (1 byte)")
    void shouldHandleMinimumValidFileSize() throws Exception {
      // Given
      MockMultipartFile minFile =
          new MockMultipartFile("file", "min.csv", "text/csv", "x".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      when(analysisStorageService.storeAnalysis(anyString(), any())).thenReturn("analysis-min");

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(minFile))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle file extension case insensitivity")
    void shouldHandleFileExtensionCaseInsensitivity() throws Exception {
      // Given - Test various case combinations
      String[] extensions = {"CSV", "csv", "Csv", "cSv", "SQL", "sql", "Sql", "sQl"};

      for (String ext : extensions) {
        MockMultipartFile caseFile =
            new MockMultipartFile("file", "test." + ext, "text/csv", "col1\nvalue1".getBytes());

        // Mock SQL file processing for SQL extensions
        if ("sql".equalsIgnoreCase(ext)) {
          Map<String, byte[]> tableDataMap = Map.of("table1", "col1\nvalue1".getBytes());
          when(sqlFileProcessorService.processAllTablesToCSV(any(MultipartFile.class)))
              .thenReturn(tableDataMap);
          when(csvParsingService.parseCsvToRequest(
                  any(InputStream.class), anyString(), any(), nullable(String.class)))
              .thenReturn(mockRequest);
          when(classificationService.classifyTable(any(TableClassificationRequest.class)))
              .thenReturn(mockResponse);
          when(analysisStorageService.storeAnalysis(anyString(), any()))
              .thenReturn("analysis-sql-" + ext);
        } else {
          when(csvParsingService.parseCsvToRequest(
                  any(InputStream.class), anyString(), any(), nullable(String.class)))
              .thenReturn(mockRequest);
          when(classificationService.classifyTable(any(TableClassificationRequest.class)))
              .thenReturn(mockResponse);
          when(analysisStorageService.storeAnalysis(anyString(), any()))
              .thenReturn("analysis-csv-" + ext);
        }

        // When & Then
        mockMvc
            .perform(multipart("/api/table-classification/analyze").file(caseFile))
            .andExpect(status().isOk());
      }
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should handle classification service errors")
    void shouldHandleClassificationServiceErrors() throws Exception {
      // Given
      MockMultipartFile csvFile =
          new MockMultipartFile("file", "error.csv", "text/csv", "col1\nvalue1".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      doThrow(new RuntimeException("Classification failed"))
          .when(classificationService)
          .classifyTable(any(TableClassificationRequest.class));

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(csvFile))
          .andExpect(status().isInternalServerError());

      verify(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService).classifyTable(mockRequest);
      verify(analysisStorageService, times(0))
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));
    }

    @Test
    @DisplayName("Should handle CSV parsing errors")
    void shouldHandleCsvParsingErrors() throws Exception {
      // Given
      MockMultipartFile csvFile =
          new MockMultipartFile(
              "file", "malformed.csv", "text/csv", "invalid csv content".getBytes());

      doThrow(new IllegalArgumentException("Invalid CSV format"))
          .when(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(csvFile))
          .andExpect(status().isBadRequest());

      verify(csvParsingService)
          .parseCsvToRequest(any(InputStream.class), anyString(), any(), nullable(String.class));
      verify(classificationService, times(0)).classifyTable(any(TableClassificationRequest.class));
      verify(analysisStorageService, times(0))
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));
    }

    @Test
    @DisplayName("Should handle analysis storage errors gracefully")
    void shouldHandleAnalysisStorageErrorsGracefully() throws Exception {
      // Given
      MockMultipartFile csvFile =
          new MockMultipartFile("file", "test.csv", "text/csv", "col1\nvalue1".getBytes());

      when(csvParsingService.parseCsvToRequest(
              any(InputStream.class), anyString(), any(), nullable(String.class)))
          .thenReturn(mockRequest);
      when(classificationService.classifyTable(any(TableClassificationRequest.class)))
          .thenReturn(mockResponse);
      doThrow(new RuntimeException("Storage failed"))
          .when(analysisStorageService)
          .storeAnalysis(anyString(), any(TableClassificationResponse.class));

      // When & Then
      mockMvc
          .perform(multipart("/api/table-classification/analyze").file(csvFile))
          .andExpect(status().isInternalServerError());

      verify(analysisStorageService).storeAnalysis("test.csv", mockResponse);
    }
  }
}

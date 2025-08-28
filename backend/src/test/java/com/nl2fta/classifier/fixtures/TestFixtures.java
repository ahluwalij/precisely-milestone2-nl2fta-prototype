package com.nl2fta.classifier.fixtures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nl2fta.classifier.dto.AwsCredentialsRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ColumnClassification;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ProcessingMetadata;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.ShapeDetail;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse.Statistics;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GenerateValidatedExamplesRequest;
import com.nl2fta.classifier.dto.semantic_type.GeneratedSemanticType;
import com.nl2fta.classifier.dto.semantic_type.GeneratedValidatedExamplesResponse;
import com.nl2fta.classifier.dto.semantic_type.SemanticTypeGenerationRequest;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

/**
 * Comprehensive test fixtures for all unit tests. Contains sample data objects for all DTOs and
 * common test scenarios.
 */
public final class TestFixtures {

  private TestFixtures() {}

  // ========================================
  // TABLE CLASSIFICATION FIXTURES
  // ========================================

  public static TableClassificationRequest createValidTableClassificationRequest() {
    return TableClassificationRequest.builder()
        .tableName("test_users")
        .columns(
            new ArrayList<>(
                Arrays.asList("id", "first_name", "last_name", "email", "age", "phone", "zipcode")))
        .data(createSampleTableData())
        .maxSamples(100)
        .locale("en-US")
        .includeStatistics(true)
        .build();
  }

  public static TableClassificationRequest createMinimalTableClassificationRequest() {
    return TableClassificationRequest.builder()
        .tableName("minimal_table")
        .columns(new ArrayList<>(Arrays.asList("id", "name")))
        .data(createMinimalTableData())
        .includeStatistics(false)
        .build();
  }

  public static List<Map<String, Object>> createSampleTableData() {
    List<Map<String, Object>> data = new ArrayList<>();

    data.add(createRow("1001", "John", "Doe", "john.doe@example.com", 25, "555-0123", "12345"));
    data.add(createRow("1002", "Jane", "Smith", "jane.smith@example.com", 30, "555-0456", "67890"));
    data.add(
        createRow("1003", "Bob", "Johnson", "bob.johnson@example.com", 35, "555-0789", "54321"));
    data.add(
        createRow("1004", "Alice", "Brown", "alice.brown@example.com", 28, "555-0012", "09876"));
    data.add(
        createRow(
            "1005", "Charlie", "Davis", "charlie.davis@example.com", 42, "555-0345", "13579"));

    return data;
  }

  public static List<Map<String, Object>> createMinimalTableData() {
    List<Map<String, Object>> data = new ArrayList<>();

    Map<String, Object> row1 = new HashMap<>();
    row1.put("id", "1");
    row1.put("name", "Test");
    data.add(row1);

    Map<String, Object> row2 = new HashMap<>();
    row2.put("id", "2");
    row2.put("name", "User");
    data.add(row2);

    return data;
  }

  private static Map<String, Object> createRow(
      String id,
      String firstName,
      String lastName,
      String email,
      int age,
      String phone,
      String zipcode) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", id);
    row.put("first_name", firstName);
    row.put("last_name", lastName);
    row.put("email", email);
    row.put("age", age);
    row.put("phone", phone);
    row.put("zipcode", zipcode);
    return row;
  }

  public static TableClassificationResponse createSampleTableClassificationResponse() {
    Map<String, ColumnClassification> columnClassifications = new HashMap<>();

    columnClassifications.put("id", createColumnClassification("id", "LONG", "IDENTIFIER", 1.0));
    columnClassifications.put(
        "first_name", createColumnClassification("first_name", "STRING", "NAME.FIRST", 0.95));
    columnClassifications.put(
        "last_name", createColumnClassification("last_name", "STRING", "NAME.LAST", 0.95));
    columnClassifications.put("email", createColumnClassification("email", "STRING", "EMAIL", 1.0));
    columnClassifications.put("age", createColumnClassification("age", "LONG", "AGE", 0.90));
    columnClassifications.put(
        "phone", createColumnClassification("phone", "STRING", "TELEPHONE", 0.85));
    columnClassifications.put(
        "zipcode", createColumnClassification("zipcode", "STRING", "POSTAL_CODE.ZIP5", 0.80));

    return TableClassificationResponse.builder()
        .tableName("test_users")
        .columnClassifications(columnClassifications)
        .processingMetadata(createProcessingMetadata())
        .analysisId("test-analysis-123")
        .build();
  }

  private static ColumnClassification createColumnClassification(
      String columnName, String baseType, String semanticType, double confidence) {
    return ColumnClassification.builder()
        .columnName(columnName)
        .baseType(baseType)
        .semanticType(semanticType)
        .confidence(confidence)
        .pattern(generatePatternForType(semanticType))
        .description("Test description for " + columnName)
        .statistics(createStatistics())
        .shapeDetails(createShapeDetails())
        .build();
  }

  private static String generatePatternForType(String semanticType) {
    switch (semanticType) {
      case "EMAIL":
        return "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
      case "IDENTIFIER":
        return "\\d+";
      case "NAME.FIRST":
      case "NAME.LAST":
        return "[A-Z][a-z]+";
      case "TELEPHONE":
        return "\\d{3}-\\d{4}";
      case "POSTAL_CODE.ZIP5":
        return "\\d{5}";
      default:
        return ".*";
    }
  }

  private static Statistics createStatistics() {
    return Statistics.builder()
        .sampleCount(5L)
        .nullCount(0L)
        .blankCount(0L)
        .distinctCount(5L)
        .minValue("1001")
        .maxValue("1005")
        .minLength(4)
        .maxLength(25)
        .mean(10.5)
        .standardDeviation(2.3)
        .cardinality(1)
        .uniqueness(1.0)
        .build();
  }

  private static List<ShapeDetail> createShapeDetails() {
    return Arrays.asList(
        ShapeDetail.builder()
            .shape("\\d{4}")
            .count(5L)
            .examples(Arrays.asList("1001", "1002", "1003"))
            .build());
  }

  private static ProcessingMetadata createProcessingMetadata() {
    return ProcessingMetadata.builder()
        .totalColumns(7)
        .analyzedColumns(7)
        .totalRowsProcessed(5)
        .processingTimeMs(150L)
        .ftaVersion("16.0.3")
        .localeUsed("en-US")
        .build();
  }

  // ========================================
  // AWS CREDENTIALS FIXTURES
  // ========================================

  public static AwsCredentialsRequest createValidAwsCredentialsRequest() {
    return AwsCredentialsRequest.builder()
        .accessKeyId("AKIATESTFAKEKEY123456")
        .secretAccessKey("test-fake-secret-key-for-unit-testing-purposes-only")
        .region("us-east-1")
        .build();
  }

  public static AwsCredentialsRequest createInvalidAwsCredentialsRequest() {
    return AwsCredentialsRequest.builder()
        .accessKeyId("INVALID_ACCESS_KEY")
        .secretAccessKey("INVALID_SECRET_KEY")
        .region("us-east-1")
        .build();
  }

  // ========================================
  // SEMANTIC TYPE GENERATION FIXTURES
  // ========================================

  public static SemanticTypeGenerationRequest createValidSemanticTypeGenerationRequest() {
    return SemanticTypeGenerationRequest.builder()
        .typeName("EMPLOYEE_ID")
        .description("Employee identification number format")
        .positiveContentExamples(Arrays.asList("EMP001", "EMP002", "EMP999"))
        .negativeContentExamples(Arrays.asList("123", "ABC", "emp001"))
        .positiveHeaderExamples(Arrays.asList("employee_id", "emp_id", "employee_number"))
        .negativeHeaderExamples(Arrays.asList("name", "email", "phone"))
        .checkExistingTypes(true)
        .proceedDespiteSimilarity(false)
        .build();
  }

  public static SemanticTypeGenerationRequest createExamplesOnlyRequest() {
    return SemanticTypeGenerationRequest.builder()
        .typeName("EXAMPLES_ONLY_TEST")
        .description("EXAMPLES GENERATION ONLY: Generate more examples")
        .positiveContentExamples(Arrays.asList("ABC123", "DEF456"))
        .build();
  }

  public static GeneratedSemanticType createGeneratedSemanticType() {
    return GeneratedSemanticType.builder()
        .resultType("generated")
        .semanticType("EMPLOYEE_ID")
        .description("Employee identification number format")
        .pluginType("regex")
        .regexPattern("EMP\\d{3}")
        .positiveContentExamples(Arrays.asList("EMP001", "EMP002", "EMP999"))
        .negativeContentExamples(Arrays.asList("123", "ABC", "emp001"))
        .positiveHeaderExamples(Arrays.asList("employee_id", "emp_id", "employee_number"))
        .negativeHeaderExamples(Arrays.asList("name", "email", "phone"))
        .confidenceThreshold(0.80)
        .explanation("Generated semantic type for employee identification")
        .build();
  }

  public static GenerateValidatedExamplesRequest createValidatedExamplesRequest() {
    return GenerateValidatedExamplesRequest.builder()
        .semanticTypeName("EMPLOYEE_ID")
        .regexPattern("EMP\\d{3}")
        .existingPositiveExamples(Arrays.asList("EMP001", "EMP002"))
        .existingNegativeExamples(Arrays.asList("123", "ABC"))
        .userDescription("Need more examples with different patterns")
        .isHeaderPatternImprovement(false)
        .build();
  }

  public static GeneratedValidatedExamplesResponse createValidatedExamplesResponse() {
    return GeneratedValidatedExamplesResponse.builder()
        .positiveExamples(Arrays.asList("EMP003", "EMP004", "EMP005"))
        .negativeExamples(Arrays.asList("456", "XYZ", "emp003"))
        .validationSuccessful(true)
        .attemptsUsed(1)
        .rationale("Generated additional examples following the EMP### pattern")
        .validationSummary(createValidationSummary())
        .build();
  }

  private static GeneratedValidatedExamplesResponse.ValidationSummary createValidationSummary() {
    return GeneratedValidatedExamplesResponse.ValidationSummary.builder()
        .totalPositiveGenerated(3)
        .totalNegativeGenerated(3)
        .positiveExamplesValidated(3)
        .negativeExamplesValidated(3)
        .positiveExamplesFailed(0)
        .negativeExamplesFailed(0)
        .build();
  }

  // ========================================
  // CUSTOM SEMANTIC TYPE FIXTURES
  // ========================================

  public static CustomSemanticType createValidCustomSemanticType() {
    CustomSemanticType.LocaleConfig localeConfig = new CustomSemanticType.LocaleConfig();
    localeConfig.setLocaleTag("en-US");

    CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
    matchEntry.setRegExpReturned("EMP\\d{3}");

    localeConfig.setMatchEntries(Arrays.asList(matchEntry));

    CustomSemanticType.HeaderRegExp headerRegExp = new CustomSemanticType.HeaderRegExp();
    headerRegExp.setRegExp("(?i)employee.?id");

    localeConfig.setHeaderRegExps(Arrays.asList(headerRegExp));

    CustomSemanticType customType = new CustomSemanticType();
    customType.setSemanticType("EMPLOYEE_ID");
    customType.setPluginType("regex");
    customType.setDescription("Employee identification number");
    customType.setValidLocales(Arrays.asList(localeConfig));

    return customType;
  }

  public static CustomSemanticType createListTypeCustomSemanticType() {
    CustomSemanticType customType = new CustomSemanticType();
    customType.setSemanticType("USER_STATUS");
    customType.setPluginType("list");
    customType.setDescription("User account status");

    return customType;
  }

  public static CustomSemanticType createInvalidCustomSemanticType() {
    CustomSemanticType customType = new CustomSemanticType();
    // Missing required fields - semanticType is null
    customType.setPluginType("regex");
    customType.setDescription("Invalid type");
    return customType;
  }

  // ========================================
  // FILE PROCESSING FIXTURES
  // ========================================

  public static byte[] createValidCsvContent() {
    String csvContent =
        "id,first_name,last_name,email,age\n"
            + "1001,John,Doe,john.doe@example.com,25\n"
            + "1002,Jane,Smith,jane.smith@example.com,30\n"
            + "1003,Bob,Johnson,bob.johnson@example.com,35\n";
    return csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  public static byte[] createEmptyCsvContent() {
    return "".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  public static byte[] createInvalidCsvContent() {
    return "This is not a valid CSV file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  public static byte[] createCsvWithMissingHeaders() {
    // Empty CSV content - no headers, no data
    return "".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // ========================================
  // STORAGE FIXTURES
  // ========================================

  public static AnalysisStorageService.StoredAnalysis createStoredAnalysis() {
    AnalysisStorageService.StoredAnalysis analysis = new AnalysisStorageService.StoredAnalysis();
    analysis.setAnalysisId("test-analysis-123");
    analysis.setFileName("test_users.csv");
    analysis.setTimestamp(java.time.LocalDateTime.now());
    analysis.setColumns(Arrays.asList("id", "first_name", "email"));
    analysis.setData(createMinimalTableData());
    analysis.setLocale("en-US");
    return analysis;
  }

  public static List<AnalysisStorageService.StoredAnalysis> createStoredAnalysesList() {
    AnalysisStorageService.StoredAnalysis secondAnalysis =
        new AnalysisStorageService.StoredAnalysis();
    secondAnalysis.setAnalysisId("test-analysis-456");
    secondAnalysis.setFileName("test_products.csv");
    secondAnalysis.setTimestamp(java.time.LocalDateTime.now().minusHours(1));
    secondAnalysis.setColumns(Arrays.asList("product_id", "name", "price"));
    List<Map<String, Object>> secondAnalysisData = new ArrayList<>();

    Map<String, Object> product1 = new HashMap<>();
    product1.put("product_id", "P001");
    product1.put("name", "Widget");
    product1.put("price", "19.99");
    secondAnalysisData.add(product1);

    Map<String, Object> product2 = new HashMap<>();
    product2.put("product_id", "P002");
    product2.put("name", "Gadget");
    product2.put("price", "29.99");
    secondAnalysisData.add(product2);

    secondAnalysis.setData(secondAnalysisData);
    secondAnalysis.setLocale("en-US");

    return Arrays.asList(createStoredAnalysis(), secondAnalysis);
  }

  // ========================================
  // ERROR SCENARIOS
  // ========================================

  public static RuntimeException createTableClassificationException() {
    return new RuntimeException("Failed to classify table: FTA analysis error");
  }

  public static IllegalArgumentException createValidationException() {
    return new IllegalArgumentException("Invalid request parameters");
  }

  public static RuntimeException createAwsException() {
    return new RuntimeException("AWS Bedrock service unavailable");
  }

  // ========================================
  // MOCK RESPONSES
  // ========================================

  public static String createClaudeResponse() {
    return """
            {
                "semantic_type": "EMPLOYEE_ID",
                "description": "Employee identification number format",
                "plugin_type": "regex",
                "regex_pattern": "EMP\\\\d{3}",
                "positive_content_examples": ["EMP001", "EMP002", "EMP999"],
                "negative_content_examples": ["123", "ABC", "emp001"],
                "positive_header_examples": ["employee_id", "emp_id", "employee_number"],
                "negative_header_examples": ["name", "email", "phone"],
                "confidence_threshold": 0.80,
                "explanation": "Generated semantic type for employee identification"
            }
            """;
  }

  public static String createInvalidClaudeResponse() {
    return "This is not a valid JSON response from Claude";
  }

  // ========================================
  // EMPLOYEE DATA FIXTURES
  // ========================================

  public static List<Map<String, Object>> createEmployeeData() {
    List<Map<String, Object>> data = new ArrayList<>();

    data.add(createEmployeeRow("E10001F", "Alice", "Wong"));
    data.add(createEmployeeRow("E10002P", "David", "Martinez"));
    data.add(createEmployeeRow("E10003F", "Sophia", "Lee"));

    return data;
  }

  private static Map<String, Object> createEmployeeRow(
      String id, String firstName, String lastName) {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", id);
    row.put("FN", firstName);
    row.put("LN", lastName);
    return row;
  }

  public static TableClassificationRequest createEmployeeTableClassificationRequest() {
    return TableClassificationRequest.builder()
        .tableName("employees")
        .columns(new ArrayList<>(Arrays.asList("ID", "FN", "LN")))
        .data(createEmployeeData())
        .maxSamples(100)
        .locale("en-US")
        .includeStatistics(true)
        .build();
  }

  public static SemanticTypeGenerationRequest createEmployeeIdGenerationRequest() {
    return SemanticTypeGenerationRequest.builder()
        .typeName("EMPLOYEE_ID")
        .description(
            "EmployeeID type definition: Values start with E, followed by 5 digits, then P for part time and F for full time.")
        .positiveContentExamples(Arrays.asList("E10001F", "E10002P", "E10003F"))
        .negativeContentExamples(Arrays.asList("123", "ABC123", "E123F", "E10001X"))
        .positiveHeaderExamples(Arrays.asList("ID", "employee_id", "emp_id"))
        .negativeHeaderExamples(Arrays.asList("first_name", "last_name", "email"))
        .checkExistingTypes(true)
        .proceedDespiteSimilarity(false)
        .build();
  }

  // ========================================
  // LARGE DATA FIXTURES
  // ========================================

  public static List<Map<String, Object>> createLargeDataset(int rowCount) {
    List<Map<String, Object>> data = new ArrayList<>();
    for (int i = 1; i <= rowCount; i++) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", String.valueOf(1000 + i));
      row.put("name", "User" + i);
      row.put("value", "Value" + i);
      data.add(row);
    }
    return data;
  }

  public static TableClassificationRequest createLargeTableRequest(int rowCount) {
    return TableClassificationRequest.builder()
        .tableName("large_test_table")
        .columns(new ArrayList<>(Arrays.asList("id", "name", "value")))
        .data(createLargeDataset(rowCount))
        .maxSamples(100)
        .locale("en-US")
        .includeStatistics(true)
        .build();
  }
}

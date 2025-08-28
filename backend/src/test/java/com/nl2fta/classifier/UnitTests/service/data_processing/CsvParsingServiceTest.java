package com.nl2fta.classifier.service.data_processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;

class CsvParsingServiceTest {

  private CsvParsingService csvParsingService;

  @BeforeEach
  void setUp() {
    csvParsingService = new CsvParsingService();
    ReflectionTestUtils.setField(csvParsingService, "defaultLocale", "en-US");
  }

  @Test
  void shouldParseCsvWithValidDataAndHeaders() throws Exception {
    String csvContent = "name,age,email\nJohn,25,john@example.com\nJane,30,jane@example.com";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "users.csv", null, "en-US");

    assertThat(result.getTableName()).isEqualTo("users");
    assertThat(result.getColumns()).containsExactly("name", "age", "email");
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getLocale()).isEqualTo("en-US");
    assertThat(result.getIncludeStatistics()).isTrue();

    Map<String, Object> firstRow = result.getData().get(0);
    assertThat(firstRow.get("name")).isEqualTo("John");
    assertThat(firstRow.get("age")).isEqualTo("25");
    assertThat(firstRow.get("email")).isEqualTo("john@example.com");
  }

  @Test
  void shouldRespectMaxSamplesLimit() throws Exception {
    String csvContent = "id,value\n1,A\n2,B\n3,C\n4,D\n5,E";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "data.csv", 3, "en-US");

    assertThat(result.getData()).hasSize(3);
    assertThat(result.getMaxSamples()).isEqualTo(3);
  }

  @Test
  void shouldUseDefaultLocaleWhenNullProvided() throws Exception {
    String csvContent = "col1\nvalue1";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "test.csv", null, null);

    assertThat(result.getLocale()).isEqualTo("en-US");
  }

  @Test
  void shouldExtractTableNameFromFileName() throws Exception {
    String csvContent = "col1\nvalue1";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "customer_data.csv", null, "en-US");

    assertThat(result.getTableName()).isEqualTo("customer_data");
  }

  @Test
  void shouldHandleFileNameWithoutExtension() throws Exception {
    String csvContent = "col1\nvalue1";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "tablename", null, "en-US");

    assertThat(result.getTableName()).isEqualTo("tablename");
  }

  @Test
  void shouldHandleNullOrEmptyFileName() throws Exception {
    String csvContent = "col1\nvalue1";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result1 =
        csvParsingService.parseCsvToRequest(csvData, null, null, "en-US");
    TableClassificationRequest result2 =
        csvParsingService.parseCsvToRequest(csvData, "", null, "en-US");

    assertThat(result1.getTableName()).isEqualTo("unnamed_table");
    assertThat(result2.getTableName()).isEqualTo("unnamed_table");
  }

  @Test
  void shouldSkipRowsWithIncorrectColumnCount() throws Exception {
    String csvContent = "col1,col2,col3\nA,B,C\nD,E\nF,G,H";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US");

    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().get(0).get("col1")).isEqualTo("A");
    assertThat(result.getData().get(1).get("col1")).isEqualTo("F");
  }

  @Test
  void shouldHandleEmptyValues() throws Exception {
    String csvContent = "name,value\nJohn,\n,100\nJane,50";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US");

    assertThat(result.getData()).hasSize(3);
    assertThat(result.getData().get(0).get("value")).isEqualTo("");
    assertThat(result.getData().get(1).get("name")).isEqualTo("");
  }

  @Test
  void shouldThrowExceptionForEmptyHeadersOnly() {
    byte[] csvData = "\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () -> csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSV file contains no data");
  }

  @Test
  void shouldThrowExceptionForNoDataRows() {
    String csvContent = "col1,col2,col3";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () -> csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSV file contains no data");
  }

  @Test
  void shouldThrowExceptionForCompletelyEmptyFile() {
    byte[] csvData = "".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () -> csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSV file has no headers");
  }

  @Test
  void shouldThrowExceptionForNullHeaders() {
    // Test case where readNext() returns null immediately
    byte[] csvData = new byte[0];

    assertThatThrownBy(
            () -> csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CSV file has no headers");
  }

  @Test
  void shouldHandleSpecialCharactersInData() throws Exception {
    String csvContent =
        "name,description\n\"John, Jr.\",\"Data with \"\"quotes\"\" and commas\"\n\"Jane\",\"Unicode: αβγ 中文\"";
    byte[] csvData = csvContent.getBytes(StandardCharsets.UTF_8);

    TableClassificationRequest result =
        csvParsingService.parseCsvToRequest(csvData, "test.csv", null, "en-US");

    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().get(0).get("name")).isEqualTo("John, Jr.");
    assertThat(result.getData().get(0).get("description"))
        .isEqualTo("Data with \"quotes\" and commas");
    assertThat(result.getData().get(1).get("description")).isEqualTo("Unicode: αβγ 中文");
  }
}

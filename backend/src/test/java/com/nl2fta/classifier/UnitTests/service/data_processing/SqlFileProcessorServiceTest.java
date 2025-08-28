package com.nl2fta.classifier.service.data_processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("SQL File Processor Service Tests")
class SqlFileProcessorServiceTest {

  @Mock private MultipartFile mockSqlFile;

  private SqlFileProcessorService sqlFileProcessorService;

  @BeforeEach
  void setUp() {
    sqlFileProcessorService = new SqlFileProcessorService();
  }

  @Nested
  @DisplayName("Process All Tables to CSV")
  class ProcessAllTablesToCsvTests {

    @Test
    @DisplayName("Should process SQL file with single table and return CSV data")
    void shouldProcessSqlFileWithSingleTable() throws Exception {
      // Given - Use simpler SQL that H2 can handle consistently
      String sqlScript =
          """
                CREATE TABLE test_users (id INT, name VARCHAR(50), email VARCHAR(100));
                INSERT INTO test_users VALUES (1, 'John Doe', 'john@example.com');
                INSERT INTO test_users VALUES (2, 'Jane Smith', 'jane@example.com');
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(sqlScript.getBytes(StandardCharsets.UTF_8)));

      // When
      Map<String, byte[]> result = sqlFileProcessorService.processAllTablesToCSV(mockSqlFile);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result).containsKey("test_users");

      String csvContent = new String(result.get("test_users"), StandardCharsets.UTF_8);
      assertThat(csvContent).contains("id");
      assertThat(csvContent).contains("name");
      assertThat(csvContent).contains("email");
      assertThat(csvContent).contains("John Doe");
      assertThat(csvContent).contains("Jane Smith");
    }

    @Test
    @DisplayName("Should process SQL file with multiple tables")
    void shouldProcessSqlFileWithMultipleTables() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE products (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10,2)
                );

                CREATE TABLE categories (
                    id INT PRIMARY KEY,
                    category_name VARCHAR(50)
                );

                INSERT INTO products VALUES (1, 'Laptop', 999.99);
                INSERT INTO categories VALUES (1, 'Electronics');
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(sqlScript.getBytes(StandardCharsets.UTF_8)));

      // When
      Map<String, byte[]> result = sqlFileProcessorService.processAllTablesToCSV(mockSqlFile);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.keySet()).containsExactlyInAnyOrder("products", "categories");

      String productsContent = new String(result.get("products"), StandardCharsets.UTF_8);
      assertThat(productsContent).contains("Laptop");
      assertThat(productsContent).contains("999.99");

      String categoriesContent = new String(result.get("categories"), StandardCharsets.UTF_8);
      assertThat(categoriesContent).contains("Electronics");
    }

    @Test
    @DisplayName("Should handle empty table")
    void shouldHandleEmptyTable() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE empty_table (
                    id INT PRIMARY KEY,
                    name VARCHAR(50)
                );
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(sqlScript.getBytes(StandardCharsets.UTF_8)));

      // When
      Map<String, byte[]> result = sqlFileProcessorService.processAllTablesToCSV(mockSqlFile);

      // Then
      assertThat(result).hasSize(1);
      String csvContent = new String(result.get("empty_table"), StandardCharsets.UTF_8);
      assertThat(csvContent).contains("id");
      assertThat(csvContent).contains("name");
      // Should only contain headers, no data rows
      String[] lines = csvContent.trim().split("\n");
      assertThat(lines).hasSize(1);
    }

    @Test
    @DisplayName("Should throw exception when no user tables found")
    void shouldThrowExceptionWhenNoUserTablesFound() throws Exception {
      // Given
      String sqlScript =
          """
                -- Just comments
                SET FOREIGN_KEY_CHECKS = 0;
                USE test_db;
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(sqlScript.getBytes(StandardCharsets.UTF_8)));

      // When & Then
      assertThatThrownBy(() -> sqlFileProcessorService.processAllTablesToCSV(mockSqlFile))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No user tables found in SQL file");
    }

    @Test
    @DisplayName("Should handle table with NULL values")
    void shouldHandleTableWithNullValues() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE nullable_table (
                    id INT PRIMARY KEY,
                    optional_field VARCHAR(50),
                    nullable_number INT
                );

                INSERT INTO nullable_table VALUES (1, 'Present', 42);
                INSERT INTO nullable_table VALUES (2, NULL, NULL);
                INSERT INTO nullable_table VALUES (3, 'Also Present', NULL);
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(sqlScript.getBytes(StandardCharsets.UTF_8)));

      // When
      Map<String, byte[]> result = sqlFileProcessorService.processAllTablesToCSV(mockSqlFile);

      // Then
      assertThat(result).hasSize(1);
      String csvContent = new String(result.get("nullable_table"), StandardCharsets.UTF_8);
      assertThat(csvContent).contains("Present");
      assertThat(csvContent).contains("Also Present");
      assertThat(csvContent).contains("42");
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle IOException from MultipartFile")
    void shouldHandleIOExceptionFromMultipartFile() throws Exception {
      // Given
      when(mockSqlFile.getInputStream()).thenThrow(new IOException("File read error"));

      // When & Then
      assertThatThrownBy(() -> sqlFileProcessorService.processAllTablesToCSV(mockSqlFile))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("File read error");
    }

    @Test
    @DisplayName("Should handle malformed SQL gracefully")
    void shouldHandleMalformedSqlGracefully() throws Exception {
      // Given
      String malformedSql =
          """
                CREATE TABLE test (
                    id INT PRIMARY KEY,
                    name VARCHAR(50)
                );

                INSERT INTO test VALUES (1, 'Valid');
                INSERT INTO nonexistent_table VALUES (2, 'Invalid');
                INSERT INTO test VALUES (3, 'Another Valid');
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(malformedSql.getBytes(StandardCharsets.UTF_8)));

      // When - Should continue processing despite some errors
      Map<String, byte[]> result = sqlFileProcessorService.processAllTablesToCSV(mockSqlFile);

      // Then - Should still return valid table with successful inserts
      assertThat(result).hasSize(1);
      String csvContent = new String(result.get("test"), StandardCharsets.UTF_8);
      assertThat(csvContent).contains("Valid");
      assertThat(csvContent).contains("Another Valid");
    }

    @Test
    @DisplayName("Should handle completely invalid SQL")
    void shouldHandleCompletelyInvalidSQL() throws Exception {
      // Given
      String invalidSql =
          """
                INVALID SQL STATEMENT;
                ANOTHER INVALID STATEMENT;
                """;

      when(mockSqlFile.getInputStream())
          .thenReturn(new ByteArrayInputStream(invalidSql.getBytes(StandardCharsets.UTF_8)));

      // When & Then - Should throw SQLException for completely invalid SQL
      assertThatThrownBy(() -> sqlFileProcessorService.processAllTablesToCSV(mockSqlFile))
          .isInstanceOf(SQLException.class);
    }
  }
}

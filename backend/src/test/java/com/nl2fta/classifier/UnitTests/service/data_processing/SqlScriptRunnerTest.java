package com.nl2fta.classifier.service.data_processing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SQL Script Runner Tests")
class SqlScriptRunnerTest {

  @Mock private Connection connection;

  @Mock private Statement statement;

  private SqlScriptRunner sqlScriptRunner;

  @BeforeEach
  void setUp() throws SQLException {
    lenient().when(connection.createStatement()).thenReturn(statement);
    sqlScriptRunner = new SqlScriptRunner(connection);
  }

  @Nested
  @DisplayName("Basic SQL Execution")
  class BasicSqlExecutionTests {

    @Test
    @DisplayName("Should execute simple SQL statements successfully")
    void shouldExecuteSimpleSqlStatements() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE users (id INT, name VARCHAR(50));
                INSERT INTO users VALUES (1, 'John');
                INSERT INTO users VALUES (2, 'Jane');
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(3)).execute(anyString());
      verify(statement).execute("CREATE TABLE users (id INT, name VARCHAR(50))");
      verify(statement).execute("INSERT INTO users VALUES (1, 'John')");
      verify(statement).execute("INSERT INTO users VALUES (2, 'Jane')");
    }

    @Test
    @DisplayName("Should handle InputStream input")
    void shouldHandleInputStreamInput() throws Exception {
      // Given
      String sqlScript = "CREATE TABLE test (id INT);";
      ByteArrayInputStream inputStream = new ByteArrayInputStream(sqlScript.getBytes());

      // When
      sqlScriptRunner.runScript(inputStream);

      // Then
      verify(statement).execute("CREATE TABLE test (id INT)");
    }

    @Test
    @DisplayName("Should handle empty script gracefully")
    void shouldHandleEmptyScript() throws Exception {
      // Given
      String emptyScript = "";

      // When & Then
      assertDoesNotThrow(() -> sqlScriptRunner.runScript(new StringReader(emptyScript)));

      verify(statement, never()).execute(anyString());
    }

    @Test
    @DisplayName("Should handle script with only whitespace and empty lines")
    void shouldHandleWhitespaceOnlyScript() throws Exception {
      // Given
      String whitespaceScript = """




                """;

      // When & Then
      assertDoesNotThrow(() -> sqlScriptRunner.runScript(new StringReader(whitespaceScript)));

      verify(statement, never()).execute(anyString());
    }
  }

  @Nested
  @DisplayName("Comment Handling")
  class CommentHandlingTests {

    @Test
    @DisplayName("Should skip single-line comments with --")
    void shouldSkipSingleLineCommentsWithDashes() throws Exception {
      // Given
      String sqlScript =
          """
                -- This is a comment
                CREATE TABLE users (id INT);
                -- Another comment
                INSERT INTO users VALUES (1);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE users (id INT)");
      verify(statement).execute("INSERT INTO users VALUES (1)");
    }

    @Test
    @DisplayName("Should skip single-line comments with #")
    void shouldSkipSingleLineCommentsWithHash() throws Exception {
      // Given
      String sqlScript =
          """
                # MySQL style comment
                CREATE TABLE test (id INT);
                # Another MySQL comment
                DROP TABLE test;
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement).execute("DROP TABLE test");
    }

    @Test
    @DisplayName("Should skip single-line multi-line comments")
    void shouldSkipSingleLineMultiLineComments() throws Exception {
      // Given
      String sqlScript =
          """
                /* This is a single line comment */
                CREATE TABLE test (id INT);
                /* Another single line comment */
                INSERT INTO test VALUES (1);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement).execute("INSERT INTO test VALUES (1)");
    }

    @Test
    @DisplayName("Should skip multi-line comments")
    void shouldSkipMultiLineComments() throws Exception {
      // Given
      String sqlScript =
          """
                /*
                This is a multi-line comment
                that spans several lines
                */
                CREATE TABLE test (id INT);
                /*
                Another multi-line
                comment
                */
                INSERT INTO test VALUES (1);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement).execute("INSERT INTO test VALUES (1)");
    }
  }

  @Nested
  @DisplayName("Delimiter Handling")
  class DelimiterHandlingTests {

    @Test
    @DisplayName("Should handle custom delimiters")
    void shouldHandleCustomDelimiters() throws Exception {
      // Given
      String sqlScript =
          """
                DELIMITER $$
                CREATE TABLE test (id INT)$$
                INSERT INTO test VALUES (1)$$
                DELIMITER ;
                DROP TABLE test;
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(3)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement).execute("INSERT INTO test VALUES (1)");
      verify(statement).execute("DROP TABLE test");
    }

    @Test
    @DisplayName("Should handle delimiter changes mid-script")
    void shouldHandleDelimiterChangesMidScript() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE test1 (id INT);
                DELIMITER //
                CREATE TABLE test2 (id INT)//
                INSERT INTO test2 VALUES (1)//
                DELIMITER ;
                INSERT INTO test1 VALUES (1);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(4)).execute(anyString());
      verify(statement).execute("CREATE TABLE test1 (id INT)");
      verify(statement).execute("CREATE TABLE test2 (id INT)");
      verify(statement).execute("INSERT INTO test2 VALUES (1)");
      verify(statement).execute("INSERT INTO test1 VALUES (1)");
    }

    @Test
    @DisplayName("Should handle statements without delimiter at end of script")
    void shouldHandleStatementsWithoutDelimiterAtEnd() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE test (id INT);
                INSERT INTO test VALUES (1)
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement).execute("INSERT INTO test VALUES (1)");
    }
  }

  @Nested
  @DisplayName("Statement Skipping")
  class StatementSkippingTests {

    @Test
    @DisplayName("Should skip CREATE DATABASE statements")
    void shouldSkipCreateDatabaseStatements() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE DATABASE testdb;
                CREATE TABLE test (id INT);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(1)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement, never()).execute(contains("CREATE DATABASE"));
    }

    @Test
    @DisplayName("Should skip USE statements")
    void shouldSkipUseStatements() throws Exception {
      // Given
      String sqlScript =
          """
                USE testdb;
                CREATE TABLE test (id INT);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(1)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement, never()).execute(contains("USE"));
    }

    @Test
    @DisplayName("Should skip SET statements")
    void shouldSkipSetStatements() throws Exception {
      // Given
      String sqlScript =
          """
                SET FOREIGN_KEY_CHECKS = 0;
                SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';
                SET NAMES utf8;
                CREATE TABLE test (id INT);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(1)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement, never()).execute(contains("SET"));
    }

    @Test
    @DisplayName("Should skip MySQL version comments")
    void shouldSkipMysqlVersionComments() throws Exception {
      // Given
      String sqlScript =
          """
                /*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
                CREATE TABLE test (id INT);
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(1)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT)");
      verify(statement, never()).execute(contains("/*!"));
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should continue execution on non-critical SQL errors")
    void shouldContinueExecutionOnNonCriticalErrors() throws Exception {
      // Given
      String sqlScript =
          """
                CREATE TABLE test (id INT);
                INSERT INTO nonexistent VALUES (1);
                INSERT INTO test VALUES (1);
                """;

      // Simulate first statement succeeds, second fails, third succeeds
      doAnswer(invocation -> null).when(statement).execute("CREATE TABLE test (id INT)");
      doThrow(new SQLException("Table 'nonexistent' doesn't exist"))
          .when(statement)
          .execute("INSERT INTO nonexistent VALUES (1)");
      doAnswer(invocation -> null).when(statement).execute("INSERT INTO test VALUES (1)");

      // When & Then - Should not throw exception
      assertDoesNotThrow(() -> sqlScriptRunner.runScript(new StringReader(sqlScript)));

      verify(statement, times(3)).execute(anyString());
    }

    @Test
    @DisplayName("Should throw exception on critical connection errors")
    void shouldThrowExceptionOnCriticalConnectionErrors() throws Exception {
      // Given
      String sqlScript = "CREATE TABLE test (id INT);";
      SQLException criticalError = new SQLException("Connection closed");
      doThrow(criticalError).when(statement).execute(anyString());

      // When & Then
      SQLException ex =
          assertThrows(
              SQLException.class, () -> sqlScriptRunner.runScript(new StringReader(sqlScript)));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("Connection closed"));
    }

    @Test
    @DisplayName("Should throw exception when all statements fail")
    void shouldThrowExceptionWhenAllStatementsFail() throws Exception {
      // Given
      String sqlScript =
          """
                INVALID SQL STATEMENT 1;
                INVALID SQL STATEMENT 2;
                """;

      doThrow(new SQLException("Invalid syntax")).when(statement).execute(anyString());

      // When & Then
      SQLException ex2 =
          assertThrows(
              SQLException.class, () -> sqlScriptRunner.runScript(new StringReader(sqlScript)));
      assertTrue(
          ex2.getMessage() != null
              && ex2.getMessage().contains("All SQL statements failed to execute"));
    }

    @Test
    @DisplayName("Should handle IOException from reader")
    void shouldHandleIOExceptionFromReader() throws Exception {
      // Given
      StringReader reader =
          new StringReader("") {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
              throw new IOException("Read error");
            }
          };

      // When & Then
      IOException ioEx = assertThrows(IOException.class, () -> sqlScriptRunner.runScript(reader));
      assertTrue(ioEx.getMessage() != null && ioEx.getMessage().contains("Read error"));
    }

    @Test
    @DisplayName("Should handle SQLException when creating statement")
    void shouldHandleSqlExceptionWhenCreatingStatement() throws Exception {
      // Given
      String sqlScript = "CREATE TABLE test (id INT);";
      when(connection.createStatement()).thenThrow(new SQLException("Cannot create statement"));

      // When & Then
      SQLException ex3 =
          assertThrows(
              SQLException.class, () -> sqlScriptRunner.runScript(new StringReader(sqlScript)));
      assertTrue(ex3.getMessage() != null && ex3.getMessage().contains("Cannot create statement"));
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarioTests {

    @Test
    @DisplayName("Should handle mixed content with comments, delimiters, and skipped statements")
    void shouldHandleMixedContent() throws Exception {
      // Given
      String sqlScript =
          """
                -- Setup script
                CREATE DATABASE testdb;
                USE testdb;

                /*
                 * User table creation
                 */
                DELIMITER $$
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(50)
                )$$

                CREATE PROCEDURE GetUser(IN userId INT)
                BEGIN
                    SELECT * FROM users WHERE id = userId;
                END$$

                DELIMITER ;

                -- Insert test data
                INSERT INTO users VALUES (1, 'John');
                INSERT INTO users VALUES (2, 'Jane');
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then - Should only execute the actual table creation and inserts, skipping USE/CREATE
      // DATABASE
      verify(statement, times(4)).execute(anyString());
      verify(statement).execute(contains("CREATE TABLE users"));
      verify(statement).execute(contains("CREATE PROCEDURE GetUser"));
      verify(statement).execute("INSERT INTO users VALUES (1, 'John')");
      verify(statement).execute("INSERT INTO users VALUES (2, 'Jane')");

      // Verify skipped statements
      verify(statement, never()).execute(contains("CREATE DATABASE"));
      verify(statement, never()).execute(contains("USE"));
    }

    @Test
    @DisplayName("Should handle script with only comments and skipped statements")
    void shouldHandleScriptWithOnlyCommentsAndSkippedStatements() throws Exception {
      // Given
      String sqlScript =
          """
                -- Configuration
                USE mydb;
                SET FOREIGN_KEY_CHECKS = 0;

                /*
                 * Database setup comments
                 */
                CREATE DATABASE mydb;
                """;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then - Should not execute any statements
      verify(statement, never()).execute(anyString());
    }

    @Test
    @DisplayName("Should handle very long statements")
    void shouldHandleVeryLongStatements() throws Exception {
      // Given - Create a long INSERT statement
      StringBuilder longInsert = new StringBuilder("INSERT INTO test VALUES ");
      for (int i = 0; i < 100; i++) {
        if (i > 0) {
          longInsert.append(", ");
        }
        longInsert.append("(").append(i).append(", 'value").append(i).append("')");
      }
      longInsert.append(";");

      String sqlScript = "CREATE TABLE test (id INT, value VARCHAR(50));\n" + longInsert;

      // When
      sqlScriptRunner.runScript(new StringReader(sqlScript));

      // Then
      verify(statement, times(2)).execute(anyString());
      verify(statement).execute("CREATE TABLE test (id INT, value VARCHAR(50))");
      verify(statement).execute(contains("INSERT INTO test VALUES"));
    }
  }
}

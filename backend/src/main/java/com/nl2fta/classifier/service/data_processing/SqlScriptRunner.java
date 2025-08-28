package com.nl2fta.classifier.service.data_processing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for executing SQL scripts Handles various SQL statement types and comment styles
 */
@Slf4j
public class SqlScriptRunner {

  private static final Pattern DELIMITER_PATTERN =
      Pattern.compile("^\\s*DELIMITER\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final String DEFAULT_DELIMITER = ";";
  private static final int LOG_TRUNCATE_LENGTH = 100;

  private static final Set<String> SKIP_PREFIXES =
      Set.of(
          "CREATE DATABASE",
          "CREATE SCHEMA",
          "USE ",
          "SET FOREIGN_KEY_CHECKS",
          "SET SQL_MODE",
          "SET NAMES",
          "SET CHARACTER_SET",
          "/*!");

  private static final Set<String> CRITICAL_ERROR_PATTERNS =
      Set.of("syntax error", "connection", "closed");

  private final Connection connection;
  private String currentDelimiter = DEFAULT_DELIMITER;

  public SqlScriptRunner(Connection connection) {
    this.connection = connection;
  }

  /** Execute SQL script from input stream */
  public void runScript(InputStream inputStream) throws SQLException, IOException {
    runScript(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
  }

  /** Execute SQL script from reader */
  public void runScript(Reader reader) throws SQLException, IOException {
    List<String> statements = parseScript(reader);
    executeStatements(statements);
  }

  /** Parse SQL script into individual statements */
  private List<String> parseScript(Reader reader) throws IOException {
    List<String> statements = new ArrayList<>();
    StringBuilder currentStatement = new StringBuilder();

    try (BufferedReader br = new BufferedReader(reader)) {
      String line;
      boolean inMultiLineComment = false;

      while ((line = br.readLine()) != null) {
        line = line.trim();

        // Skip empty lines
        if (line.isEmpty()) {
          continue;
        }

        // Handle multi-line comments
        if (line.startsWith("/*")) {
          // Check if comment closes on the same line
          if (line.contains("*/")) {
            // Single line comment, skip this line but don't set inMultiLineComment
            continue;
          } else {
            // Multi-line comment starts
            inMultiLineComment = true;
            continue;
          }
        }
        if (inMultiLineComment) {
          if (line.contains("*/")) {
            inMultiLineComment = false;
          }
          continue;
        }

        // Skip single-line comments
        if (line.startsWith("--") || line.startsWith("#")) {
          continue;
        }

        // Check for DELIMITER command (MySQL specific)
        Matcher delimiterMatcher = DELIMITER_PATTERN.matcher(line);
        if (delimiterMatcher.matches()) {
          // Execute any pending statement
          if (currentStatement.length() > 0) {
            statements.add(currentStatement.toString());
            currentStatement.setLength(0);
          }
          currentDelimiter = delimiterMatcher.group(1).trim();
          continue;
        }

        // Add line to current statement
        currentStatement.append(line).append(" ");

        // Check if statement is complete
        if (line.endsWith(currentDelimiter)) {
          String statement = currentStatement.toString().trim();
          // Remove delimiter from end
          statement = statement.substring(0, statement.length() - currentDelimiter.length()).trim();

          if (!statement.isEmpty()) {
            statements.add(statement);
          }
          currentStatement.setLength(0);
        }
      }

      // Add any remaining statement
      String remaining = currentStatement.toString().trim();
      if (!remaining.isEmpty()) {
        statements.add(remaining);
      }
    }

    return statements;
  }

  /** Execute list of SQL statements */
  private void executeStatements(List<String> statements) throws SQLException {
    int successCount = 0;
    int failCount = 0;

    try (Statement stmt = connection.createStatement()) {
      for (String sql : statements) {
        try {
          // Skip certain statements that might not be supported
          if (shouldSkipStatement(sql)) {
            log.debug("Skipping statement: {}", truncateStatement(sql));
            continue;
          }

          log.debug("Executing: {}", truncateStatement(sql));
          stmt.execute(sql);
          successCount++;

        } catch (SQLException e) {
          log.warn(
              "Failed to execute statement: {}. Error: {}", truncateStatement(sql), e.getMessage());
          failCount++;

          // Continue with other statements unless it's a critical error
          if (isCriticalError(e)) {
            throw e;
          }
        }
      }
    }

    log.info("Script execution complete. Success: {}, Failed: {}", successCount, failCount);

    if (successCount == 0 && failCount > 0) {
      throw new SQLException("All SQL statements failed to execute");
    }
  }

  /** Check if statement should be skipped */
  private boolean shouldSkipStatement(String sql) {
    String upperSql = sql.toUpperCase();
    return SKIP_PREFIXES.stream().anyMatch(upperSql::startsWith);
  }

  /** Check if SQLException is critical */
  private boolean isCriticalError(SQLException e) {
    String message = e.getMessage().toLowerCase();
    return CRITICAL_ERROR_PATTERNS.stream().anyMatch(message::contains);
  }

  /** Truncate statement for logging */
  private String truncateStatement(String sql) {
    return sql.length() <= LOG_TRUNCATE_LENGTH
        ? sql
        : sql.substring(0, LOG_TRUNCATE_LENGTH) + "...";
  }
}

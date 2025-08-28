package com.nl2fta.classifier.service.data_processing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SqlFileProcessorService {

  private static final String DB_URL =
      "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
  private static final String DB_USER = "sa";
  private static final String DB_PASSWORD = "";

  private static final Set<String> H2_SYSTEM_TABLES =
      Set.of(
          "CATALOGS",
          "COLLATIONS",
          "COLUMNS",
          "COLUMN_PRIVILEGES",
          "CONSTANTS",
          "CONSTRAINTS",
          "CROSS_REFERENCES",
          "DOMAINS",
          "DOMAIN_CONSTRAINTS",
          "FUNCTION_ALIASES",
          "FUNCTION_COLUMNS",
          "HELP",
          "INDEXES",
          "IN_DOUBT",
          "KEY_COLUMN_USAGE",
          "LOCKS",
          "QUERY_STATISTICS",
          "RIGHTS",
          "ROLES",
          "SCHEMATA",
          "SEQUENCES",
          "SESSIONS",
          "SESSION_STATE",
          "SETTINGS",
          "SYNONYMS",
          "TABLES",
          "TABLE_PRIVILEGES",
          "TABLE_TYPES",
          "TRIGGERS",
          "TYPE_INFO",
          "USERS",
          "VIEWS");

  public Map<String, byte[]> processAllTablesToCSV(MultipartFile sqlFile)
      throws SQLException, IOException {
    Map<String, byte[]> tableDataMap = new LinkedHashMap<>();

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
      // Clean up any existing user tables from previous runs
      cleanupUserTables(conn);

      executeSqlFile(conn, sqlFile);

      List<String> tableNames = getUserTables(conn);

      if (tableNames.isEmpty()) {
        throw new IllegalArgumentException("No user tables found in SQL file");
      }

      for (String tableName : tableNames) {
        byte[] csvData = exportTableToCsv(conn, tableName);
        tableDataMap.put(tableName, csvData);
      }

      return tableDataMap;
    }
  }

  private void executeSqlFile(Connection conn, MultipartFile sqlFile)
      throws SQLException, IOException {
    SqlScriptRunner runner = new SqlScriptRunner(conn);
    try (InputStream is = sqlFile.getInputStream()) {
      runner.runScript(is);
    }
  }

  private byte[] exportTableToCsv(Connection conn, String tableName)
      throws SQLException, IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
      String escapedTableName = escapeTableName(tableName);
      String query = "SELECT * FROM " + escapedTableName;

      try (Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(query)) {

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        String[] headers = new String[columnCount];
        for (int i = 1; i <= columnCount; i++) {
          headers[i - 1] = metaData.getColumnName(i);
        }
        writer.writeNext(headers);

        while (rs.next()) {
          String[] row = new String[columnCount];
          for (int i = 1; i <= columnCount; i++) {
            Object value = rs.getObject(i);
            row[i - 1] = value != null ? value.toString() : "";
          }
          writer.writeNext(row);
        }
      }
    }

    return baos.toByteArray();
  }

  private List<String> getUserTables(Connection conn) throws SQLException {
    List<String> tableNames = new ArrayList<>();

    try (ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        String tableName = tables.getString("TABLE_NAME");
        String tableType = tables.getString("TABLE_TYPE");
        String tableSchema = tables.getString("TABLE_SCHEM");

        log.debug("Found table: {} (type: {}, schema: {})", tableName, tableType, tableSchema);

        // H2 returns "BASE TABLE" for regular tables
        if (("TABLE".equals(tableType) || "BASE TABLE".equals(tableType))
            && !isSystemTable(tableName, tableSchema)) {
          log.debug("Identified user table: {}", tableName);
          tableNames.add(tableName);
        } else if ("TABLE".equals(tableType) || "BASE TABLE".equals(tableType)) {
          log.debug("Skipped system table: {}", tableName);
        }
      }
    }

    log.info("Found {} user tables: {}", tableNames.size(), tableNames);
    return tableNames;
  }

  private boolean isSystemTable(String tableName, String tableSchema) {
    log.debug("Checking if {} is a system table (schema: {})", tableName, tableSchema);

    // INFORMATION_SCHEMA tables are always system tables
    if ("INFORMATION_SCHEMA".equalsIgnoreCase(tableSchema)) {
      log.debug("{} is in INFORMATION_SCHEMA, marking as system table", tableName);
      return true;
    }

    // For PUBLIC schema, only consider it a system table if it's in our known H2 system tables list
    // User tables in H2 are also created in PUBLIC schema by default
    if ("PUBLIC".equalsIgnoreCase(tableSchema)) {
      boolean isH2System = isH2SystemTable(tableName);
      log.debug("{} is in PUBLIC schema, isH2SystemTable: {}", tableName, isH2System);
      return isH2System;
    }

    // Tables in other schemas are considered user tables
    log.debug("{} is in schema {}, marking as user table", tableName, tableSchema);
    return false;
  }

  private boolean isH2SystemTable(String tableName) {
    boolean isSystemTable = H2_SYSTEM_TABLES.contains(tableName.toUpperCase());
    log.debug("Checking if {} is in H2_SYSTEM_TABLES: {}", tableName, isSystemTable);
    return isSystemTable;
  }

  private String escapeTableName(String tableName) {
    return "\"" + tableName.replace("\"", "\"\"") + "\"";
  }

  private void cleanupUserTables(Connection conn) throws SQLException {
    List<String> existingTables = getUserTables(conn);

    if (!existingTables.isEmpty()) {
      log.info("Cleaning up {} existing user tables", existingTables.size());

      try (Statement stmt = conn.createStatement()) {
        for (String tableName : existingTables) {
          String dropSql = "DROP TABLE IF EXISTS " + escapeTableName(tableName);
          log.debug("Executing: {}", dropSql);
          stmt.execute(dropSql);
        }
      }
    }
  }
}

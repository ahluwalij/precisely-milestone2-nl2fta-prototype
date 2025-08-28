package com.nl2fta.classifier.service.data_processing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.opencsv.CSVReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CsvParsingService {

  @Value("${app.defaults.locale:en-US}")
  private String defaultLocale;

  public TableClassificationRequest parseCsvToRequest(
      byte[] csvData, String fileName, Integer maxSamples, String locale) throws Exception {
    List<Map<String, Object>> data = new ArrayList<>();
    List<String> columns = new ArrayList<>();

    try (CSVReader reader =
        new CSVReader(
            new InputStreamReader(new ByteArrayInputStream(csvData), StandardCharsets.UTF_8))) {

      String[] headers = reader.readNext();
      if (headers == null || headers.length == 0) {
        throw new IllegalArgumentException("CSV file has no headers");
      }

      columns.addAll(Arrays.asList(headers));

      String[] row;
      while ((row = reader.readNext()) != null) {
        if (row.length != headers.length) {
          log.debug(
              "Skipping row with incorrect column count: {} vs {}", row.length, headers.length);
          continue;
        }

        Map<String, Object> rowData = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
          rowData.put(headers[i], row[i]);
        }
        data.add(rowData);

        if (maxSamples != null && data.size() >= maxSamples) {
          break;
        }
      }
    }

    if (data.isEmpty()) {
      throw new IllegalArgumentException("CSV file contains no data");
    }

    return TableClassificationRequest.builder()
        .tableName(extractTableName(fileName))
        .columns(columns)
        .data(data)
        .maxSamples(maxSamples)
        .locale(locale != null ? locale : defaultLocale)
        .includeStatistics(true)
        // Frontend file-upload path should use combined semantic types (converted built-ins +
        // customs)
        .useAllSemanticTypes(true)
        .build();
  }

  public TableClassificationRequest parseCsvToRequest(
      InputStream csvStream, String fileName, Integer maxSamples, String locale) throws Exception {
    List<Map<String, Object>> data = new ArrayList<>();
    List<String> columns = new ArrayList<>();

    try (CSVReader reader =
        new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
      String[] headers = reader.readNext();
      if (headers == null || headers.length == 0) {
        throw new IllegalArgumentException("CSV file has no headers");
      }

      columns.addAll(Arrays.asList(headers));

      String[] row;
      while ((row = reader.readNext()) != null) {
        if (row.length != headers.length) {
          log.debug(
              "Skipping row with incorrect column count: {} vs {}", row.length, headers.length);
          continue;
        }

        Map<String, Object> rowData = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
          rowData.put(headers[i], row[i]);
        }
        data.add(rowData);

        if (maxSamples != null && data.size() >= maxSamples) {
          break;
        }
      }
    }

    if (data.isEmpty()) {
      throw new IllegalArgumentException("CSV file contains no data");
    }

    return TableClassificationRequest.builder()
        .tableName(extractTableName(fileName))
        .columns(columns)
        .data(data)
        .maxSamples(maxSamples)
        .locale(locale != null ? locale : defaultLocale)
        .includeStatistics(true)
        // Frontend file-upload path should use combined semantic types (converted built-ins +
        // customs)
        .useAllSemanticTypes(true)
        .build();
  }

  public int countCsvDataRows(InputStream csvStream) throws Exception {
    int count = 0;
    try (CSVReader reader =
        new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
      String[] headers = reader.readNext();
      if (headers == null || headers.length == 0) {
        throw new IllegalArgumentException("CSV file has no headers");
      }
      while (reader.readNext() != null) {
        count++;
      }
    }
    return count;
  }

  private String extractTableName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "unnamed_table";
    }
    int lastDotIndex = fileName.lastIndexOf('.');
    return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
  }
}

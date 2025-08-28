package com.nl2fta.classifier.service.storage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AnalysisStorageService {

  private final Map<String, StoredAnalysis> analysisStorage = new ConcurrentHashMap<>();

  @Data
  public static class StoredAnalysis {
    private String analysisId;
    private String fileName;
    private LocalDateTime timestamp;
    private TableClassificationResponse response;
    private List<FieldData> fields = new ArrayList<>();
    private List<String> columns = new ArrayList<>();
    private List<Map<String, Object>> data = new ArrayList<>();
    private String locale;
  }

  @Data
  public static class FieldData {
    private String fieldName;
    private String currentSemanticType;
    private double currentConfidence;
    private List<String> sampleValues = new ArrayList<>();
  }

  public String storeAnalysis(String fileName, TableClassificationResponse response) {
    String analysisId = UUID.randomUUID().toString();

    StoredAnalysis analysis = new StoredAnalysis();
    analysis.setAnalysisId(analysisId);
    analysis.setFileName(fileName);
    analysis.setTimestamp(LocalDateTime.now());
    analysis.setResponse(response);

    // Store columns and data if available
    if (response.getData() != null && !response.getData().isEmpty()) {
      // Preserve column order from the LinkedHashMap
      Map<String, Object> firstRow = response.getData().get(0);
      if (firstRow instanceof LinkedHashMap) {
        analysis.setColumns(new ArrayList<>(firstRow.keySet()));
      } else {
        // Fallback to unordered if not LinkedHashMap
        analysis.setColumns(new ArrayList<>(firstRow.keySet()));
      }
      analysis.setData(response.getData());
    } else if (response.getColumnClassifications() != null) {
      // If data is not in response, at least store the column names
      analysis.setColumns(new ArrayList<>(response.getColumnClassifications().keySet()));
    }

    // Store locale if available in processing metadata
    if (response.getProcessingMetadata() != null
        && response.getProcessingMetadata().getLocaleUsed() != null) {
      analysis.setLocale(response.getProcessingMetadata().getLocaleUsed());
    } else {
      // Default to en-US if locale is not available
      analysis.setLocale("en-US");
    }

    // Extract field data for quick access
    if (response.getColumnClassifications() != null) {
      response
          .getColumnClassifications()
          .forEach(
              (columnName, classification) -> {
                FieldData fieldData = new FieldData();
                fieldData.setFieldName(columnName);
                fieldData.setCurrentSemanticType(classification.getSemanticType());
                fieldData.setCurrentConfidence(
                    classification.getConfidence() != null ? classification.getConfidence() : 0.0);

                // Store sample values from shape details
                if (classification.getShapeDetails() != null) {
                  List<String> sampleValues = new ArrayList<>();
                  classification
                      .getShapeDetails()
                      .forEach(
                          shape -> {
                            if (shape.getExamples() != null) {
                              sampleValues.addAll(shape.getExamples());
                            }
                          });
                  fieldData.setSampleValues(sampleValues);
                }

                analysis.getFields().add(fieldData);
              });
    }

    analysisStorage.put(analysisId, analysis);
    log.info("Stored analysis {} for file {}", analysisId, fileName);

    return analysisId;
  }

  public StoredAnalysis getAnalysis(String analysisId) {
    return analysisStorage.get(analysisId);
  }

  public List<StoredAnalysis> getAllAnalyses() {
    return new ArrayList<>(analysisStorage.values());
  }

  public void updateAnalysis(String analysisId, TableClassificationResponse response) {
    StoredAnalysis existingAnalysis = analysisStorage.get(analysisId);
    if (existingAnalysis != null) {
      // Update the response and re-extract field data
      existingAnalysis.setResponse(response);
      existingAnalysis.setTimestamp(LocalDateTime.now());
      existingAnalysis.getFields().clear();

      // Update locale if available
      if (response.getProcessingMetadata() != null
          && response.getProcessingMetadata().getLocaleUsed() != null) {
        existingAnalysis.setLocale(response.getProcessingMetadata().getLocaleUsed());
      }

      // Re-extract field data with updated semantic types
      if (response.getColumnClassifications() != null) {
        response
            .getColumnClassifications()
            .forEach(
                (columnName, classification) -> {
                  FieldData fieldData = new FieldData();
                  fieldData.setFieldName(columnName);
                  fieldData.setCurrentSemanticType(classification.getSemanticType());
                  fieldData.setCurrentConfidence(
                      classification.getConfidence() != null
                          ? classification.getConfidence()
                          : 0.0);

                  // Store sample values from shape details
                  if (classification.getShapeDetails() != null) {
                    List<String> sampleValues = new ArrayList<>();
                    classification
                        .getShapeDetails()
                        .forEach(
                            shape -> {
                              if (shape.getExamples() != null) {
                                sampleValues.addAll(shape.getExamples());
                              }
                            });
                    fieldData.setSampleValues(sampleValues);
                  }

                  existingAnalysis.getFields().add(fieldData);
                });
      }

      log.info("Updated analysis {} for file {}", analysisId, existingAnalysis.getFileName());
    } else {
      log.warn("Cannot update analysis - not found: {}", analysisId);
    }
  }

  public void clearAnalyses() {
    analysisStorage.clear();
    log.info("Cleared all stored analyses");
  }

  public boolean deleteAnalysis(String analysisId) {
    StoredAnalysis removed = analysisStorage.remove(analysisId);
    if (removed != null) {
      log.info("Deleted analysis {} for file {}", analysisId, removed.getFileName());
      return true;
    }
    log.warn("Analysis not found for deletion: {}", analysisId);
    return false;
  }
}

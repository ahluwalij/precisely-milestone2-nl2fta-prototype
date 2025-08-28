package com.nl2fta.classifier.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nl2fta.classifier.config.ApplicationProperties;
import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Table Classification", description = "Table column classification using FTA")
public class TableClassificationController {

  private final TableClassificationService classificationService;
  private final AnalysisStorageService analysisStorageService;
  private final ApplicationProperties applicationProperties;

  @Value("${app.defaults.max-samples:1000}")
  private Integer defaultMaxSamples;

  @PostMapping(
      value = "/classify/table",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Classify table columns",
      description = "Analyzes a table's columns to detect base types and semantic types using FTA")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful classification",
            content =
                @Content(schema = @Schema(implementation = TableClassificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content)
      })
  public ResponseEntity<TableClassificationResponse> classifyTable(
      @Valid @RequestBody TableClassificationRequest request,
      @org.springframework.web.bind.annotation.RequestHeader(
              value = "X-Return-Full-Data",
              required = false)
          Boolean fullDataHeader) {
    log.info(
        "[TABLE-CLASSIFICATION-CONTROLLER] Received classification request for table: {}",
        request.getTableName());
    log.info(
        "[TABLE-CLASSIFICATION-CONTROLLER] Request details - columns: {}, data rows: {}, max samples: {}",
        request.getColumns(),
        request.getData() != null ? request.getData().size() : 0,
        request.getMaxSamples());

    if (request.getMaxSamples() == null) {
      request.setMaxSamples(defaultMaxSamples);
      log.info("[TABLE-CLASSIFICATION-CONTROLLER] Set default max samples: {}", defaultMaxSamples);
    }

    // Do not server-truncate. The service layer will respect maxSamples when training.

    try {
      TableClassificationResponse response = classificationService.classifyTable(request);
      log.info("[TABLE-CLASSIFICATION-CONTROLLER] Classification completed successfully");

      // Mirror the (possibly truncated) request data in the response
      response.setData(request.getData());

      // Store the analysis for preview functionality
      String analysisId = analysisStorageService.storeAnalysis(request.getTableName(), response);
      log.info(
          "[TABLE-CLASSIFICATION-CONTROLLER] Stored analysis with ID: {} for table: {}",
          analysisId,
          request.getTableName());

      // Include the analysis ID in the response
      response.setAnalysisId(analysisId);

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("[TABLE-CLASSIFICATION-CONTROLLER] Error during classification", e);
      throw e;
    }
  }

  @GetMapping("/health")
  @Operation(
      summary = "Health check",
      description = "Check if the classification service is healthy")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Service is healthy")})
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.ok(Map.of("status", "UP", "timestamp", System.currentTimeMillis()));
  }

  @GetMapping("/analyses")
  @Operation(
      summary = "Get all stored analyses",
      description = "Retrieve all analyses stored in the system")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved analyses")})
  public ResponseEntity<List<AnalysisStorageService.StoredAnalysis>> getAllAnalyses() {
    List<AnalysisStorageService.StoredAnalysis> storedAnalyses =
        analysisStorageService.getAllAnalyses();
    log.debug("Retrieved {} stored analyses", storedAnalyses.size());
    return ResponseEntity.ok(storedAnalyses);
  }

  @DeleteMapping("/analyses")
  @Operation(
      summary = "Delete all stored analyses",
      description = "Remove all analyses stored in the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Successfully deleted all analyses")
      })
  public ResponseEntity<Void> deleteAllAnalyses() {
    analysisStorageService.clearAnalyses();
    log.info("Deleted all stored analyses");
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/analyses/{analysisId}")
  @Operation(
      summary = "Delete a specific analysis",
      description = "Remove a specific analysis from the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Successfully deleted the analysis"),
        @ApiResponse(responseCode = "404", description = "Analysis not found")
      })
  public ResponseEntity<Void> deleteAnalysis(@PathVariable String analysisId) {
    boolean deleted = analysisStorageService.deleteAnalysis(analysisId);
    if (deleted) {
      log.info("Deleted analysis: {}", analysisId);
      return ResponseEntity.noContent().build();
    } else {
      log.warn("Analysis not found for deletion: {}", analysisId);
      return ResponseEntity.notFound().build();
    }
  }

  @PostMapping("/analyses/{analysisId}/reanalyze")
  @Operation(
      summary = "Reanalyze a stored analysis",
      description = "Re-run analysis on a previously stored analysis using current semantic types")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully reanalyzed"),
        @ApiResponse(responseCode = "404", description = "Analysis not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<TableClassificationResponse> reanalyzeAnalysis(
      @PathVariable String analysisId,
      @org.springframework.web.bind.annotation.RequestHeader(
              value = "X-Return-Full-Data",
              required = false)
          Boolean fullDataHeader) {
    log.info("Reanalyzing analysis: {}", analysisId);

    AnalysisStorageService.StoredAnalysis storedAnalysis =
        analysisStorageService.getAnalysis(analysisId);
    if (storedAnalysis == null) {
      log.warn("Analysis not found: {}", analysisId);
      return ResponseEntity.notFound().build();
    }

    try {
      // Get the original data and rerun classification
      if (storedAnalysis.getData() != null && !storedAnalysis.getData().isEmpty()) {
        // Reconstruct the original request
        TableClassificationRequest request = new TableClassificationRequest();
        request.setTableName(storedAnalysis.getFileName());
        request.setData(storedAnalysis.getData());
        request.setMaxSamples(defaultMaxSamples);
        // Ensure combined registry is used for re-analysis
        request.setUseAllSemanticTypes(true);

        // Extract columns from the data
        if (!storedAnalysis.getData().isEmpty()) {
          Map<String, Object> firstRow = storedAnalysis.getData().get(0);
          request.setColumns(new ArrayList<>(firstRow.keySet()));
        }

        // Preserve full dataset size on re-analysis; do not truncate
        if (request.getData() != null) {
          request.setMaxSamples(request.getData().size());
        }

        // Run classification with current semantic types
        TableClassificationResponse response = classificationService.classifyTable(request);
        response.setData(request.getData());
        response.setAnalysisId(analysisId);

        // Update the stored analysis
        storedAnalysis.setResponse(response);
        storedAnalysis.setTimestamp(java.time.LocalDateTime.now());

        log.info("Successfully reanalyzed analysis: {}", analysisId);
        return ResponseEntity.ok(response);
      } else {
        log.warn("No data available for reanalysis: {}", analysisId);
        return ResponseEntity.badRequest().build();
      }
    } catch (Exception e) {
      log.error("Failed to reanalyze analysis: {}", analysisId, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  private List<Map<String, Object>> truncateByHalf(List<Map<String, Object>> data) {
    if (data == null || data.isEmpty()) {
      return data;
    }
    int half = Math.max(1, data.size() / 2);
    return new ArrayList<>(data.subList(0, half));
  }
}

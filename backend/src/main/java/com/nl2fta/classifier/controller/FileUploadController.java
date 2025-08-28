package com.nl2fta.classifier.controller;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.service.TableClassificationService;
import com.nl2fta.classifier.service.data_processing.CsvParsingService;
import com.nl2fta.classifier.service.data_processing.SqlFileProcessorService;
import com.nl2fta.classifier.service.storage.AnalysisStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload and analysis endpoints")
public class FileUploadController {

  @Value("${app.upload.max-file-size:10485760}")
  private long maxFileSize;

  @Value("${app.upload.allowed-extensions:csv,sql}")
  private Set<String> allowedExtensions;

  private final TableClassificationService classificationService;
  private final SqlFileProcessorService sqlFileProcessorService;
  private final CsvParsingService csvParsingService;
  private final AnalysisStorageService analysisStorageService;

  @PostMapping(
      value = "/table-classification/analyze",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Analyze uploaded file",
      description = "Analyze an uploaded CSV or SQL file to classify columns")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful analysis",
            content =
                @Content(schema = @Schema(implementation = TableClassificationResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid file or request",
            content = @Content),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content)
      })
  public ResponseEntity<TableClassificationResponse> analyzeFile(
      @Parameter(description = "File to analyze (CSV or SQL)", required = true)
          @RequestParam("file")
          MultipartFile file,
      @Parameter(description = "Table name (for SQL files)", required = false)
          @RequestParam(value = "tableName", required = false)
          String tableName,
      @Parameter(description = "Maximum samples to analyze", required = false)
          @RequestParam(value = "maxSamples", required = false)
          Integer maxSamples,
      @Parameter(description = "Locale for analysis", required = false)
          @RequestParam(value = "locale", required = false)
          String locale,
      @org.springframework.web.bind.annotation.RequestHeader(
              value = "X-Return-Full-Data",
              required = false)
          Boolean fullDataHeader) {

    try {
      validateFile(file);

      String fileName = file.getOriginalFilename();
      String extension = extractFileExtension(fileName);

      TableClassificationRequest request;

      if ("sql".equalsIgnoreCase(extension)) {
        Map<String, byte[]> tableDataMap = sqlFileProcessorService.processAllTablesToCSV(file);
        Map.Entry<String, byte[]> entry = tableDataMap.entrySet().iterator().next();
        String processedFileName = fileName + " (" + entry.getKey() + ")";
        request =
            csvParsingService.parseCsvToRequest(
                new java.io.ByteArrayInputStream(entry.getValue()),
                processedFileName,
                maxSamples,
                locale);
      } else {
        // Stream the file to avoid loading whole payload; compute half rows if maxSamples not
        // provided
        request =
            csvParsingService.parseCsvToRequest(
                file.getInputStream(), fileName, maxSamples, locale);
      }

      TableClassificationResponse response = classificationService.classifyTable(request);
      response.setData(request.getData());

      // Store the analysis for preview functionality
      String analysisId = analysisStorageService.storeAnalysis(fileName, response);
      log.info("Stored analysis with ID: {} for file: {}", analysisId, fileName);

      // Include the analysis ID in the response
      response.setAnalysisId(analysisId);

      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      log.error("Invalid request: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (SQLException e) {
      log.error("SQL processing error: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    } catch (Exception e) {
      log.error("Error processing file: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping(
      value = "/table-classification/reanalyze/{analysisId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Re-analyze with updated semantic types",
      description = "Re-analyze an existing analysis using the latest custom semantic types")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful re-analysis",
            content =
                @Content(schema = @Schema(implementation = TableClassificationResponse.class))),
        @ApiResponse(responseCode = "404", description = "Analysis not found", content = @Content),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content)
      })
  public ResponseEntity<TableClassificationResponse> reanalyzeWithUpdatedTypes(
      @Parameter(description = "Analysis ID", required = true) @PathVariable String analysisId,
      @org.springframework.web.bind.annotation.RequestHeader(
              value = "X-Return-Full-Data",
              required = false)
          Boolean fullDataHeader) {

    try {
      // Retrieve the stored analysis
      AnalysisStorageService.StoredAnalysis storedAnalysis =
          analysisStorageService.getAnalysis(analysisId);

      if (storedAnalysis == null) {
        log.warn("Analysis not found for ID: {}", analysisId);
        return ResponseEntity.notFound().build();
      }

      // Reconstruct the classification request from stored data
      TableClassificationRequest request = new TableClassificationRequest();
      request.setTableName(storedAnalysis.getFileName());
      request.setColumns(storedAnalysis.getColumns());
      request.setData(storedAnalysis.getData());
      request.setIncludeStatistics(true);
      // Always ensure locale is set, use en-US as default
      String locale = storedAnalysis.getLocale();
      if (locale == null || locale.trim().isEmpty()) {
        locale = "en-US";
      }
      request.setLocale(locale);

      // CRITICAL: Ensure re-analysis uses the full combined registry
      // (converted built-ins + user custom types) just like fresh uploads
      request.setUseAllSemanticTypes(true);

      // Do not truncate rows during re-analysis; preserve original dataset size
      if (request.getData() != null) {
        request.setMaxSamples(request.getData().size());
      }

      // Re-analyze with current semantic types (including any new custom types)
      TableClassificationResponse response = classificationService.classifyTable(request);
      response.setData(request.getData());

      // Keep the same analysis ID for continuity
      response.setAnalysisId(analysisId);

      // Update the stored analysis with new results (using the same ID)
      analysisStorageService.updateAnalysis(analysisId, response);
      log.info("Re-analyzed and updated analysis: {}", analysisId);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("Error re-analyzing with ID {}: {}", analysisId, e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    if (file.getSize() > maxFileSize) {
      throw new IllegalArgumentException(
          "File size exceeds maximum allowed size of " + maxFileSize + " bytes");
    }

    String fileName = file.getOriginalFilename();
    if (fileName == null || fileName.isEmpty()) {
      throw new IllegalArgumentException("File name is empty");
    }

    String extension = extractFileExtension(fileName);
    if (!allowedExtensions.contains(extension.toLowerCase())) {
      throw new IllegalArgumentException(
          "File type not supported. Allowed types: " + allowedExtensions);
    }
  }

  private String extractFileExtension(String fileName) {
    int lastDotIndex = fileName.lastIndexOf('.');
    return (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1)
        ? ""
        : fileName.substring(lastDotIndex + 1);
  }
}

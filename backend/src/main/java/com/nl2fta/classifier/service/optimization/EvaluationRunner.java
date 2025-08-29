package com.nl2fta.classifier.service.optimization;

import com.nl2fta.classifier.dto.analysis.TableClassificationRequest;
import com.nl2fta.classifier.dto.analysis.TableClassificationResponse;
import com.nl2fta.classifier.dto.optimization.F1EvaluationRequest;
import com.nl2fta.classifier.dto.optimization.F1EvaluationResult;
import com.nl2fta.classifier.service.TableClassificationService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationRunner {

  private final TableClassificationService classificationService;

  public F1EvaluationResult evaluate(F1EvaluationRequest request) throws Exception {
    Path datasetsRoot = Path.of("evaluator", "datasets", "data");
    Path csv = resolveDatasetCsv(datasetsRoot, request.getDatasetCsv());
    if (csv == null || !Files.exists(csv)) throw new IllegalArgumentException("Dataset CSV not found: " + datasetsRoot.resolve(request.getDatasetCsv()).normalize());

    // Load CSV quickly (supports evaluator format: baseline row, custom row, headers row, then data rows)
    List<String> lines = Files.readAllLines(csv);
    if (lines.isEmpty()) throw new IllegalArgumentException("Empty CSV: " + csv);

    String[] headers;
    int dataStartIndex;
    if (lines.size() >= 3) {
      headers = lines.get(2).split(",");
      dataStartIndex = 3;
    } else {
      headers = lines.get(0).split(",");
      dataStartIndex = 1;
    }

    // Build data rows (limited sample for speed)
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = dataStartIndex; i < Math.min(lines.size(), dataStartIndex + 2000); i++) {
      String line = lines.get(i);
      String[] parts = line.split(",", -1);
      Map<String, Object> row = new LinkedHashMap<>();
      for (int c = 0; c < headers.length && c < parts.length; c++) row.put(headers[c], parts[c]);
      rows.add(row);
    }

    TableClassificationRequest classifyReq = TableClassificationRequest.builder()
        .tableName(new File(request.getDatasetCsv()).getName())
        .columns(List.of(headers))
        .data(rows)
        .maxSamples(2000)
        .includeStatistics(false)
        .useAllSemanticTypes(true)
        .build();

    TableClassificationResponse resp = classificationService.classifyTable(classifyReq);

    Map<String, String> gt = (request.getGroundTruthPairs() == null || request.getGroundTruthPairs().isEmpty())
        ? deriveGroundTruthFromCsv(lines)
        : parseGroundTruthPairs(request.getGroundTruthPairs());
    List<String> targetColumns = request.getColumns() != null && !request.getColumns().isEmpty()
        ? request.getColumns() : new ArrayList<>(gt.keySet());

    int tp = 0, fp = 0, fn = 0;
    List<F1EvaluationResult.PerColumn> details = new ArrayList<>();
    for (String col : targetColumns) {
      TableClassificationResponse.ColumnClassification cc = resp.getColumnClassifications().get(col);
      String predicted = cc == null ? null : cc.getSemanticType();
      String expected = gt.get(col);
      if (expected == null) continue; // skip columns without ground truth
      boolean correct = predicted != null && predicted.equals(expected);
      if (correct) tp++; else {
        if (predicted != null) fp++; else fn++;
      }
      details.add(F1EvaluationResult.PerColumn.builder().columnName(col).predictedType(predicted).expectedType(expected).correct(correct).build());
    }

    int evalCols = details.size();
    double precision = tp + fp == 0 ? 0 : tp / (double) (tp + fp);
    double recall = tp + fn == 0 ? 0 : tp / (double) (tp + fn);
    double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);

    return F1EvaluationResult.builder()
        .precision(precision)
        .recall(recall)
        .f1(f1)
        .totalColumns(headers.length)
        .evaluatedColumns(evalCols)
        .truePositives(tp)
        .falsePositives(fp)
        .falseNegatives(fn)
        .details(details)
        .build();
  }

  private Path resolveDatasetCsv(Path datasetsRoot, String datasetCsv) throws Exception {
    if (datasetCsv == null || datasetCsv.isEmpty()) return null;
    Path candidate = datasetsRoot.resolve(datasetCsv).normalize();
    if (Files.exists(candidate)) return candidate;

    // Try *_data.csv variant
    if (datasetCsv.toLowerCase().endsWith(".csv")) {
      int idx = datasetCsv.lastIndexOf('.');
      String alt = datasetCsv.substring(0, idx) + "_data.csv";
      Path altPath = datasetsRoot.resolve(alt).normalize();
      if (Files.exists(altPath)) return altPath;
    }

    // If target refers to a directory, pick the first csv inside
    Path dir = candidate.getParent();
    if (dir != null && Files.exists(dir) && Files.isDirectory(dir)) {
      try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
        Path firstCsv = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).findFirst().orElse(null);
        if (firstCsv != null) return firstCsv;
      }
    }

    return null;
  }

  private Map<String, String> parseGroundTruthPairs(List<String> pairs) {
    Map<String, String> out = new HashMap<>();
    if (pairs == null) return out;
    for (String p : pairs) {
      if (p == null) continue;
      int idx = p.indexOf('=');
      if (idx <= 0 || idx >= p.length() - 1) continue;
      String k = p.substring(0, idx).trim();
      String v = p.substring(idx + 1).trim();
      if (!k.isEmpty() && !v.isEmpty()) out.put(k, v);
    }
    return out;
  }

  private Map<String, String> deriveGroundTruthFromCsv(List<String> lines) {
    Map<String, String> out = new HashMap<>();
    if (lines.size() < 3) return out;
    String[] baseline = lines.get(0).split(",", -1);
    String[] custom = lines.get(1).split(",", -1);
    String[] headers = lines.get(2).split(",", -1);
    int n = headers.length;
    for (int i = 0; i < n; i++) {
      String header = headers[i].trim();
      if (header.isEmpty()) continue;
      String expected = i < custom.length ? custom[i].trim() : "";
      if (expected.isEmpty()) expected = i < baseline.length ? baseline[i].trim() : "";
      if (!expected.isEmpty()) out.put(header, expected);
    }
    return out;
  }
}



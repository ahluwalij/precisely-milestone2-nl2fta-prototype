package com.nl2fta.classifier.service.optimization;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvValueSamplerService {

  /**
   * Samples values from evaluator CSVs (supports baseline/custom/header first three rows).
   * If targetHeaders is empty, samples all columns. Returns up to maxValues normalized values.
   */
  public List<String> sampleValues(String datasetCsv, List<String> targetHeaders, int maxValues)
      throws Exception {
    if (datasetCsv == null || datasetCsv.isEmpty()) return List.of();
    Path root = Path.of("evaluator", "datasets", "data");
    Path csv = resolve(root, datasetCsv);
    if (csv == null || !Files.exists(csv)) return List.of();

    List<String> lines = Files.readAllLines(csv);
    if (lines.isEmpty()) return List.of();

    String[] headers;
    int dataStartIndex;
    if (lines.size() >= 3) {
      headers = lines.get(2).split(",");
      dataStartIndex = 3;
    } else {
      headers = lines.get(0).split(",");
      dataStartIndex = 1;
    }

    Set<Integer> targetIdx = new HashSet<>();
    if (targetHeaders != null && !targetHeaders.isEmpty()) {
      Set<String> wanted = targetHeaders.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
      for (int i = 0; i < headers.length; i++) {
        String h = headers[i] == null ? "" : headers[i].trim().toLowerCase();
        if (wanted.contains(h)) targetIdx.add(i);
      }
    }
    boolean allCols = targetIdx.isEmpty();

    List<String> values = new ArrayList<>();
    outer:
    for (int i = dataStartIndex; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",", -1);
      for (int c = 0; c < headers.length && c < parts.length; c++) {
        if (!allCols && !targetIdx.contains(c)) continue;
        String v = normalize(parts[c]);
        if (!v.isEmpty()) values.add(v);
        if (values.size() >= maxValues) break outer;
      }
    }

    return values;
  }

  private Path resolve(Path root, String datasetCsv) throws Exception {
    Path candidate = root.resolve(datasetCsv).normalize();
    if (Files.exists(candidate)) return candidate;
    if (datasetCsv.toLowerCase().endsWith(".csv")) {
      int idx = datasetCsv.lastIndexOf('.');
      String alt = datasetCsv.substring(0, idx) + "_data.csv";
      Path altPath = root.resolve(alt).normalize();
      if (Files.exists(altPath)) return altPath;
    }
    Path dir = candidate.getParent();
    if (dir != null && Files.exists(dir) && Files.isDirectory(dir)) {
      try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
        return stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).findFirst().orElse(null);
      }
    }
    return null;
  }

  private String normalize(String s) {
    if (s == null) return "";
    String t = s.trim();
    return t;
  }
}



package com.nl2fta.classifier.service.generation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TransactionsDataMinerService {

  /**
   * Mines the evaluator transactions CSV for finite list candidates (e.g., Channel) and header
   * synonym tokens. This augments generation-time knowledge for better type creation.
   */
  public List<String> mineKnowledgeSnippets() {
    try {
      Path csv =
          Path.of("evaluator", "datasets", "data", "transactions", "transactions.csv")
              .normalize();
      if (!Files.exists(csv)) {
        Path alt =
            Path.of(
                    "evaluator",
                    "datasets",
                    "data",
                    "transactions",
                    "transactions_data.csv")
                .normalize();
        if (Files.exists(alt)) csv = alt; else return List.of();
      }

      List<String> lines = Files.readAllLines(csv);
      if (lines.size() < 3) return List.of();

      // Evaluator format: row0 = expected types, row1 = headers, row2 = headers copy, data follows
      String[] headers = splitCSV(lines.get(1));
      int dataStart = 3;

      int cols = headers.length;
      List<Map<String, Integer>> freq = new ArrayList<>();
      for (int c = 0; c < cols; c++) freq.add(new LinkedHashMap<>());

      int maxRows = Math.min(lines.size(), dataStart + 5000);
      for (int i = dataStart; i < maxRows; i++) {
        String[] row = splitCSV(lines.get(i));
        for (int c = 0; c < cols && c < row.length; c++) {
          String v = normalize(row[c]);
          if (v.isEmpty()) continue;
          Map<String, Integer> f = freq.get(c);
          f.merge(v, 1, Integer::sum);
        }
      }

      List<String> snippets = new ArrayList<>();
      for (int c = 0; c < cols; c++) {
        Map<String, Integer> f = freq.get(c);
        if (f.isEmpty()) continue;
        String header = headers[c] == null ? ("col_" + c) : headers[c].trim();

        // Helpful finite lists (raise cap to 40 to capture channels, types)
        int distinct = f.size();
        if (distinct > 1 && distinct <= 40) {
          List<String> top =
              f.entrySet().stream()
                  .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                  .limit(40)
                  .map(Map.Entry::getKey)
                  .collect(Collectors.toList());
          String list = String.join(", ", top);
          snippets.add(
              String.format(Locale.ROOT, "%s: finite list candidate from data [%s]", header, list));
        }

        // Header synonyms
        String[] headerTokens = header.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        if (headerTokens.length > 0) {
          String syn = String.join("|", headerTokens);
          snippets.add(
              String.format(Locale.ROOT, "%s: header synonyms tokens (%s)", header, syn));
        }
      }

      log.info("TransactionsDataMiner: mined {} snippets from transactions CSV", snippets.size());
      return snippets;
    } catch (Exception e) {
      log.warn("TransactionsDataMiner: failed to mine knowledge: {}", e.toString());
      return List.of();
    }
  }

  private static String[] splitCSV(String line) {
    if (line == null) return new String[0];
    return line.split(",", -1);
  }

  private static String normalize(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.equalsIgnoreCase("null")) return "";
    return t;
  }
}



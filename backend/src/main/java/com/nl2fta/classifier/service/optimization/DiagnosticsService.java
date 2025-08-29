package com.nl2fta.classifier.service.optimization;

import com.nl2fta.classifier.dto.optimization.ColumnDiagnostics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticsService {

  private final FiniteListBuilderService listBuilder;

  public ColumnDiagnostics compute(
      List<String> values,
      List<String> canonicalMembers,
      List<String> regexes,
      int topK,
      String decisionReason) {

    if (values == null || values.isEmpty()) {
      return ColumnDiagnostics.builder()
          .finiteCoverage(0)
          .regexCoverage(0)
          .nonNullCount(0)
          .sampleCount(0)
          .unmatchedTop(Collections.emptyList())
          .unmatchedFrequencies(Collections.emptyMap())
          .suggestedAdditions(Collections.emptyList())
          .suggestedHeaderPatterns(Collections.emptyList())
          .decisionReason(decisionReason)
          .build();
    }

    List<String> normalized = values.stream().map(listBuilder::normalize).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    int nonNull = normalized.size();

    // Finite coverage
    int finiteMatches = 0;
    if (canonicalMembers != null && !canonicalMembers.isEmpty()) {
      java.util.Set<String> set = new java.util.HashSet<>(canonicalMembers);
      for (String v : normalized) if (set.contains(v)) finiteMatches++;
    }
    double finiteCoverage = nonNull == 0 ? 0 : (finiteMatches / (double) nonNull);

    // Regex coverage (union of patterns)
    int regexMatches = 0;
    List<Pattern> compiled = new ArrayList<>();
    if (regexes != null) for (String r : regexes) compiled.add(Pattern.compile(r));
    for (String v : normalized) {
      boolean ok = false;
      for (Pattern p : compiled) {
        if (p.matcher(v).matches()) { ok = true; break; }
      }
      if (ok) regexMatches++;
    }
    double regexCoverage = nonNull == 0 ? 0 : (regexMatches / (double) nonNull);

    // Unmatched analysis
    Map<String, Integer> freq = new HashMap<>();
    for (String v : normalized) {
      boolean matchedFinite = canonicalMembers != null && canonicalMembers.contains(v);
      boolean matchedRegex = false;
      if (compiled != null) {
        for (Pattern p : compiled) { if (p.matcher(v).matches()) { matchedRegex = true; break; } }
      }
      if (!(matchedFinite || matchedRegex)) freq.merge(v, 1, Integer::sum);
    }
    List<String> unmatchedTop = freq.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(Math.max(1, topK))
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());

    ColumnDiagnostics diag = ColumnDiagnostics.builder()
        .finiteCoverage(finiteCoverage)
        .regexCoverage(regexCoverage)
        .nonNullCount(nonNull)
        .sampleCount(nonNull)
        .unmatchedTop(unmatchedTop)
        .unmatchedFrequencies(freq)
        .suggestedAdditions(unmatchedTop)
        .suggestedHeaderPatterns(Collections.emptyList())
        .decisionReason(decisionReason)
        .build();
    return diag;
  }
}



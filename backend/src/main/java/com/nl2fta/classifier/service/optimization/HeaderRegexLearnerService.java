package com.nl2fta.classifier.service.optimization;

import com.nl2fta.classifier.dto.optimization.OptimizationResult.HeaderPatternCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HeaderRegexLearnerService {

  public List<HeaderPatternCandidate> learn(List<String> positiveHeaders, List<String> negativeHeaders) {
    List<String> pos = normalizeHeaders(positiveHeaders);
    List<String> neg = normalizeHeaders(negativeHeaders);

    Set<String> vocabulary = buildVocabulary(pos);
    List<String> candidates = generateCandidates(vocabulary);
    candidates.addAll(generateShapeCandidates(pos));

    List<ScoredPattern> scored = new ArrayList<>();
    for (String pattern : candidates) {
      Score s = scorePattern(pattern, pos, neg);
      if (s.tp + s.fp == 0) continue;
      int confidence = (int) Math.round(100.0 * s.tp / (double) (s.tp + s.fp));
      // Boost recall: accept reasonably specific patterns
      if (confidence >= 60 && s.tp >= 1) {
        scored.add(new ScoredPattern(pattern, confidence, s.tp, s.fp, s.fn));
      }
    }

    // Negative guards from frequent negative tokens
    List<String> negativeGuards = topNegativeTokens(neg, 3);
    for (String token : negativeGuards) {
      String guard = "(?i)\\b" + Pattern.quote(token) + "\\b";
      scored.add(new ScoredPattern(guard, -100, 0, 0, 0));
    }

    // De-duplicate by pattern, keep highest confidence
    Map<String, ScoredPattern> bestByPattern = new HashMap<>();
    for (ScoredPattern sp : scored) {
      bestByPattern.merge(sp.pattern, sp, (a, b) -> a.confidence >= b.confidence ? a : b);
    }
    List<ScoredPattern> deduped = new ArrayList<>(bestByPattern.values());
    deduped.sort(Comparator.comparingInt((ScoredPattern sp) -> sp.confidence).reversed());

    // Keep many patterns to maximize header coverage
    List<ScoredPattern> top = deduped.stream().limit(128).collect(Collectors.toList());
    return top.stream()
        .map(sp -> HeaderPatternCandidate.builder().pattern(sp.pattern).confidence(sp.confidence).build())
        .collect(Collectors.toList());
  }

  private List<String> normalizeHeaders(List<String> headers) {
    if (headers == null) return Collections.emptyList();
    return headers.stream()
        .filter(h -> h != null && !h.trim().isEmpty())
        .map(h -> h.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toList());
  }

  private Set<String> buildVocabulary(List<String> headers) {
    Set<String> vocab = new LinkedHashSet<>();
    for (String h : headers) {
      for (String token : tokenize(h)) {
        if (token.length() >= 2) vocab.add(token);
      }
    }
    return vocab;
  }

  private List<String> tokenize(String header) {
    String[] parts = header.split("[^a-z0-9]+");
    List<String> out = new ArrayList<>();
    for (String p : parts) if (!p.isEmpty()) out.add(p);
    return out;
  }

  private List<String> generateCandidates(Set<String> vocabulary) {
    List<String> patterns = new ArrayList<>();
    // Exact token anywhere
    for (String t : vocabulary) {
      patterns.add("(?i)\\b" + Pattern.quote(t) + "\\b");
    }
    // Common suffix/prefix pairs
    String[] ids = {"id", "code", "number", "num"};
    for (String t : vocabulary) {
      for (String s : ids) {
        patterns.add("(?i)^" + Pattern.quote(t) + "[ _-]?" + s + "$");
        patterns.add("(?i)^" + s + "[ _-]?" + Pattern.quote(t) + "$");
      }
    }
    return patterns;
  }

  private List<String> generateShapeCandidates(List<String> positives) {
    // Patterns based on observed separators and casing
    Set<String> out = new HashSet<>();
    for (String h : positives) {
      if (h.contains("_")) out.add("(?i)^[a-z0-9]+(?:_[a-z0-9]+)+$");
      if (h.contains("-")) out.add("(?i)^[a-z0-9]+(?:-[a-z0-9]+)+$");
      if (h.contains(" ")) out.add("(?i)^[a-z0-9]+(?: [a-z0-9]+)+$");
    }
    return new ArrayList<>(out);
  }

  private Score scorePattern(String regex, List<String> pos, List<String> neg) {
    Pattern p = Pattern.compile(regex);
    int tp = 0, fp = 0, fn = 0;
    for (String h : pos) {
      if (p.matcher(h).find()) tp++; else fn++;
    }
    for (String h : neg) {
      if (p.matcher(h).find()) fp++;
    }
    return new Score(tp, fp, fn);
  }

  private List<String> topNegativeTokens(List<String> negatives, int k) {
    Map<String, Integer> freq = new HashMap<>();
    for (String h : negatives) {
      for (String t : tokenize(h)) freq.merge(t, 1, Integer::sum);
    }
    return freq.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(k)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private static class Score {
    final int tp, fp, fn;
    Score(int tp, int fp, int fn) { this.tp = tp; this.fp = fp; this.fn = fn; }
  }

  private static class ScoredPattern {
    final String pattern; final int confidence; final int tp; final int fp; final int fn;
    ScoredPattern(String pattern, int confidence, int tp, int fp, int fn) {
      this.pattern = pattern; this.confidence = confidence; this.tp = tp; this.fp = fp; this.fn = fn;
    }
  }
}



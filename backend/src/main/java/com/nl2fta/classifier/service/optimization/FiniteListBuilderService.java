package com.nl2fta.classifier.service.optimization;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FiniteListBuilderService {

  public List<String> buildCanonicalList(List<String> positiveValues) {
    if (positiveValues == null || positiveValues.isEmpty()) return Collections.emptyList();
    Set<String> canon = new HashSet<>();
    for (String v : positiveValues) {
      String n = normalize(v);
      if (!n.isEmpty()) canon.add(n);
    }
    List<String> out = new ArrayList<>(canon);
    Collections.sort(out);
    return out;
  }

  public String normalize(String value) {
    if (value == null) return "";
    String s = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    s = s.replaceAll("\\s+", " ");
    s = s.toUpperCase(Locale.ROOT);
    return s;
  }

  public List<String> filterInvalid(List<String> values, List<String> negatives) {
    if (values == null) return Collections.emptyList();
    Set<String> neg = negatives == null ? Collections.emptySet() : negatives.stream().map(this::normalize).collect(Collectors.toSet());
    return values.stream().map(this::normalize).filter(v -> !v.isEmpty() && !neg.contains(v)).distinct().collect(Collectors.toList());
  }
}

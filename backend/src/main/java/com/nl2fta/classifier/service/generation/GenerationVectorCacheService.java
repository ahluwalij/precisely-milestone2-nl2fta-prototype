package com.nl2fta.classifier.service.generation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lightweight in-memory vector cache for generation-time retrieval.
 *
 * Uses simple bag-of-words TF normalization and cosine similarity. Keeps separate namespaces
 * ("domains") so we can isolate banking from other datasets.
 */
@Slf4j
@Service
public class GenerationVectorCacheService {

  private final Map<String, List<Document>> domainToDocs = new ConcurrentHashMap<>();
  private final Pattern splitPattern = Pattern.compile("[^\\p{Alnum}]+");

  public void clearDomain(String domain) {
    domainToDocs.remove(domain);
  }

  public int size(String domain) {
    return domainToDocs.getOrDefault(domain, Collections.emptyList()).size();
  }

  public void addDocuments(String domain, List<String> texts, Map<String, Object> baseMeta) {
    if (texts == null || texts.isEmpty()) return;
    List<Document> docs = domainToDocs.computeIfAbsent(domain, d -> Collections.synchronizedList(new ArrayList<>()));
    for (String text : texts) {
      if (text == null || text.isBlank()) continue;
      String norm = normalize(text);
      Map<String, Integer> tf = termFrequency(norm);
      double normLen = vectorNorm(tf);
      Map<String, Object> meta = baseMeta == null ? new HashMap<>() : new HashMap<>(baseMeta);
      docs.add(new Document(norm, tf, normLen, meta));
    }
    log.info("GenerationVectorCache: domain='{}' now has {} docs", domain, docs.size());
  }

  public List<Result> search(String domain, String query, int topK) {
    List<Document> docs = domainToDocs.getOrDefault(domain, Collections.emptyList());
    if (docs.isEmpty() || query == null || query.isBlank()) return Collections.emptyList();
    String qn = normalize(query);
    Map<String, Integer> qtf = termFrequency(qn);
    double qnorm = vectorNorm(qtf);
    if (qnorm == 0.0) return Collections.emptyList();
    List<Result> results = new ArrayList<>();
    for (Document d : docs) {
      double sim = cosine(qtf, qnorm, d.tf, d.normLen);
      if (sim > 0) results.add(new Result(d.text, sim, d.metadata));
    }
    results.sort(Comparator.comparingDouble((Result r) -> r.score).reversed());
    if (results.size() > topK) return new ArrayList<>(results.subList(0, topK));
    return results;
  }

  private String normalize(String s) {
    String n = Normalizer.normalize(s, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replace('\n', ' ').replace('\r', ' ');
    n = n.replaceAll("\\s+", " ").trim();
    return n;
  }

  private Map<String, Integer> termFrequency(String text) {
    String[] tokens = splitPattern.split(text);
    Map<String, Integer> tf = new LinkedHashMap<>();
    for (String t : tokens) {
      if (t.isBlank()) continue;
      String tok = t.strip();
      if (tok.length() < 2) continue;
      tf.merge(tok, 1, Integer::sum);
    }
    return tf;
  }

  private double vectorNorm(Map<String, Integer> tf) {
    long sumSq = 0;
    for (int c : tf.values()) sumSq += (long) c * c;
    return Math.sqrt(sumSq);
  }

  private double cosine(Map<String, Integer> a, double an, Map<String, Integer> b, double bn) {
    if (an == 0 || bn == 0) return 0.0;
    double dot = 0.0;
    // iterate smaller map for speed
    Map<String, Integer> small = a.size() <= b.size() ? a : b;
    Map<String, Integer> large = small == a ? b : a;
    for (Map.Entry<String, Integer> e : small.entrySet()) {
      Integer bc = large.get(e.getKey());
      if (bc != null) dot += (double) e.getValue() * (double) bc;
    }
    return dot / (an * bn);
  }

  @Data
  public static class Result {
    public final String text;
    public final double score;
    public final Map<String, Object> metadata;
  }

  @Data
  private static class Document {
    public final String text;
    public final Map<String, Integer> tf;
    public final double normLen;
    public final Map<String, Object> metadata;
  }
}



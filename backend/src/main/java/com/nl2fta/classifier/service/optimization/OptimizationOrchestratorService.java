package com.nl2fta.classifier.service.optimization;

import com.nl2fta.classifier.dto.optimization.ColumnDiagnostics;
import com.nl2fta.classifier.dto.optimization.OptimizationRequest;
import com.nl2fta.classifier.dto.optimization.OptimizationResult;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeValidationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationOrchestratorService {

  private final HeaderRegexLearnerService headerLearner;
  private final FiniteListBuilderService listBuilder;
  private final DiagnosticsService diagnosticsService;
  private final CustomSemanticTypeService customSemanticTypeService;
  private final SemanticTypeValidationService validationService;
  private final VectorIndexInitializationService vectorIndexService;

  public OptimizationResult run(OptimizationRequest request, List<String> sampleValues) {
    String typeName = request.getTypeName() != null && !request.getTypeName().trim().isEmpty()
        ? request.getTypeName().trim()
        : suggestTypeName(request.getDescription());

    List<String> canonicalMembers = listBuilder.buildCanonicalList(
        listBuilder.filterInvalid(request.getPositiveValues(), request.getNegativeValues()));

    // Construct finite plugin
    CustomSemanticType finite = CustomSemanticType.builder()
        .semanticType(typeName)
        .description(request.getDescription())
        .pluginType("list")
        .baseType("STRING")
        .threshold(request.getFiniteThreshold())
        .priority(880)
        .content(CustomSemanticType.ContentConfig.builder().type("inline").values(canonicalMembers).build())
        .validLocales(Collections.singletonList(
            CustomSemanticType.LocaleConfig.builder().localeTag("*").build()))
        .build();

    // Build a conservative shape regex from examples if present (optional)
    List<String> regexes = synthesizeValueRegexes(request.getPositiveValues());
    CustomSemanticType regex = null;
    if (!regexes.isEmpty()) {
      regex = CustomSemanticType.builder()
          .semanticType(typeName + "_REGEX")
          .description("Shape constraints for " + typeName)
          .pluginType("regex")
          .baseType("STRING")
          .threshold(request.getRegexThreshold())
          .priority(820)
          .validLocales(Collections.singletonList(CustomSemanticType.LocaleConfig.builder().localeTag("*").build()))
          .build();
      // Pack into matchEntries
      List<CustomSemanticType.MatchEntry> entries = new ArrayList<>();
      for (String r : regexes) {
        entries.add(CustomSemanticType.MatchEntry.builder().regExpReturned(r).isRegExpComplete(true).build());
      }
      CustomSemanticType.LocaleConfig lc = CustomSemanticType.LocaleConfig.builder().localeTag("*").matchEntries(entries).build();
      regex.setValidLocales(Collections.singletonList(lc));
    }

    // Learn headers
    List<OptimizationResult.HeaderPatternCandidate> headerPatterns = headerLearner.learn(
        request.getPositiveHeaders(), request.getNegativeHeaders());
    if (!headerPatterns.isEmpty()) {
      CustomSemanticType.LocaleConfig lc = finite.getValidLocales().get(0);
      List<CustomSemanticType.HeaderRegExp> headers = lc.getHeaderRegExps() == null ? new ArrayList<>() : new ArrayList<>(lc.getHeaderRegExps());
      headers.addAll(headerPatterns.stream().map(h -> CustomSemanticType.HeaderRegExp.builder().regExp(h.getPattern()).confidence(h.getConfidence()).mandatory(true).build()).collect(Collectors.toList()));
      lc.setHeaderRegExps(headers);
      finite.setValidLocales(Collections.singletonList(lc));
    }

    // Diagnostics on provided sample values (caller provides column values to evaluate)
    ColumnDiagnostics diag = diagnosticsService.compute(
        sampleValues,
        canonicalMembers,
        regexes,
        request.getTopKUnmatched(),
        decisionReason(canonicalMembers, regexes));

    OptimizationResult.PerColumnOutcome outcome = OptimizationResult.PerColumnOutcome.builder()
        .dataset(request.getDatasetCsv())
        .tableName(null)
        .columnName(null)
        .diagnostics(diag)
        .build();

    OptimizationResult result = OptimizationResult.builder()
        .semanticType(typeName)
        .finitePlugin(finite)
        .regexPlugin(regex)
        .headerPatterns(headerPatterns)
        .outcomes(Collections.singletonList(outcome))
        .rationale("Generated finite and optional regex plugin with learned headers and diagnostics.")
        .build();

    // Persistence and indexing
    if (request.isAutoLearn() && diag.getSuggestedAdditions() != null && !diag.getSuggestedAdditions().isEmpty()) {
      List<String> extended = new ArrayList<>(canonicalMembers);
      for (String s : diag.getSuggestedAdditions()) if (!extended.contains(s)) extended.add(s);
      finite.setContent(CustomSemanticType.ContentConfig.builder().type("inline").values(extended).build());
    }

    if (request.isPersist()) {
      try {
        validationService.validateCustomType(finite);
        customSemanticTypeService.addCustomType(finite);
        vectorIndexService.reindexSemanticType(finite);
        if (regex != null) {
          validationService.validateCustomType(regex);
          customSemanticTypeService.addCustomType(regex);
          vectorIndexService.reindexSemanticType(regex);
        }
      } catch (Exception e) {
        log.warn("Persistence/indexing failed: {}", e.getMessage());
      }
    }

    return result;
  }

  private String suggestTypeName(String description) {
    if (description == null || description.trim().isEmpty()) return "CUSTOM.TYPE";
    String s = description.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_");
    if (s.length() > 40) s = s.substring(0, 40);
    if (!s.contains(".")) s = "CUSTOM." + s;
    return s;
  }

  private List<String> synthesizeValueRegexes(List<String> positives) {
    if (positives == null || positives.isEmpty()) return Collections.emptyList();
    List<String> n = positives.stream().map(listBuilder::normalize).collect(Collectors.toList());
    boolean twoLetters = n.stream().allMatch(v -> v.matches("^[A-Z]{2}$"));
    boolean threeLetters = n.stream().allMatch(v -> v.matches("^[A-Z]{3}$"));
    boolean alnum2to6 = n.stream().allMatch(v -> v.matches("^[A-Z0-9]{2,6}$"));
    List<String> out = new ArrayList<>();
    if (twoLetters) out.add("^[A-Z]{2}$");
    if (threeLetters) out.add("^[A-Z]{3}$");
    if (alnum2to6) out.add("^[A-Z0-9]{2,6}$");
    return out;
  }

  private String decisionReason(List<String> canonicalMembers, List<String> regexes) {
    if (canonicalMembers != null && !canonicalMembers.isEmpty()) return "finite_match";
    if (regexes != null && !regexes.isEmpty()) return "regex_match";
    return "rejected";
  }
}



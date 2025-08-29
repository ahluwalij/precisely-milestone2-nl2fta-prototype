package com.nl2fta.classifier.controller.optimization;

import com.nl2fta.classifier.dto.optimization.F1EvaluationRequest;
import com.nl2fta.classifier.dto.optimization.F1EvaluationResult;
import com.nl2fta.classifier.dto.optimization.OptimizeAndEvalRequest;
import com.nl2fta.classifier.dto.optimization.OptimizationRequest;
import com.nl2fta.classifier.dto.optimization.OptimizationResult;
import com.nl2fta.classifier.service.optimization.OptimizationOrchestratorService;
import com.nl2fta.classifier.service.optimization.CsvValueSamplerService;
import com.nl2fta.classifier.service.optimization.EvaluationRunner;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/optimization")
@Tag(name = "Optimization", description = "Per-description optimization jobs and diagnostics")
public class OptimizationController {

  private final OptimizationOrchestratorService orchestrator;
  private final EvaluationRunner evaluationRunner;
  private final CustomSemanticTypeService customSemanticTypeService;
  private final VectorIndexInitializationService vectorIndexInitializationService;
  private final CsvValueSamplerService csvValueSamplerService;

  @PostMapping("/run")
  @Operation(summary = "Run optimization for a single description")
  public ResponseEntity<OptimizationResult> run(@Valid @RequestBody OptimizationRequest request) {
    List<String> sampleValues = Collections.emptyList();
    try {
      sampleValues = csvValueSamplerService.sampleValues(
          request.getDatasetCsv(),
          null,
          2000);
    } catch (Exception ignored) {}
    OptimizationResult result = orchestrator.run(request, sampleValues);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/f1")
  @Operation(summary = "Evaluate F1 on a single CSV (exclude semtab)")
  public ResponseEntity<F1EvaluationResult> eval(@Valid @RequestBody F1EvaluationRequest request)
      throws Exception {
    F1EvaluationResult result = evaluationRunner.evaluate(request);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/optimize-and-eval")
  @Operation(summary = "Optimize with provided examples, persist if requested, then evaluate F1")
  public ResponseEntity<F1EvaluationResult> optimizeAndEval(
      @Valid @RequestBody OptimizeAndEvalRequest request) throws Exception {
    // Baseline
    F1EvaluationResult baseline = evaluationRunner.evaluate(request.getEvaluation());

    // Attempt optimization with persistence
    List<String> sampleValues = Collections.emptyList();
    try {
      sampleValues = csvValueSamplerService.sampleValues(
          request.getEvaluation().getDatasetCsv(),
          request.getEvaluation().getColumns(),
          2000);
    } catch (Exception ignored) {}
    var optReq = request.getOptimization();
    if (optReq != null) optReq.setPersist(true);
    var optResult = orchestrator.run(optReq, sampleValues);

    F1EvaluationResult post = evaluationRunner.evaluate(request.getEvaluation());

    if (post.getF1() >= baseline.getF1()) {
      return ResponseEntity.ok(post);
    }

    // Rollback if not improved
    try {
      if (optResult.getFinitePlugin() != null) {
        String st = optResult.getFinitePlugin().getSemanticType();
        customSemanticTypeService.removeCustomType(st);
        vectorIndexInitializationService.removeFromIndex(st);
      }
      if (optResult.getRegexPlugin() != null) {
        String st = optResult.getRegexPlugin().getSemanticType();
        customSemanticTypeService.removeCustomType(st);
        vectorIndexInitializationService.removeFromIndex(st);
      }
    } catch (Exception ignored) {}

    return ResponseEntity.ok(baseline);
  }
}



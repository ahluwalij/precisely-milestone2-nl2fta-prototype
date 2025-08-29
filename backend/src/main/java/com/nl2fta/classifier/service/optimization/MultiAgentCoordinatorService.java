package com.nl2fta.classifier.service.optimization;

import com.nl2fta.classifier.dto.optimization.AgentInfo;
import com.nl2fta.classifier.dto.optimization.AgentTask;
import com.nl2fta.classifier.dto.optimization.CampaignRequest;
import com.nl2fta.classifier.dto.optimization.F1EvaluationRequest;
import com.nl2fta.classifier.dto.optimization.F1EvaluationResult;
import com.nl2fta.classifier.dto.optimization.OptimizationRequest;
import com.nl2fta.classifier.dto.optimization.OptimizationResult;
import com.nl2fta.classifier.dto.optimization.TaskResult;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentCoordinatorService {

  private final OptimizationOrchestratorService orchestrator;
  private final EvaluationRunner evaluationRunner;
  private final com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService customSemanticTypeService;
  private final com.nl2fta.classifier.service.vector.VectorIndexInitializationService vectorIndexInitializationService;
  private final CsvValueSamplerService csvValueSamplerService;

  private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
  private final Deque<AgentTask> queue = new ArrayDeque<>();
  private final Map<String, TaskResult> results = new ConcurrentHashMap<>();

  public AgentInfo registerAgent(String name, List<String> capabilities) {
    String id = UUID.randomUUID().toString();
    AgentInfo info = AgentInfo.builder().agentId(id).name(name).capabilities(capabilities).registeredAt(Instant.now()).build();
    agents.put(id, info);
    return info;
  }

  public AgentTask nextTask(String agentId) {
    AgentTask task = queue.pollFirst();
    if (task != null) {
      task.setAgentId(agentId);
      task.setStatus(AgentTask.Status.RUNNING);
      task.setUpdatedAt(Instant.now());
    }
    return task;
  }

  public void submitCampaign(CampaignRequest campaign) {
    List<OptimizationRequest> opts = campaign.getOptimizations() == null ? Collections.emptyList() : campaign.getOptimizations();
    List<F1EvaluationRequest> evals = campaign.getEvaluations() == null ? Collections.emptyList() : campaign.getEvaluations();
    int n = Math.min(opts.size(), evals.size());
    IntStream.range(0, n).forEach(i -> {
      AgentTask t = AgentTask.builder()
          .taskId(UUID.randomUUID().toString())
          .optimization(opts.get(i))
          .evaluation(evals.get(i))
          .status(AgentTask.Status.PENDING)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();
      queue.addLast(t);
    });
  }

  public TaskResult executeTask(AgentTask task) {
    try {
      F1EvaluationResult baseline = evaluationRunner.evaluate(task.getEvaluation());
      com.nl2fta.classifier.dto.optimization.OptimizationRequest optReq = task.getOptimization();
      if (optReq != null) optReq.setPersist(true);
      List<String> sampleValues = Collections.emptyList();
      try {
        sampleValues = csvValueSamplerService.sampleValues(
            task.getEvaluation().getDatasetCsv(),
            task.getEvaluation().getColumns(),
            2000);
      } catch (Exception ignored) {}
      OptimizationResult optRes = orchestrator.run(optReq, sampleValues);
      F1EvaluationResult after = evaluationRunner.evaluate(task.getEvaluation());
      double delta = (after.getF1() - baseline.getF1());
      boolean kept = after.getF1() >= baseline.getF1();
      if (!kept) {
        try {
          if (optRes.getFinitePlugin() != null) {
            String st = optRes.getFinitePlugin().getSemanticType();
            customSemanticTypeService.removeCustomType(st);
            vectorIndexInitializationService.removeFromIndex(st);
          }
          if (optRes.getRegexPlugin() != null) {
            String st = optRes.getRegexPlugin().getSemanticType();
            customSemanticTypeService.removeCustomType(st);
            vectorIndexInitializationService.removeFromIndex(st);
          }
        } catch (Exception ignored) {}
      }
      TaskResult tr = TaskResult.builder()
          .taskId(task.getTaskId())
          .baseline(baseline)
          .after(after)
          .optimizationResult(optRes)
          .deltaF1(delta)
          .persisted(kept)
          .notes(kept ? "kept" : "rollback")
          .completedAt(Instant.now())
          .build();
      results.put(task.getTaskId(), tr);
      task.setStatus(AgentTask.Status.COMPLETED);
      task.setUpdatedAt(Instant.now());
      return tr;
    } catch (Exception e) {
      task.setStatus(AgentTask.Status.FAILED);
      task.setUpdatedAt(Instant.now());
      TaskResult tr = TaskResult.builder().taskId(task.getTaskId()).notes("error: " + e.getMessage()).completedAt(Instant.now()).build();
      results.put(task.getTaskId(), tr);
      return tr;
    }
  }

  public Map<String, Object> scoreboard() {
    Map<String, Object> s = new HashMap<>();
    double totalDelta = results.values().stream().mapToDouble(TaskResult::getDeltaF1).sum();
    s.put("agents", new ArrayList<>(agents.values()));
    s.put("queueDepth", queue.size());
    s.put("tasks", results.size());
    s.put("deltaF1Sum", totalDelta);
    return s;
  }

  public Map<String, TaskResult> getResults() {
    return new HashMap<>(results);
  }
}



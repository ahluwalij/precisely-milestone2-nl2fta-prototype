package com.nl2fta.classifier.controller.optimization;

import com.nl2fta.classifier.dto.optimization.AgentGuide;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guide")
@Tag(name = "Agent Guide", description = "Serve and update optimization guide for agents")
public class GuideController {

  private final AtomicReference<AgentGuide> current = new AtomicReference<>(
      AgentGuide.builder()
          .name("Default Optimization Guide")
          .version("v0")
          .objective("Reach F1=1.0 across all datasets by optimizing finite list types and header regex")
          .datasets(List.of(
              "example/example.csv",
              "banking/banking.csv",
              "insurance/insurance.csv",
              "transactions/transactions.csv",
              "telco_5GTraffic/telco_5GTraffic.csv",
              "telco_customer_churn/telco_customer_churn.csv"
          ))
          .priorityDescriptions(List.of("3", "4", "6"))
          .constraints(new HashMap<>(Map.of(
              "persistOnlyOnImprovement", true,
              "vectorIndexEnabled", true,
              "requireFinite", false,
              "maxIterationsPerTask", 3
          )))
          .playbook(List.of(
              AgentGuide.PlaybookStep.builder()
                  .id("baseline_f1")
                  .description("Compute baseline F1 for dataset using provided ground truth")
                  .parameters(Map.of("endpoint", "/api/optimization/f1"))
                  .build(),
              AgentGuide.PlaybookStep.builder()
                  .id("optimize_then_eval")
                  .description("Run optimize-and-eval with autoLearn=true, persist=true; rollback if no gain")
                  .parameters(Map.of("endpoint", "/api/optimization/optimize-and-eval"))
                  .build(),
              AgentGuide.PlaybookStep.builder()
                  .id("report")
                  .description("Report delta F1 and notes to scoreboard via AgentsController")
                  .parameters(Map.of("endpoint", "/api/agents/scoreboard"))
                  .build()
          ))
          .build());

  @GetMapping
  @Operation(summary = "Get current agent guide")
  public ResponseEntity<AgentGuide> get() {
    return ResponseEntity.ok(current.get());
  }

  @PostMapping
  @Operation(summary = "Replace current agent guide")
  public ResponseEntity<AgentGuide> set(@Valid @RequestBody AgentGuide guide) {
    current.set(guide);
    return ResponseEntity.ok(guide);
  }
}



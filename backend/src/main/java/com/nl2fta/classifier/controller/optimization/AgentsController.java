package com.nl2fta.classifier.controller.optimization;

import com.nl2fta.classifier.dto.optimization.AgentInfo;
import com.nl2fta.classifier.dto.optimization.AgentTask;
import com.nl2fta.classifier.dto.optimization.CampaignRequest;
import com.nl2fta.classifier.dto.optimization.TaskResult;
import com.nl2fta.classifier.service.optimization.MultiAgentCoordinatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agents")
@Tag(name = "Agents", description = "Coordinate multiple optimization agents")
public class AgentsController {

  private final MultiAgentCoordinatorService coordinator;

  @PostMapping("/register")
  @Operation(summary = "Register an agent")
  public ResponseEntity<AgentInfo> register(@RequestParam String name, @RequestBody(required = false) List<String> capabilities) {
    AgentInfo info = coordinator.registerAgent(name, capabilities == null ? List.of() : capabilities);
    return ResponseEntity.ok(info);
  }

  @GetMapping("/next-task")
  @Operation(summary = "Agent pulls next task")
  public ResponseEntity<AgentTask> next(@RequestParam String agentId) {
    return ResponseEntity.ok(coordinator.nextTask(agentId));
  }

  @PostMapping("/submit-campaign")
  @Operation(summary = "Submit a batch campaign of optimization tasks")
  public ResponseEntity<Void> submitCampaign(@Valid @RequestBody CampaignRequest request) {
    coordinator.submitCampaign(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/execute")
  @Operation(summary = "Execute a pulled task now (synchronous)")
  public ResponseEntity<TaskResult> execute(@Valid @RequestBody AgentTask task) {
    TaskResult tr = coordinator.executeTask(task);
    return ResponseEntity.ok(tr);
  }

  @GetMapping("/scoreboard")
  @Operation(summary = "Scoreboard for current campaign")
  public ResponseEntity<Map<String, Object>> scoreboard() {
    return ResponseEntity.ok(coordinator.scoreboard());
  }

  @GetMapping("/results")
  @Operation(summary = "List completed task results")
  public ResponseEntity<Map<String, TaskResult>> results() {
    // Expose shallow copy to avoid mutation
    Map<String, TaskResult> out = new java.util.HashMap<>();
    coordinator.getResults().forEach(out::put);
    return ResponseEntity.ok(out);
  }
}



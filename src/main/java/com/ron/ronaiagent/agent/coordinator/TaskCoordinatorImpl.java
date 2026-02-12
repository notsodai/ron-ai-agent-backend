package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of TaskCoordinator for multi-agent coordination.
 * Supports chain, parallel, and master-worker collaboration patterns.
 *
 * @author ron-ai-agent
 */
@Component
public class TaskCoordinatorImpl implements TaskCoordinator, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TaskCoordinatorImpl.class);

    private final AgentManager agentManager;
    private final ExecutorService executorService;

    /**
     * Constructor for dependency injection.
     *
     * @param agentManager agent manager for agent lookup
     */
    public TaskCoordinatorImpl(AgentManager agentManager) {
        if (agentManager == null) {
            throw new IllegalArgumentException("AgentManager cannot be null");
        }
        this.agentManager = agentManager;
        // Thread pool for parallel execution
        this.executorService = Executors.newFixedThreadPool(10);
        log.info("TaskCoordinator initialized with thread pool size: 10");
    }

    @Override
    public CoordinationResult executeMasterWorker(
            String taskDescription,
            String masterAgentId,
            List<String> workerAgentIds) {

        long startTime = System.currentTimeMillis();

        // Parameter validation
        if (taskDescription == null || taskDescription.isBlank()) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Task description cannot be null or empty")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        if (masterAgentId == null || masterAgentId.isBlank()) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Master agent ID cannot be null or empty")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        if (workerAgentIds == null) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Worker agent IDs cannot be null")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        try {
            // Get master agent
            Optional<BaseAgent> masterOpt = agentManager.getAgent(masterAgentId);
            if (masterOpt.isEmpty()) {
                return CoordinationResult.builder()
                    .success(false)
                    .errorMessage("Master agent not found: " + masterAgentId)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }

            BaseAgent masterAgent = masterOpt.get();
            log.info("Starting master-worker coordination - Master: {}, Workers: {}",
                    masterAgentId, workerAgentIds);

            // Delegate to workers in parallel
            List<CompletableFuture<Void>> futures = workerAgentIds.stream()
                .filter(workerId -> agentManager.getAgent(workerId).isPresent())
                .map(workerId -> CompletableFuture.runAsync(() -> {
                    agentManager.getAgent(workerId).ifPresent(worker -> {
                        log.debug("Executing worker: {}", workerId);
                        worker.run(taskDescription);
                    });
                }, executorService))
                .collect(Collectors.toList());

            // Wait for all workers to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect worker results
            Map<String, String> workerResults = new java.util.LinkedHashMap<>();
            for (String workerId : workerAgentIds) {
                agentManager.getAgent(workerId).ifPresent(worker -> {
                    // Use message history to get output
                    String output = extractLastMessage(worker);
                    workerResults.put(workerId, output);
                });
            }

            // Master aggregates results
            String aggregatedResult = aggregateResults(workerResults);

            log.info("Master-worker coordination completed - Master: {}, Workers executed: {}",
                    masterAgentId, workerResults.size());

            return CoordinationResult.builder()
                    .success(true)
                    .finalOutput(aggregatedResult)
                    .agentOutputs(workerResults)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error in master-worker coordination", e);
            return CoordinationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public CoordinationResult executeChain(
            String taskDescription,
            List<String> agentIds) {

        long startTime = System.currentTimeMillis();

        // Parameter validation
        if (taskDescription == null || taskDescription.isBlank()) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Task description cannot be null or empty")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        if (agentIds == null) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Agent IDs cannot be null")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        try {
            String currentTask = taskDescription;
            Map<String, String> agentOutputs = new java.util.LinkedHashMap<>();

            log.info("Starting chain coordination - Agents: {}, Steps: {}",
                    agentIds, agentIds.size());

            for (String agentId : agentIds) {
                // Get agent
                Optional<BaseAgent> agentOpt = agentManager.getAgent(agentId);
                if (agentOpt.isEmpty()) {
                    log.warn("Agent not found in chain: {}", agentId);
                    return CoordinationResult.builder()
                        .success(false)
                        .errorMessage("Agent not found: " + agentId)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
                }

                // Run agent
                BaseAgent agent = agentOpt.get();
                log.debug("Executing agent in chain: {} with task: {}", agentId,
                          currentTask.substring(0, Math.min(50, currentTask.length())));
                String result = agent.run(currentTask);

                // Extract last step output from full result
                String lastStepOutput = extractLastStepOutput(result);
                agentOutputs.put(agentId, lastStepOutput);

                // Update task for next agent
                currentTask = lastStepOutput; // Pass output to next agent
            }

            // Final output is last agent's output
            String finalOutput = agentOutputs.isEmpty() ? "No output" :
                new java.util.ArrayList<>(agentOutputs.values()).get(agentOutputs.size() - 1);

            log.info("Chain coordination completed - Final output length: {}",
                    finalOutput.length());

            return CoordinationResult.builder()
                    .success(true)
                    .finalOutput(finalOutput)
                    .agentOutputs(agentOutputs)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error in chain coordination", e);
            return CoordinationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public CoordinationResult executeParallel(
            String taskDescription,
            List<String> agentIds) {

        long startTime = System.currentTimeMillis();

        // Parameter validation
        if (taskDescription == null || taskDescription.isBlank()) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Task description cannot be null or empty")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        if (agentIds == null) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Agent IDs cannot be null")
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }

        try {
            log.info("Starting parallel coordination - Agents: {}", agentIds);

            // Execute all agents in parallel
            List<CompletableFuture<Map.Entry<String, String>>> futures = agentIds.stream()
                .filter(agentId -> agentManager.getAgent(agentId).isPresent())
                .map(agentId -> CompletableFuture.supplyAsync(() -> {
                    Optional<BaseAgent> agentOpt = agentManager.getAgent(agentId);
                    if (agentOpt.isEmpty()) {
                        return Map.entry(agentId, "Agent not found");
                    }

                    BaseAgent agent = agentOpt.get();
                    log.debug("Executing agent in parallel: {}", agentId);
                    String result = agent.run(taskDescription);
                    String output = extractLastStepOutput(result);

                    return Map.entry(agentId, output);
                }, executorService))
                .collect(Collectors.toList());

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Aggregate results
            Map<String, String> agentOutputs = new java.util.LinkedHashMap<>();
            for (var future : futures) {
                Map.Entry<String, String> result = future.get();
                agentOutputs.put(result.getKey(), result.getValue());
            }

            // Aggregate results
            String aggregatedResult = agentOutputs.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));

            log.info("Parallel coordination completed - Agents executed: {}",
                    agentOutputs.size());

            return CoordinationResult.builder()
                    .success(true)
                    .finalOutput(aggregatedResult)
                    .agentOutputs(agentOutputs)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error in parallel coordination", e);
            return CoordinationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public CoordinationResult execute(
            String taskDescription,
            CollaborationPattern pattern,
            List<String> agentIds) {

        // Parameter validation
        if (pattern == null) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Collaboration pattern cannot be null")
                .executionTimeMs(0L)
                .build();
        }

        if (agentIds == null || agentIds.isEmpty()) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage("Agent IDs cannot be null or empty")
                .executionTimeMs(0L)
                .build();
        }

        // Route to specific implementation based on pattern
        return switch (pattern) {
            case MASTER_WORKER -> {
                String masterId = agentIds.isEmpty() ? null : agentIds.get(0);
                List<String> workers = agentIds.size() > 1 ? agentIds.subList(1, agentIds.size()) : List.of();
                yield executeMasterWorker(taskDescription, masterId, workers);
            }
            case CHAIN -> executeChain(taskDescription, agentIds);
            case PARALLEL -> executeParallel(taskDescription, agentIds);
            default -> {
                log.warn("Unsupported collaboration pattern: {}", pattern);
                yield CoordinationResult.builder()
                    .success(false)
                    .errorMessage("Pattern not implemented: " + pattern)
                    .executionTimeMs(0L)
                    .build();
            }
        };
    }

    /**
     * Extracts the last message from an agent's message history.
     *
     * @param agent agent to extract message from
     * @return last message or "No output" if no messages exist
     */
    private String extractLastMessage(BaseAgent agent) {
        var messages = agent.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "No output";
        }

        // Get the last message content
        var lastMessage = messages.get(messages.size() - 1);
        if (lastMessage == null) {
            return "No output";
        }

        // Spring AI Message interface uses getText() method
        return lastMessage.getText();
    }

    /**
     * Extracts the last step output from an agent's full execution result.
     * The run() method returns a multi-line string with all steps.
     *
     * @param fullResult full result from agent.run()
     * @return last step output or full result if parsing fails
     */
    private String extractLastStepOutput(String fullResult) {
        if (fullResult == null || fullResult.isBlank()) {
            return "No output";
        }

        // Split by newlines and find last step
        String[] lines = fullResult.split("\n");

        // Look for last Step line (format: "StepN: output")
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("Step") && line.contains(":")) {
                int colonIndex = line.indexOf(":");
                if (colonIndex < line.length() - 1) {
                    return line.substring(colonIndex + 1).trim();
                }
            }
        }

        // If no step format found, return last non-empty line
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.startsWith("Terminated:")) {
                return line;
            }
        }

        return fullResult;
    }

    /**
     * Aggregates results from multiple agents into a single string.
     *
     * @param results map of agent IDs to their outputs
     * @return aggregated result string
     */
    private String aggregateResults(Map<String, String> results) {
        if (results == null || results.isEmpty()) {
            return "No results";
        }

        return results.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Shutdown the executor service gracefully.
     * Called by Spring container when bean is destroyed.
     */
    @Override
    public void destroy() {
        log.info("Shutting down TaskCoordinator thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Thread pool did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Thread pool shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("TaskCoordinator thread pool shut down successfully");
    }

    /**
     * Public method to manually trigger shutdown for testing purposes.
     */
    public void shutdownForTest() {
        destroy();
    }
}

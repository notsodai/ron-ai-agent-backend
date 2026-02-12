package com.ron.ronaiagent.agent.coordinator;

import java.util.List;

/**
 * Coordinates multi-agent collaboration for task execution.
 *
 * <p>This interface defines the contract for orchestrating multiple agents
 * to work together on complex tasks using various coordination patterns.
 *
 * <p>Supported coordination patterns:
 * <ul>
 *   <li>Master-Worker: One coordinator agent delegates tasks to specialized workers</li>
 *   <li>Chain: Agents execute sequentially, each building on previous outputs</li>
 *   <li>Parallel: Multiple agents work independently on different aspects</li>
 *   <li>Generic: Execute with any supported {@link CollaborationPattern}</li>
 * </ul>
 *
 * <p>Implementations should integrate with {@link AgentManager} to access
 * registered agents and manage their lifecycle during coordination.
 *
 * @see CoordinationResult
 * @see CollaborationPattern
 * @see AgentManager
 */
public interface TaskCoordinator {

    /**
     * Execute a task using the master-worker coordination pattern.
     *
     * <p>In this pattern, the master agent coordinates and delegates subtasks
     * to worker agents, which execute independently. The master agent
     * aggregates worker outputs to produce the final result.
     *
     * @param taskDescription The task to execute
     * @param masterAgentId The unique identifier of the coordinator (master) agent
     * @param workerAgentIds The unique identifiers of the worker agents
     * @return CoordinationResult containing success status, final output, individual agent outputs, and execution time
     * @throws IllegalArgumentException if masterAgentId is not registered or workerAgentIds is empty
     */
    CoordinationResult executeMasterWorker(
        String taskDescription,
        String masterAgentId,
        List<String> workerAgentIds
    );

    /**
     * Execute a task using the chain coordination pattern.
     *
     * <p>In this pattern, agents execute sequentially in the order specified.
     * Each agent receives the output of the previous agent as input,
     * building progressively toward the final result.
     *
     * @param taskDescription The task to execute
     * @param agentIds The unique identifiers of agents in execution order
     * @return CoordinationResult containing success status, final output, individual agent outputs, and execution time
     * @throws IllegalArgumentException if agentIds is empty or contains unregistered agents
     */
    CoordinationResult executeChain(
        String taskDescription,
        List<String> agentIds
    );

    /**
     * Execute a task using the parallel coordination pattern.
     *
     * <p>In this pattern, all agents execute independently and concurrently
     * on the same task. Their outputs are aggregated to produce the final result.
     * This pattern is useful for tasks that can be decomposed into
     * independent subtasks.
     *
     * @param taskDescription The task to execute
     * @param agentIds The unique identifiers of agents to execute in parallel
     * @return CoordinationResult containing success status, final output, individual agent outputs, and execution time
     * @throws IllegalArgumentException if agentIds is empty or contains unregistered agents
     */
    CoordinationResult executeParallel(
        String taskDescription,
        List<String> agentIds
    );

    /**
     * Execute a task with a custom coordination pattern.
     *
     * <p>This generic method allows execution with any supported coordination pattern,
     * providing flexibility for custom orchestration strategies.
     *
     * @param taskDescription The task to execute
     * @param pattern The coordination pattern to use
     * @param agentIds The unique identifiers of agents to execute
     * @return CoordinationResult containing success status, final output, individual agent outputs, and execution time
     * @throws IllegalArgumentException if pattern is not supported or agentIds is invalid
     * @throws UnsupportedOperationException if the specified pattern is not implemented
     */
    CoordinationResult execute(
        String taskDescription,
        CollaborationPattern pattern,
        List<String> agentIds
    );
}

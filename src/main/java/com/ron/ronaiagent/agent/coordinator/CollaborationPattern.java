package com.ron.ronaiagent.agent.coordinator;

public enum CollaborationPattern {
    /**
     * Master-Worker: One coordinator agent delegates tasks to specialized worker agents
     */
    MASTER_WORKER,

    /**
     * Chain: Agents execute sequentially, each building on the previous agent's output
     */
    CHAIN,

    /**
     * Parallel: Multiple agents work independently on different aspects of a task
     */
    PARALLEL,

    /**
     * Hierarchy: High-level planning agents coordinate lower-level execution agents
     */
    HIERARCHY,

    /**
     * RoundRobin: Task distributed equally among agents in rotation
     */
    ROUND_ROBIN
}

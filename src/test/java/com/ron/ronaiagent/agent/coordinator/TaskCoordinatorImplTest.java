package com.ron.ronaiagent.agent.coordinator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class TaskCoordinatorImplTest {
    private TaskCoordinator taskCoordinator;
    private AgentManager agentManager;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManager() {
            private final Map<String, com.ron.ronaiagent.agent.BaseAgent> agents = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public void registerAgent(String name, com.ron.ronaiagent.agent.BaseAgent agent) {
                agents.put(name, agent);
            }

            @Override
            public void unregisterAgent(String name) {
                agents.remove(name);
            }

            @Override
            public java.util.Optional<com.ron.ronaiagent.agent.BaseAgent> getAgent(String name) {
                return java.util.Optional.ofNullable(agents.get(name));
            }

            @Override
            public java.util.List<String> getAgentNames() {
                return new java.util.ArrayList<>(agents.keySet());
            }

            @Override
            public java.util.List<com.ron.ronaiagent.agent.BaseAgent> getAllAgents() {
                return new java.util.ArrayList<>(agents.values());
            }

            @Override
            public boolean isAgentRegistered(String name) {
                return agents.containsKey(name);
            }

            @Override
            public int getAgentCount() {
                return agents.size();
            }
        };

        taskCoordinator = new TaskCoordinatorImpl(agentManager);
    }

    @Test
    void testChainExecution() {
        // Register test agents
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));
        agentManager.registerAgent("agent2", createMockAgent("Agent 2", "Agent 2 output"));

        var result = taskCoordinator.executeChain(
            "Test task",
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAgentOutputs().size());
        assertEquals("Agent 2 output", result.getFinalOutput());
    }

    @Test
    void testParallelExecution() {
        // Register test agents
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));
        agentManager.registerAgent("agent2", createMockAgent("Agent 2", "Agent 2 output"));

        var result = taskCoordinator.executeParallel(
            "Test task",
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAgentOutputs().size());
    }

    @Test
    void testNonExistentAgent() {
        var result = taskCoordinator.executeChain(
            "Test task",
            List.of("nonexistent")
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testAgentNotRegistered() {
        // Don't register agent
        var result = taskCoordinator.executeChain(
            "Test task",
            List.of("agent1")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("not found"));
    }

    @Test
    void testMasterWorkerExecution() {
        agentManager.registerAgent("master", createMockAgent("Master", "Master output"));
        agentManager.registerAgent("worker1", createMockAgent("Worker 1", "Worker 1 output"));
        agentManager.registerAgent("worker2", createMockAgent("Worker 2", "Worker 2 output"));

        var result = taskCoordinator.executeMasterWorker(
            "Test task",
            "master",
            List.of("worker1", "worker2")
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAgentOutputs().size());
    }

    @Test
    void testExecuteWithChainPattern() {
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));
        agentManager.registerAgent("agent2", createMockAgent("Agent 2", "Agent 2 output"));

        var result = taskCoordinator.execute(
            "Test task",
            CollaborationPattern.CHAIN,
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void testExecuteWithParallelPattern() {
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));
        agentManager.registerAgent("agent2", createMockAgent("Agent 2", "Agent 2 output"));

        var result = taskCoordinator.execute(
            "Test task",
            CollaborationPattern.PARALLEL,
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void testExecuteWithMasterWorkerPattern() {
        agentManager.registerAgent("master", createMockAgent("Master", "Master output"));
        agentManager.registerAgent("worker1", createMockAgent("Worker 1", "Worker 1 output"));

        var result = taskCoordinator.execute(
            "Test task",
            CollaborationPattern.MASTER_WORKER,
            List.of("master", "worker1")
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void testEmptyAgentList() {
        var result = taskCoordinator.executeChain(
            "Test task",
            List.of()
        );

        assertTrue(result.isSuccess());
        assertEquals("No output", result.getFinalOutput());
    }

    @Test
    void testExecutionTimeIsRecorded() {
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));

        var result = taskCoordinator.executeChain(
            "Test task",
            List.of("agent1")
        );

        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    void testExecutorServiceShutdown() {
        // Register test agents
        agentManager.registerAgent("agent1", createMockAgent("Agent 1", "Agent 1 output"));
        agentManager.registerAgent("agent2", createMockAgent("Agent 2", "Agent 2 output"));

        // Execute parallel coordination
        var result = taskCoordinator.executeParallel(
            "Test task",
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());

        // Manually trigger shutdown to verify it works
        ((TaskCoordinatorImpl) taskCoordinator).shutdownForTest();

        // After shutdown, executor should be terminated
        // Note: In production, @PreDestroy handles this automatically
    }

    private com.ron.ronaiagent.agent.BaseAgent createMockAgent(String name, String response) {
        return new com.ron.ronaiagent.agent.BaseAgent() {
            {
                setName(name);
            }

            @Override
            public String step() {
                // Simulate agent execution
                return response;
            }

            @Override
            public void cleanup() {
                // No-op for test
            }
        };
    }
}

package com.ron.ronaiagent.agent.specialized;

import cn.hutool.core.util.StrUtil;
import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * Coordinator Agent - Specialized agent for task distribution and result aggregation
 *
 * This agent handles coordination-related tasks including:
 * - Understanding complex user requirements
 * - Breaking down tasks into subtasks
 * - Delegating subtasks to appropriate specialized agents
 * - Aggregating results from multiple agents
 * - Ensuring quality and coherence of final output
 *
 * Coordination patterns:
 * - Master-Worker: Delegate to specialized workers
 * - Chain: Sequential processing with handoffs
 * - Parallel: Distribute independent subtasks
 *
 * @author admin
 * @date 2025/10/12
 */
public class CoordinatorAgent extends ToolCallAgent {

    /**
     * Default maximum steps for agent execution
     */
    public static final int DEFAULT_MAX_STEPS = 10;

    private static final String SYSTEM_PROMPT = """
        You are a Coordinator Agent responsible for task distribution and result aggregation.

        Your responsibilities include:
        - Understanding complex user requirements
        - Breaking down tasks into subtasks
        - Delegating subtasks to appropriate specialized agents
        - Aggregating results from multiple agents
        - Ensuring quality and coherence of final output

        Available coordination patterns:
        - Master-Worker: Delegate to specialized workers
        - Chain: Sequential processing with handoffs
        - Parallel: Distribute independent subtasks

        Always:
        - Clearly communicate the task breakdown
        - Explain why specific agents are chosen
        - Synthesize results into coherent responses
        - Handle agent failures gracefully
        """;

    private static final String NEXT_STEP_PROMPT = """
        Continue coordinating the task.
        Monitor agent progress and aggregate results.
        Ensure all subtasks are completed and results are coherent.
        Use the terminate tool when coordination is complete.
        """;

    private final AgentManager agentManager;

    /**
     * Constructor for CoordinatorAgent
     *
     * @param agentId Unique identifier for this agent (must not be blank)
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @param agentManager AgentManager for coordinating other agents (must not be null)
     * @param availableTools Array of tool callbacks available to this agent
     * @throws IllegalArgumentException if agentId is null or blank
     * @throws NullPointerException if agentManager is null
     */
    public CoordinatorAgent(String agentId, ChatModel chatModel, AgentManager agentManager, ToolCallback[] availableTools) {
        super(availableTools);

        // Validate parameters
        if (StrUtil.isBlank(agentId)) {
            throw new IllegalArgumentException("agentId must not be null or blank");
        }
        if (agentManager == null) {
            throw new NullPointerException("agentManager must not be null");
        }

        this.agentManager = agentManager;
        configureAgent(agentId, chatModel);
    }

    /**
     * Constructor with null tools (for testing purposes)
     *
     * @param agentId Unique identifier for this agent (must not be blank)
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @param agentManager AgentManager for coordinating other agents (must not be null)
     * @throws IllegalArgumentException if agentId is null or blank
     * @throws NullPointerException if agentManager is null
     */
    public CoordinatorAgent(String agentId, ChatModel chatModel, AgentManager agentManager) {
        this(agentId, chatModel, agentManager, null);
    }

    /**
     * Configure the agent with system prompt and chat client
     *
     * @param agentId Unique identifier for this agent
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     */
    private void configureAgent(String agentId, ChatModel chatModel) {
        // Set agent identification
        setName(agentId);
        setDescription("Coordinator Agent for task distribution and result aggregation");

        // Set prompts
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);

        // Set max steps using constant
        setMaxSteps(DEFAULT_MAX_STEPS);

        // Initialize chat client if chatModel is provided
        // Note: chatModel can be null for testing purposes, which is acceptable
        if (chatModel != null) {
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(new MyLoggerAdvisor())
                    .build();
            setChatClient(chatClient);
        }
        // If chatModel is null, the agent will need to have chatClient set manually before use
        // This is intentional for test scenarios where we may not have a full Spring context
    }

    /**
     * Get the AgentManager for coordinating other agents
     *
     * @return AgentManager instance
     */
    public AgentManager getAgentManager() {
        return agentManager;
    }
}

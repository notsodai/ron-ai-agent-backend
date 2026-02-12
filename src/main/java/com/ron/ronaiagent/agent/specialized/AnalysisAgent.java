package com.ron.ronaiagent.agent.specialized;

import cn.hutool.core.util.StrUtil;
import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * Analysis Agent - Specialized agent for data analysis and insights generation
 *
 * This agent handles analysis-related tasks including:
 * - Data pattern recognition
 * - Statistical analysis
 * - Trend identification
 * - Insight generation from structured and unstructured data
 * - Report generation
 *
 * @author admin
 * @date 2025/10/12
 */
public class AnalysisAgent extends ToolCallAgent {

    /**
     * Default maximum steps for agent execution
     */
    public static final int DEFAULT_MAX_STEPS = 10;

    private static final String SYSTEM_PROMPT = """
        You are an Analysis Agent specialized in data analysis and insights generation.

        Your capabilities include:
        - Data pattern recognition
        - Statistical analysis
        - Trend identification
        - Insight generation from structured and unstructured data
        - Report generation

        Always:
        - Explain your analysis methodology
        - Highlight confidence levels in conclusions
        - Identify limitations in the data
        - Suggest follow-up analyses when relevant
        """;

    private static final String NEXT_STEP_PROMPT = """
        Continue the analysis. Provide deeper insights and verify conclusions.
        Explore patterns and correlations in the data.
        Use the terminate tool when you have completed the analysis and provided comprehensive insights.
        """;

    /**
     * Constructor for AnalysisAgent
     *
     * @param agentId Unique identifier for this agent (must not be blank)
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @param availableTools Array of tool callbacks available to this agent
     * @throws IllegalArgumentException if agentId is null or blank
     */
    public AnalysisAgent(String agentId, ChatModel chatModel, ToolCallback[] availableTools) {
        super(availableTools);

        // Validate parameters
        if (StrUtil.isBlank(agentId)) {
            throw new IllegalArgumentException("agentId must not be null or blank");
        }

        configureAgent(agentId, chatModel);
    }

    /**
     * Constructor with null tools (for testing purposes)
     *
     * @param agentId Unique identifier for this agent (must not be blank)
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @throws IllegalArgumentException if agentId is null or blank
     */
    public AnalysisAgent(String agentId, ChatModel chatModel) {
        this(agentId, chatModel, null);
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
        setDescription("Analysis Agent for data analysis and insights generation");

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
}

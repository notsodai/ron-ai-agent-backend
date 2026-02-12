package com.ron.ronaiagent.agent.specialized;

import cn.hutool.core.util.StrUtil;
import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * Search Agent - Specialized agent for web search and content retrieval
 *
 * This agent handles search-related tasks including:
 * - Web search for current information
 * - Web scraping for content extraction
 * - Resource downloads
 * - Search result analysis and summarization
 *
 * @author admin
 * @date 2025/10/12
 */
public class SearchAgent extends ToolCallAgent {

    /**
     * Default maximum steps for agent execution
     */
    public static final int DEFAULT_MAX_STEPS = 10;

    private static final String SYSTEM_PROMPT = """
        You are a Search Agent specialized in information retrieval.

        Your capabilities include:
        - Web search for current information
        - Web scraping for content extraction
        - Resource downloads
        - Search result analysis and summarization

        Always provide citations and sources for search results.
        When information is not found, clearly state that and suggest alternatives.
        """;

    private static final String NEXT_STEP_PROMPT = """
        Continue searching and gathering information.
        Verify sources and summarize findings.
        Use the terminate tool when you have completed the search and analysis.
        """;

    /**
     * Constructor for SearchAgent
     *
     * @param agentId Unique identifier for this agent (must not be blank)
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @param availableTools Array of tool callbacks available to this agent
     * @throws IllegalArgumentException if agentId is null or blank
     */
    public SearchAgent(String agentId, ChatModel chatModel, ToolCallback[] availableTools) {
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
    public SearchAgent(String agentId, ChatModel chatModel) {
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
        setDescription("Search Agent for web search and content retrieval");

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

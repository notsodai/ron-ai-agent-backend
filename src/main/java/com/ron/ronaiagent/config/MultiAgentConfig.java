package com.ron.ronaiagent.config;

import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.agent.specialized.AnalysisAgent;
import com.ron.ronaiagent.agent.specialized.CoordinatorAgent;
import com.ron.ronaiagent.agent.specialized.FileProcessingAgent;
import com.ron.ronaiagent.agent.specialized.SearchAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Multi-Agent Configuration for automatic agent registration.
 *
 * This configuration class provides factory methods for creating and configuring
 * all specialized agents in the multi-agent system. It handles:
 *
 * - FileProcessingAgent: File operations with configurable base directory
 * - SearchAgent: Web search and content retrieval
 * - AnalysisAgent: Data analysis and insights generation
 * - CoordinatorAgent: Task distribution and result aggregation
 *
 * All agents are automatically registered as Spring beans and can be
 * dependency-injected throughout the application.
 *
 * Coordination and Integration Layer (Phase 5, Task 1)
 *
 * @author admin
 * @date 2025/10/12
 */
@Configuration
public class MultiAgentConfig {

    /**
     * Base directory for file processing agent operations.
     * Can be configured via application properties with key: agent.file.base-directory
     * Defaults to /tmp/file-processing if not specified.
     */
    @Value("${agent.file.base-directory:/tmp/file-processing}")
    private String fileBaseDirectory;

    private final ChatModel chatModel;

    private final AgentManager agentManager;

    /**
     * Constructor with dependency injection.
     *
     * @param chatModel ChatModel for LLM interactions (auto-injected by Spring)
     * @param agentManager AgentManager for coordination (auto-injected from AgentManagerImpl)
     */
    public MultiAgentConfig(ChatModel chatModel, AgentManager agentManager) {
        this.chatModel = chatModel;
        this.agentManager = agentManager;
    }

    /**
     * Creates and configures the FileProcessingAgent bean.
     *
     * Agent ID: "file-processor"
     * Capabilities: File operations including reading, writing, PDF generation, and directory management
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @return Configured FileProcessingAgent instance
     */
    @Bean
    public FileProcessingAgent fileProcessingAgent(ToolCallback[] allTools) {
        return new FileProcessingAgent(
                "file-processor",
                chatModel,
                fileBaseDirectory,
                allTools
        );
    }

    /**
     * Creates and configures the SearchAgent bean.
     *
     * Agent ID: "search-agent"
     * Capabilities: Web search, content extraction, resource downloads, and search result analysis
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @return Configured SearchAgent instance
     */
    @Bean
    public SearchAgent searchAgent(ToolCallback[] allTools) {
        return new SearchAgent(
                "search-agent",
                chatModel,
                allTools
        );
    }

    /**
     * Creates and configures the AnalysisAgent bean.
     *
     * Agent ID: "analysis-agent"
     * Capabilities: Data analysis, pattern recognition, statistical analysis, and insights generation
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @return Configured AnalysisAgent instance
     */
    @Bean
    public AnalysisAgent analysisAgent(ToolCallback[] allTools) {
        return new AnalysisAgent(
                "analysis-agent",
                chatModel,
                allTools
        );
    }

    /**
     * Creates and configures the CoordinatorAgent bean.
     *
     * Agent ID: "coordinator"
     * Capabilities: Task distribution, result aggregation, and multi-agent coordination
     *
     * The CoordinatorAgent uses the AgentManager to coordinate with other specialized agents
     * for handling complex multi-step tasks.
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @return Configured CoordinatorAgent instance
     */
    @Bean
    public CoordinatorAgent coordinatorAgent(ToolCallback[] allTools) {
        return new CoordinatorAgent(
                "coordinator",
                chatModel,
                agentManager,
                allTools
        );
    }
}

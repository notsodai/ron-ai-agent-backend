package com.ron.ronaiagent.config;

import com.ron.ronaiagent.agent.RonManus;
import com.ron.ronaiagent.agent.specialized.FileProcessingAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for agent beans
 *
 * This class provides factory methods for creating specialized agent instances
 * with proper Spring dependency injection and configuration properties.
 *
 * @author admin
 * @date 2025/10/12
 */
@Configuration
public class AgentConfiguration {

    /**
     * Default maximum steps for agent execution
     */
    public static final int DEFAULT_MAX_STEPS = 10;

    /**
     * Base directory for file processing agent operations
     * Can be configured via application properties with key: agent.file.base-directory
     * Defaults to /tmp/file-processing if not specified
     */
    @Value("${agent.file.base-directory:/tmp/file-processing}")
    private String fileBaseDirectory;

    /**
     * Creates and configures the RonManus agent bean
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @param dashScopeChatModel ChatModel for LLM interactions
     * @return Configured RonManus agent instance
     */
    @Bean
    public RonManus ronManus(ToolCallback[] allTools, ChatModel dashScopeChatModel) {
        return new RonManus(allTools, dashScopeChatModel);
    }

    /**
     * Creates and configures the FileProcessingAgent bean
     *
     * @param allTools Auto-registered tool callbacks from ToolRegistration
     * @param dashScopeChatModel ChatModel for LLM interactions
     * @return Configured FileProcessingAgent instance
     */
    @Bean
    public FileProcessingAgent fileProcessingAgent(ToolCallback[] allTools, ChatModel dashScopeChatModel) {
        return new FileProcessingAgent("file-processor", dashScopeChatModel, fileBaseDirectory, allTools);
    }
}

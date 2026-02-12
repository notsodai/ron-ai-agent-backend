package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * File Processing Agent - Specialized agent for file operations
 *
 * This agent handles file-related tasks including:
 * - Reading and writing files
 * - Creating PDF documents
 * - Managing directory structures
 * - File format conversions
 *
 * @author admin
 * @date 2025/10/12
 */
@Component
public class FileProcessingAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
        You are a File Processing Agent specialized in handling file operations.

        Your capabilities include:
        - Reading and writing files
        - Creating PDF documents
        - Managing directory structures
        - File format conversions

        Always verify file paths and ensure safe operations.
        When tasks involve sensitive operations, explain what you're doing before executing.
        """;

    private static final String NEXT_STEP_PROMPT = """
        Based on the user's file operation request, select the appropriate tools.
        Always verify file paths exist before operations.
        For file modifications, clearly explain the changes you're making.
        Use the terminate tool when the file operation is complete.
        """;

    private final String baseDirectory;

    /**
     * Constructor for FileProcessingAgent
     *
     * @param agentId Unique identifier for this agent
     * @param chatModel ChatModel for LLM interactions (can be null for testing)
     * @param baseDirectory Base directory for file operations
     * @param availableTools Array of tool callbacks available to this agent
     */
    public FileProcessingAgent(String agentId, ChatModel chatModel, String baseDirectory, ToolCallback[] availableTools) {
        super(availableTools);
        this.baseDirectory = baseDirectory;
        configureAgent(agentId, chatModel);
    }

    /**
     * Constructor with null tools (for testing purposes)
     */
    public FileProcessingAgent(String agentId, ChatModel chatModel, String baseDirectory) {
        this(agentId, chatModel, baseDirectory, null);
    }

    /**
     * Configure the agent with system prompt and chat client
     */
    private void configureAgent(String agentId, ChatModel chatModel) {
        // Set agent identification
        setName(agentId);
        setDescription("Specialized agent for file operations including reading, writing, PDF generation, and directory management");

        // Set prompts
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);

        // Set max steps
        setMaxSteps(10);

        // Initialize chat client if chatModel is provided
        if (chatModel != null) {
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(new MyLoggerAdvisor())
                    .build();
            setChatClient(chatClient);
        }
    }

    /**
     * Get the base directory for file operations
     *
     * @return Base directory path
     */
    public String getBaseDirectory() {
        return baseDirectory;
    }
}

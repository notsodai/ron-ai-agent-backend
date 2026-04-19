package com.ron.ronaiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author admin
 * @date 2025/10/12 下午2:39
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ToolCallAgent extends ReActAgent {
    /**
     * Available tools list.
     */
    private ToolCallback[] availableTools;
    /**
     * Tool call results.
     */
    private ChatResponse toolCallResponse;
    /**
     * Tool calling manager.
     */
    private final ToolCallingManager toolCallingManager;
    /**
     * Chat options — disables built-in tool execution to manually control context.
     */
    private final ChatOptions chatOptions;
    /**
     * Retry policy for tool execution failures.
     */
    private ToolExecutionRetryPolicy retryPolicy = ToolExecutionRetryPolicy.defaultPolicy();

    Logger logger = LoggerFactory.getLogger(ToolCallAgent.class);

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessages().add(userMessage);
        }
        List<Message> messages = getMessages();
        Prompt prompt = new Prompt(messages, chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            this.toolCallResponse = chatResponse;
            if (toolCallResponse != null && toolCallResponse.getResult() != null) {
                AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
                String result = assistantMessage.getText();
                List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                logger.info("{} think: {}", getName(), result);
                logger.info("{} selected {} tool(s)", getName(), toolCalls.size());
                String toolCallInfo = toolCalls.stream()
                        .map(toolCall -> String.format("Tool: %s, Args: %s", toolCall.getClass(), toolCall.arguments()))
                        .collect(Collectors.joining("\n"));
                logger.info("{} toolCallInfo: {}", getName(), toolCallInfo);
                if (toolCalls.isEmpty()) {
                    getMessages().add(assistantMessage);
                    return false;
                } else {
                    return true;
                }
            }
        } catch (Exception e) {
            String rootCause = extractRootCause(e);
            logger.error("{}: think phase error — root cause: {}", getName(), rootCause, e);
            String errorMsg = String.format(
                    "Thinking phase error. Root cause: %s. Safe retry: rephrase the query. Stop condition: if error persists after 2 retries.",
                    rootCause);
            getMessages().add(new AssistantMessage(errorMsg));
            return false;
        }
        return false;
    }

    @Override
    public String act() {
        if (!toolCallResponse.hasToolCalls()) {
            return "No tool calls requested";
        }

        int attempt = 0;
        Exception lastError = null;

        while (retryPolicy.shouldRetry(attempt)) {
            attempt++;
            try {
                Prompt prompt = new Prompt(getMessages(), chatOptions);
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
                setMessages(toolExecutionResult.conversationHistory());

                List<Message> history = toolExecutionResult.conversationHistory();
                if (!history.isEmpty() && history.getLast() instanceof ToolResponseMessage toolResponseMessage) {
                    String results = toolResponseMessage.getResponses().stream()
                            .filter(Objects::nonNull)
                            .map(response -> "Tool " + response.name() + " completed. Result: " + response.responseData())
                            .collect(Collectors.joining("\n"));

                    logger.info("{} act result: {}", getName(), results);

                    boolean hasTerminateTool = toolResponseMessage.getResponses().stream()
                            .anyMatch(response -> response != null &&
                                    ("doTerminate".equals(response.name()) ||
                                            response.name().toLowerCase().contains("terminate")));

                    if (hasTerminateTool) {
                        logger.info("{} terminated via TerminateTool", getName());
                        setAgentState(AgentState.FINISHED);
                    }
                    return results;
                } else {
                    logger.warn("{}: no valid ToolResponseMessage after tool execution", getName());
                    return "Tool execution completed but no valid response found";
                }
            } catch (Exception e) {
                lastError = e;
                logger.warn("{}: tool execution attempt {}/{} failed: {}",
                        getName(), attempt, retryPolicy.shouldRetry(attempt + 1) ? attempt + 1 : attempt,
                        e.getMessage());
                if (retryPolicy.shouldRetry(attempt + 1)) {
                    try {
                        Thread.sleep(retryPolicy.getBackoffMs(attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.error("{}: tool execution failed after {} attempts", getName(), attempt, lastError);
        String errorMsg = "Tool execution failed after " + attempt + " attempts: " +
                (lastError != null ? lastError.getMessage() : "unknown error");
        getMessages().add(new AssistantMessage(errorMsg));
        return errorMsg;
    }

    @Override
    public void cleanup() {
    }

    private String extractRootCause(Throwable t) {
        Throwable cause = t;
        int depth = 0;
        while (cause.getCause() != null && depth < 5) {
            cause = cause.getCause();
            depth++;
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}

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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author admin
 * @date 2025/10/12 下午2:39
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Component
public class ToolCallAgent extends ReActAgent {
    /**
     * 可用工具列表
     */
    private ToolCallback[] availableTools;
    /**
     * 工具调用结果
     */
    private ChatResponse toolCallResponse;
    /**
     * 工具调用管理器
     */
    private final ToolCallingManager toolCallingManager;
    /**
     * 聊天选项，禁用内置的工具调用机制，自己维护上下文
     */
    private final ChatOptions chatOptions;

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
        // 传入工具列表调用大模型，得到需⁢要调用的工具列表
        if (getNextStepPrompt()  != null && !getNextStepPrompt().isEmpty()){
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessages().add(userMessage);
        }
        List<Message> messages = getMessages();
        Prompt prompt = new Prompt(messages, chatOptions);
        try{
            // 获取工具选项的响应
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 获取工具调用结果用于Act
            this.toolCallResponse = chatResponse;
            // 输出提示信息
            if (toolCallResponse != null && toolCallResponse.getResult() != null){
                AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
                String result = assistantMessage.getText();
                List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                logger.info("{}的思考：{}", getName(), result);
                logger.info("{}选择了{}个工具", getName(), toolCalls.size());
                String toolCallInfo = toolCalls.stream()
                        .map(toolCall -> String.format("工具：%s,参数：%s", toolCall.getClass(), toolCall.arguments()))
                        .collect(Collectors.joining("\n"));
                logger.info("{}的toolCallInfo：{}", getName(), toolCallInfo);
                if (toolCalls.isEmpty()){
                    // 不调用工具时，添加助手消息
                    getMessages().add(assistantMessage);
                    return false;
                } else {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("{}的思考出错：{}", getName(), e.getMessage());
            getMessages().add(new AssistantMessage("处理时遇到错误" + e.getMessage()));
            return false;
        }
        return false;
    }

    @Override
    public String act() {
        if (!toolCallResponse.hasToolCalls()){
            return "没有调用工具";
        }
        try {
            // 调用工具
            Prompt prompt = new Prompt(getMessages(), chatOptions);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);

            // 记录消息上下文
            setMessages(toolExecutionResult.conversationHistory());

            // 安全地获取工具响应消息
            List<Message> conversationHistory = toolExecutionResult.conversationHistory();
            if (!conversationHistory.isEmpty() && conversationHistory.getLast() instanceof ToolResponseMessage toolResponseMessage) {

                // 处理工具响应
                String results = toolResponseMessage.getResponses().stream()
                        .filter(Objects::nonNull)
                        .map(response -> "工具" + response.name() + "完成了任务！结果: " +
                                response.responseData())
                        .collect(Collectors.joining("\n"));

                logger.info("{}的act结果：{}", getName(), results);

                // 检查是否调用了终止工具（改进的终止逻辑）
                boolean hasTerminateTool = toolResponseMessage.getResponses().stream()
                        .anyMatch(response -> response != null && ("doTerminate".equals(response.name()) || response.name().toLowerCase().contains("terminate")));

                if (hasTerminateTool) {
                    logger.info("{}已终止", getName());
                    setAgentState(AgentState.FINISHED);
                }

                return results;
            } else {
                logger.warn("{}: 工具执行后未找到有效的ToolResponseMessage", getName());
                return "工具执行完成，但未获取到有效响应";
            }
        } catch (Exception e) {
            logger.error("{}: 工具执行过程中发生错误", getName(), e);
            return "工具执行失败：" + e.getMessage();
        }
    }

    @Override
    public void cleanup() {
    }
}

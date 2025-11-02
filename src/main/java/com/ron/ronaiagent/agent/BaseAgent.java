package com.ron.ronaiagent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author admin
 * @date 2025/10/12 下午2:54
 */
@Data
public abstract class BaseAgent {
    /**
     * 代理名称
     */
    private String name;
    /**
     * 代理描述
     */
    private String description;
    /**
     * 代理系统提示
     */
    private String systemPrompt;
    /**
     * 代理下一步提示
     */
    private String nextStepPrompt;
    /**
     * 代理状态
     */
    private AgentState agentState = AgentState.IDLE;
    /**
     * LLM 模型
     */
    private ChatClient chatClient;
    /**
     * 消息记忆
     */
    private List<Message> messages = new ArrayList<>();
    /**
     * 最大步骤数
     */
    private int maxSteps = 10;
    /**
     * 当前步骤数
     */
    private int currentStep = 0;

    private static final Logger logger = LoggerFactory.getLogger(BaseAgent.class);

    public String run(String userPrompt) {
        if (agentState != AgentState.IDLE) {
            throw new RuntimeException("Agent is not idle");
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("User prompt cannot be empty");
        }
        agentState = AgentState.RUNNING;
        // 添加用户输入
        messages.add(new UserMessage(userPrompt));
        // 保存结果
        List<String> results = new ArrayList<>();
        try {
            while (currentStep < maxSteps && agentState == AgentState.RUNNING) {
                String stepResult = step();
                String result = "Step" + currentStep + ": " + stepResult;
                results.add(result);
                currentStep++;
                logger.info("Current step: {}, Result: {}", currentStep, result);
                if (currentStep >= maxSteps) {
                    agentState = AgentState.FINISHED;
                    logger.info("Agent finished");
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                }
                if (agentState == AgentState.FINISHED) {
                    break;
                }
            }
            return StrUtil.join("\n", results);
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            logger.error("Agent error", e);
            return "Error: " + e.getMessage();
        } finally {
            // 清理资源
            cleanup();
        }
    }

    /**
     * 执行单个步骤
     *
     * @return 执行结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    public abstract void cleanup();
}
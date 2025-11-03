package com.ron.ronaiagent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 流式执行
     *
     * @param userPrompt 用户输入
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        // 异步执行，避免阻塞
        CompletableFuture.runAsync(() -> {
            try {
                if (agentState != AgentState.IDLE) {
                    sseEmitter.send("Agent is not idle" + this.agentState);
                    sseEmitter.complete();
                    return;
                }
                if (userPrompt == null){
                    sseEmitter.send("User prompt cannot be empty");
                    sseEmitter.complete();
                    return;
                }
                this.agentState = AgentState.RUNNING;
                // 添加用户输入
                messages.add(new UserMessage(userPrompt));
                try {
                    for (int i = 0; i < maxSteps && agentState != AgentState.FINISHED; i++) {
                        currentStep = i + 1;
                        logger.info("Current step: {} / {}", currentStep, maxSteps);
                        String stepResult = step();
                        sseEmitter.send("Step" + currentStep + ": " + stepResult);
                    }
                    if (currentStep >= maxSteps) {
                        this.agentState = AgentState.FINISHED;
                        sseEmitter.send("Terminated: Reached max steps (" + maxSteps + ")");
                    }
                    sseEmitter.complete();
                } catch (Exception e){
                    this.agentState = AgentState.ERROR;
                    logger.error("Agent error", e);
                    try{
                        sseEmitter.send("Error: " + e.getMessage());
                        sseEmitter.complete();
                    } catch (Exception e1){
                        sseEmitter.completeWithError(e1);
                    }
                } finally {
                    cleanup();
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 超时处理
            sseEmitter.onTimeout(() -> {
                this.agentState = AgentState.ERROR;
                this.cleanup();
                logger.error("Agent timeout");
            });
            sseEmitter.onCompletion(() -> {
                this.agentState = AgentState.IDLE;
                this.cleanup();
                logger.info("Agent completed");
            });

        });
        return sseEmitter;
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
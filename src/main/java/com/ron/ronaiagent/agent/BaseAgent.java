package com.ron.ronaiagent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author admin
 * @date 2025/10/12 下午2:54
 */
@Slf4j
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

    /**
     * 代理开始时间
     */
    private LocalDateTime startTime;
    /**
     * 取消标志
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public String run(String userPrompt) {
        if (agentState != AgentState.IDLE) {
            throw new RuntimeException("Agent is not idle, current state: " + agentState);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("User prompt cannot be empty");
        }

        startTime = LocalDateTime.now();
        cancelled.set(false);
        agentState = AgentState.RUNNING;

        log.info("Starting {} execution - Prompt: {}", name, userPrompt.substring(0, Math.min(100, userPrompt.length())));

        // 添加用户输入
        messages.add(new UserMessage(userPrompt));
        // 保存结果
        List<String> results = new ArrayList<>();

        try {
            while (currentStep < maxSteps && agentState == AgentState.RUNNING && !cancelled.get()) {
                if (cancelled.get()) {
                    log.info("Agent {} execution cancelled at step {}", name, currentStep);
                    agentState = AgentState.FINISHED;
                    results.add("Execution cancelled at step " + currentStep);
                    break;
                }

                log.debug("Agent {} executing step {}/{}", name, currentStep + 1, maxSteps);
                String stepResult = step();
                String result = "Step" + currentStep + ": " + stepResult;
                results.add(result);
                currentStep++;

                log.info("Agent {} step {} completed: {}", name, currentStep, stepResult.substring(0, Math.min(100, stepResult.length())));

                if (currentStep >= maxSteps) {
                    agentState = AgentState.FINISHED;
                    log.info("Agent {} finished - reached max steps ({})", name, maxSteps);
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                }
                if (agentState == AgentState.FINISHED) {
                    break;
                }
            }

            String finalResult = StrUtil.join("\n", results);
            log.info("Agent {} execution completed - Duration: {}ms, Steps: {}",
                    name, java.time.Duration.between(startTime, LocalDateTime.now()).toMillis(), currentStep);
            return finalResult;

        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Agent {} error at step {}", name, currentStep, e);
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

        // 设置事件处理器
        sseEmitter.onTimeout(() -> {
            log.warn("Agent {} stream timeout", name);
            cancelled.set(true);
            agentState = AgentState.ERROR;
            cleanup();
        });

        sseEmitter.onCompletion(() -> {
            log.info("Agent {} stream completed", name);
            agentState = AgentState.IDLE;
            cleanup();
        });

        // 异步执行，避免阻塞
        CompletableFuture.runAsync(() -> {
            try {
                if (agentState != AgentState.IDLE) {
                    log.warn("Agent {} is not idle, current state: {}", name, agentState);
                    sseEmitter.send("Agent is not idle: " + agentState);
                    sseEmitter.complete();
                    return;
                }

                if (StrUtil.isBlank(userPrompt)) {
                    log.warn("Agent {} received empty user prompt", name);
                    sseEmitter.send("User prompt cannot be empty");
                    sseEmitter.complete();
                    return;
                }

                startTime = LocalDateTime.now();
                cancelled.set(false);
                agentState = AgentState.RUNNING;

                log.info("Starting {} stream execution - Prompt: {}", name, userPrompt.substring(0, Math.min(100, userPrompt.length())));

                // 添加用户输入
                messages.add(new UserMessage(userPrompt));

                try {
                    for (int i = 0; i < maxSteps && agentState != AgentState.FINISHED && !cancelled.get(); i++) {
                        if (cancelled.get()) {
                            log.info("Agent {} stream execution cancelled at step {}", name, i + 1);
                            sseEmitter.send("Execution cancelled at step " + (i + 1));
                            break;
                        }

                        currentStep = i + 1;
                        log.debug("Agent {} executing stream step {}/{}", name, currentStep, maxSteps);

                        String stepResult = step();
                        String stepMessage = "Step" + currentStep + ": " + stepResult;

                        sseEmitter.send(stepMessage);
                        log.info("Agent {} stream step {} completed", name, currentStep);

                        if (currentStep >= maxSteps) {
                            agentState = AgentState.FINISHED;
                            sseEmitter.send("Terminated: Reached max steps (" + maxSteps + ")");
                            log.info("Agent {} stream finished - reached max steps", name);
                            break;
                        }
                    }

                    // 发送完成标记
                    sseEmitter.send("[DONE]");

                    // 延迟完成，确保客户端收到所有数据
                    Thread.sleep(100);
                    sseEmitter.complete();

                    log.info("Agent {} stream execution completed - Duration: {}ms, Steps: {}",
                            name, java.time.Duration.between(startTime, LocalDateTime.now()).toMillis(), currentStep);

                } catch (Exception e) {
                    agentState = AgentState.ERROR;
                    log.error("Agent {} stream error at step {}", name, currentStep, e);
                    try {
                        sseEmitter.send("Error: " + e.getMessage());
                        sseEmitter.send("[ERROR]");
                        sseEmitter.complete();
                    } catch (Exception e1) {
                        log.error("Error sending error response", e1);
                        sseEmitter.completeWithError(e1);
                    }
                } finally {
                    cleanup();
                }

            } catch (Exception e) {
                log.error("Agent {} stream initialization error", name, e);
                sseEmitter.completeWithError(e);
            }
        });

        return sseEmitter;
    }

    /**
     * 取消代理执行
     */
    public void cancel() {
        log.info("Cancelling agent {} execution", name);
        cancelled.set(true);
    }

    /**
     * 获取执行持续时间
     */
    public long getExecutionDurationMillis() {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
    }

    /**
     * 获取代理状态信息
     */
    public String getStatusInfo() {
        return String.format("Agent: %s, State: %s, Step: %d/%d, Duration: %dms",
                name, agentState, currentStep, maxSteps, getExecutionDurationMillis());
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
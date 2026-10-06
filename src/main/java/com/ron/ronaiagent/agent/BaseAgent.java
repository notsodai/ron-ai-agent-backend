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
import com.ron.ronaiagent.dto.AgentEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    /**
     * Context window manager for token estimation and compaction.
     */
    private ContextWindowManager contextWindowManager = ContextWindowManager.defaultManager();
    /**
     * Execution listener for state persistence.
     */
    private AgentExecutionListener executionListener;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 当前流式执行使用的 SSE emitter。
     *
     * 只在 runStream 生命周期内有效。
     */
    private transient SseEmitter currentEmitter;

    /**
     * 向前端发送统一 AgentEvent。
     */
    protected void sendEvent(AgentEvent event) {
        SseEmitter emitter = this.currentEmitter;
        if (emitter == null) {
            return;
        }
        try {
            String json =
                    OBJECT_MAPPER
                            .writeValueAsString(event);
            emitter.send(
                    SseEmitter.event()
                            .name(
                                    event.getType()
                                            .toLowerCase()
                            )
                            .data(json)
            );
        } catch (Exception e) {
            log.error(
                    "Failed to send Agent event: {}",
                    event,
                    e
            );
        }
    }

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

        if (executionListener != null) {
            executionListener.onExecutionStart(name, userPrompt, maxSteps);
        }

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

                if (executionListener != null) {
                    executionListener.onStepComplete(name, currentStep, stepResult);
                }

                // Compact context if approaching token limit
                if (contextWindowManager.shouldCompact(messages)) {
                    messages = contextWindowManager.compact(messages, 3);
                    log.info("Agent {} compacted context at step {}", name, currentStep);
                }

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

            if (executionListener != null) {
                executionListener.onExecutionComplete(name, agentState, finalResult, getExecutionDurationMillis());
            }
            return finalResult;

        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Agent {} error at step {}", name, currentStep, e);

            if (executionListener != null) {
                executionListener.onExecutionComplete(name, agentState, "Error: " + e.getMessage(), getExecutionDurationMillis());
            }
            return "Error: " + e.getMessage();
        } finally {
            // 清理资源
            cleanup();
        }
    }

    /**
     * 流式执行 Agent
     *
     * 当前版本：
     *
     * 1. SSE 使用统一 AgentEvent
     * 2. 不再向前端发送 Step1/Step2 文本
     * 3. ToolCallAgent 可以通过 sendEvent() 主动发送工具事件
     *
     * @param userPrompt 用户输入
     * @return SseEmitter
     */
    public SseEmitter runStream(String userPrompt) {

        SseEmitter sseEmitter =
                new SseEmitter(180000L);

        /*
         * 保存当前 emitter，
         * 供子类 ToolCallAgent 发送事件。
         */
        this.currentEmitter =
                sseEmitter;


        /*
         * 超时
         */
        sseEmitter.onTimeout(() -> {

            log.warn(
                    "Agent {} stream timeout",
                    name
            );

            cancelled.set(true);

            agentState =
                    AgentState.ERROR;

            sendEvent(
                    AgentEvent.error(
                            name,
                            currentStep,
                            "Agent execution timeout"
                    )
            );

            cleanup();

            currentEmitter = null;
        });


        /*
         * 正常完成
         */
        sseEmitter.onCompletion(() -> {

            log.info(
                    "Agent {} stream completed",
                    name
            );

            agentState =
                    AgentState.IDLE;

            cleanup();

            currentEmitter = null;
        });


        CompletableFuture.runAsync(() -> {

            try {

                /*
                 * 状态检查
                 */
                if (agentState
                        != AgentState.IDLE) {

                    log.warn(
                            "Agent {} is not idle, current state: {}",
                            name,
                            agentState
                    );

                    sendEvent(
                            AgentEvent.error(
                                    name,
                                    currentStep,
                                    "Agent is not idle: "
                                            + agentState
                            )
                    );

                    sendEvent(
                            AgentEvent.done(
                                    name,
                                    currentStep
                            )
                    );

                    sseEmitter.complete();

                    return;
                }

                /*
                 * 参数检查
                 */
                if (StrUtil.isBlank(
                        userPrompt)) {

                    log.warn(
                            "Agent {} received empty user prompt",
                            name
                    );

                    sendEvent(
                            AgentEvent.error(
                                    name,
                                    currentStep,
                                    "User prompt cannot be empty"
                            )
                    );

                    sendEvent(
                            AgentEvent.done(
                                    name,
                                    currentStep
                            )
                    );

                    sseEmitter.complete();

                    return;
                }

                startTime =
                        LocalDateTime.now();

                cancelled.set(false);

                agentState =
                        AgentState.RUNNING;

                log.info(
                        "Starting {} stream execution - Prompt: {}",
                        name,
                        userPrompt.substring(
                                0,
                                Math.min(
                                        100,
                                        userPrompt.length()
                                )
                        )
                );

                /*
                 * 通知前端：
                 *
                 * Agent 开始处理。
                 */
                sendEvent(
                        AgentEvent.status(
                                name,
                                0,
                                "正在处理请求"
                        )
                );

                /*
                 * 添加用户输入
                 */
                messages.add(
                        new UserMessage(
                                userPrompt
                        )
                );

                try {

                    for (
                            int i = 0;
                            i < maxSteps
                                    && agentState
                                    != AgentState.FINISHED
                                    && !cancelled.get();
                            i++
                    ) {

                        if (cancelled.get()) {

                            log.info(
                                    "Agent {} stream execution cancelled at step {}",
                                    name,
                                    i + 1
                            );

                            sendEvent(
                                    AgentEvent.error(
                                            name,
                                            i + 1,
                                            "Execution cancelled"
                                    )
                            );

                            break;
                        }

                        currentStep =
                                i + 1;


                        log.debug(
                                "Agent {} executing stream step {}/{}",
                                name,
                                currentStep,
                                maxSteps
                        );

                        /*
                         * 只告诉前端：
                         *
                         * 当前 Agent 正在工作。
                         *
                         * 不发送内部完整推理内容。
                         */
                        sendEvent(
                                AgentEvent.status(
                                        name,
                                        currentStep,
                                        "正在处理"
                                )
                        );


                        /*
                         * 执行 ReAct step
                         */
                        String stepResult =
                                step();

                        log.info(
                                "Agent {} stream step {} completed",
                                name,
                                currentStep
                        );

                        /*
                         * 如果 Agent 已经结束，
                         * 当前 stepResult 可认为是最终答案。
                         *
                         * 这里依赖你现有 ReActAgent.step()
                         * 的行为：
                         *
                         * think() 不再请求工具时，
                         * step() 返回最终响应。
                         */
                        if (agentState
                                == AgentState.FINISHED) {

                            if (stepResult != null
                                    && !stepResult.isBlank()) {

                                sendEvent(
                                        AgentEvent.answer(
                                                name,
                                                currentStep,
                                                stepResult
                                        )
                                );
                            }

                            break;
                        }

                        /*
                         * 达到最大步骤
                         */
                        if (currentStep
                                >= maxSteps) {

                            agentState =
                                    AgentState.FINISHED;

                            log.info(
                                    "Agent {} stream finished - reached max steps",
                                    name
                            );
                            sendEvent(
                                    AgentEvent.error(
                                            name,
                                            currentStep,
                                            "Reached max steps ("
                                                    + maxSteps
                                                    + ")"
                                    )
                            );
                            break;
                        }
                    }

                    /*
                     * 正常完成事件
                     */
                    sendEvent(
                            AgentEvent.done(
                                    name,
                                    currentStep
                            )
                    );

                    /*
                     * 给客户端一点时间接收 DONE。
                     */
                    Thread.sleep(100);

                    sseEmitter.complete();

                    log.info(
                            "Agent {} stream execution completed - Duration: {}ms, Steps: {}",
                            name,
                            java.time.Duration
                                    .between(
                                            startTime,
                                            LocalDateTime.now()
                                    )
                                    .toMillis(),
                            currentStep
                    );

                } catch (Exception e) {
                    agentState =
                            AgentState.ERROR;
                    log.error(
                            "Agent {} stream error at step {}",
                            name,
                            currentStep,
                            e
                    );
                    sendEvent(
                            AgentEvent.error(
                                    name,
                                    currentStep,
                                    e.getMessage()
                            )
                    );
                    sendEvent(
                            AgentEvent.done(
                                    name,
                                    currentStep
                            )
                    );
                    sseEmitter.complete();
                } finally {
                    cleanup();
                }


            } catch (Exception e) {
                log.error(
                        "Agent {} stream initialization error",
                        name,
                        e
                );
                sendEvent(
                        AgentEvent.error(
                                name,
                                currentStep,
                                e.getMessage()
                        )
                );
                sseEmitter.completeWithError(
                        e
                );
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

    protected void resetExecutionState() {
        currentStep = 0;
        cancelled.set(false);
        agentState = AgentState.IDLE;
    }
}
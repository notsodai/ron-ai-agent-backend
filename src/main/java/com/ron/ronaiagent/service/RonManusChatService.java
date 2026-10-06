package com.ron.ronaiagent.service;

import com.ron.ronaiagent.agent.AgentExecutionListener;
import com.ron.ronaiagent.agent.AgentState;
import com.ron.ronaiagent.agent.RonManus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RonManus 聊天服务
 *
 * 第一阶段主要职责：
 *
 * 1. 创建 RonManus
 * 2. 根据 conversationId 管理不同会话
 * 3. 保存不同会话的上下文 messages
 * 4. 防止同一个会话同时执行多个 Agent 请求
 * 5. 管理 Agent 每轮执行状态
 *
 * 当前会话保存在 JVM 内存中。
 *
 * 后续阶段将逐步替换/扩展为：
 *
 * Redis
 * PostgreSQL
 * Spring AI ChatMemory
 *
 * @author admin
 */
@Slf4j
@Service
public class RonManusChatService {

    /**
     * 项目中已经注册好的全部 Tool
     */
    @Resource
    private ToolCallback[] availableTools;

    /**
     * DashScope / Qwen ChatModel
     */
    @Resource
    private ChatModel dashScopeChatModel;

    /**
     * Agent 执行监听器。
     *
     * 项目中如果存在则自动注入，
     * 不存在也不影响聊天。
     */
    @Autowired(required = false)
    private AgentExecutionListener agentExecutionListener;

    /**
     * 会话 Agent 缓存。
     *
     * key:
     * conversationId
     *
     * value:
     * 当前会话专属 RonManus
     *
     * 每个 conversationId 对应一个独立 RonManus，
     * 因此 RonManus 内部的 messages 可以被保留下来，
     * 从而实现多轮上下文。
     */
    private final Map<String, RonManus> conversations =
            new ConcurrentHashMap<>();

    /**
     * 当前正在执行的会话。
     *
     * 防止用户连续点击“发送”，
     * 导致同一个 RonManus 并发执行。
     */
    private final Set<String> activeConversations =
            ConcurrentHashMap.newKeySet();


    /**
     * 流式聊天
     *
     * @param conversationId 会话 ID
     * @param message        用户消息
     * @return SseEmitter
     */
    public SseEmitter chatStream(
            String conversationId,
            String message) {

        validateParameters(conversationId, message);

        /*
         * 对同一个 conversation 加锁。
         *
         * add()：
         *
         * 第一次添加 -> true
         * 已经存在   -> false
         */
        boolean acquired =
                activeConversations.add(conversationId);

        if (!acquired) {

            log.warn(
                    "Conversation is already running: {}",
                    conversationId
            );

            return createErrorEmitter(
                    "This conversation is currently generating a response. " +
                            "Please wait until the current response is finished."
            );
        }

        try {

            RonManus ronManus =
                    getOrCreateAgent(conversationId);

            /*
             * BaseAgent.runStream() 中 currentStep
             * 并不会在下一轮自动回到 0。
             *
             * 所以每轮正式执行前重置步骤数。
             *
             * 注意：
             *
             * 不允许清空 messages。
             *
             * messages 正是我们现在实现多轮上下文的核心。
             */
            ronManus.setCurrentStep(0);

            /*
             * 正常情况下上一轮完成后
             * runStream 的 onCompletion 会恢复 IDLE。
             *
             * 这里额外进行检查，
             * 防止异常状态下再次进入执行。
             */
            if (ronManus.getAgentState() != AgentState.IDLE) {

                log.warn(
                        "Agent is not idle. conversationId={}, state={}",
                        conversationId,
                        ronManus.getAgentState()
                );

                activeConversations.remove(conversationId);

                return createErrorEmitter(
                        "Agent is not ready. Current state: "
                                + ronManus.getAgentState()
                );
            }

            log.info(
                    "Starting RonManus chat. conversationId={}, message={}",
                    conversationId,
                    abbreviate(message, 100)
            );

            SseEmitter emitter =
                    ronManus.runStream(message);

            /*
             * Agent 正常结束
             */
            emitter.onCompletion(() -> {

                activeConversations.remove(conversationId);

                /*
                 * 这里只清 execution state，
                 * 绝对不要清空 messages。
                 */
                ronManus.setCurrentStep(0);

                log.info(
                        "RonManus conversation completed: {}",
                        conversationId
                );
            });

            /*
             * Agent 执行异常
             */
            emitter.onError(error -> {

                activeConversations.remove(conversationId);

                ronManus.setCurrentStep(0);

                log.error(
                        "RonManus conversation error: {}",
                        conversationId,
                        error
                );
            });

            /*
             * Agent 超时
             */
            emitter.onTimeout(() -> {

                activeConversations.remove(conversationId);

                ronManus.setCurrentStep(0);

                log.warn(
                        "RonManus conversation timeout: {}",
                        conversationId
                );
            });

            return emitter;

        } catch (Exception e) {

            activeConversations.remove(conversationId);

            log.error(
                    "Failed to execute RonManus chat. conversationId={}",
                    conversationId,
                    e
            );

            return createErrorEmitter(
                    "RonManus execution failed: "
                            + e.getMessage()
            );
        }
    }


    /**
     * 同步聊天
     *
     * 当前主要用于接口测试以及后续非流式场景。
     *
     * @param conversationId 会话 ID
     * @param message        用户消息
     * @return AI 完整执行结果
     */
    public String chatSync(
            String conversationId,
            String message) {

        validateParameters(conversationId, message);

        boolean acquired =
                activeConversations.add(conversationId);

        if (!acquired) {

            throw new IllegalStateException(
                    "This conversation is currently generating a response."
            );
        }

        RonManus ronManus =
                getOrCreateAgent(conversationId);

        try {

            if (ronManus.getAgentState()
                    != AgentState.IDLE) {

                throw new IllegalStateException(
                        "Agent is not idle. Current state: "
                                + ronManus.getAgentState()
                );
            }

            ronManus.setCurrentStep(0);

            log.info(
                    "Starting sync RonManus chat. conversationId={}",
                    conversationId
            );

            return ronManus.run(message);

        } finally {

            /*
             * BaseAgent.run() 当前不会像 runStream()
             * 那样通过 onCompletion 自动恢复 IDLE。
             *
             * 因此同步调用结束后这里统一恢复。
             */
            ronManus.setCurrentStep(0);
            ronManus.setAgentState(AgentState.IDLE);

            activeConversations.remove(conversationId);

            log.info(
                    "Sync RonManus chat finished. conversationId={}",
                    conversationId
            );
        }
    }


    /**
     * 根据 conversationId 获取 Agent。
     *
     * 不存在则创建。
     */
    private RonManus getOrCreateAgent(
            String conversationId) {

        return conversations.computeIfAbsent(
                conversationId,
                id -> {

                    log.info(
                            "Creating new RonManus conversation: {}",
                            id
                    );

                    return createAgent();
                }
        );
    }


    /**
     * 创建一个全新的 RonManus。
     */
    private RonManus createAgent() {

        RonManus ronManus =
                new RonManus(
                        availableTools,
                        dashScopeChatModel
                );

        if (agentExecutionListener != null) {

            ronManus.setExecutionListener(
                    agentExecutionListener
            );
        }

        return ronManus;
    }


    /**
     * 删除指定会话。
     *
     * 后续前端“删除会话”功能可以直接调用。
     */
    public boolean removeConversation(
            String conversationId) {

        if (conversationId == null
                || conversationId.isBlank()) {

            return false;
        }

        /*
         * 正在运行的 Agent 不允许删除。
         */
        if (activeConversations.contains(
                conversationId)) {

            log.warn(
                    "Cannot remove active conversation: {}",
                    conversationId
            );

            return false;
        }

        RonManus removed =
                conversations.remove(
                        conversationId
                );

        if (removed != null) {

            log.info(
                    "Conversation removed: {}",
                    conversationId
            );

            return true;
        }

        return false;
    }


    /**
     * 清空所有会话。
     *
     * 后续主要用于管理接口。
     */
    public void clearConversations() {

        conversations.entrySet()
                .removeIf(entry ->
                        !activeConversations.contains(
                                entry.getKey()
                        )
                );

        log.info(
                "Inactive RonManus conversations cleared"
        );
    }


    /**
     * 获取当前内存中的会话数量。
     */
    public int getConversationCount() {

        return conversations.size();
    }


    /**
     * 获取正在执行的会话数量。
     */
    public int getActiveConversationCount() {

        return activeConversations.size();
    }


    /**
     * 参数校验
     */
    private void validateParameters(
            String conversationId,
            String message) {

        if (conversationId == null
                || conversationId.isBlank()) {

            throw new IllegalArgumentException(
                    "conversationId cannot be empty"
            );
        }

        if (message == null
                || message.isBlank()) {

            throw new IllegalArgumentException(
                    "message cannot be empty"
            );
        }
    }


    /**
     * 创建错误 SSE。
     */
    private SseEmitter createErrorEmitter(
            String errorMessage) {

        SseEmitter emitter =
                new SseEmitter(30_000L);

        try {

            emitter.send(
                    SseEmitter.event()
                            .name("error")
                            .data(errorMessage)
            );

            emitter.send(
                    SseEmitter.event()
                            .name("done")
                            .data("[DONE]")
            );

            emitter.complete();

        } catch (Exception e) {

            emitter.completeWithError(e);
        }

        return emitter;
    }


    /**
     * 日志字符串缩略显示。
     */
    private String abbreviate(
            String value,
            int maxLength) {

        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }
}
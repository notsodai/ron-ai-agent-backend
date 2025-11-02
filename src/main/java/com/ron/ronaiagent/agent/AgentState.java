package com.ron.ronaiagent.agent;

/**
 * @author admin
 * @date 2025/10/12 下午2:46
 * @description: 智能助手状态
 */
public enum AgentState {
    /**
     * 空闲状态
     */
    IDLE,
    /**
     * 运行中
     */
    RUNNING,
    /**
     * 结束状态
     */
    FINISHED,
    /**
     * 错误状态
     */
    ERROR
}

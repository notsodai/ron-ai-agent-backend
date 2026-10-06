package com.ron.ronaiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent SSE 统一事件对象
 *
 * 前端以后不再解析：
 *
 * Step1:
 * Step2:
 * [DONE]
 *
 * 而是统一解析 JSON。
 *
 * @author admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    /**
     * 事件类型
     *
     * STATUS
     * TOOL_START
     * TOOL_RESULT
     * ANSWER
     * ERROR
     * DONE
     */
    private String type;

    /**
     * 当前执行步骤
     */
    private Integer step;

    /**
     * Agent 名称
     */
    private String agent;

    /**
     * 工具名称
     */
    private String tool;

    /**
     * 展示内容
     */
    private String content;

    /**
     * 执行状态
     *
     * RUNNING
     * SUCCESS
     * ERROR
     */
    private String status;


    public static AgentEvent status(
            String agent,
            Integer step,
            String content) {

        return AgentEvent.builder()
                .type("STATUS")
                .agent(agent)
                .step(step)
                .content(content)
                .status("RUNNING")
                .build();
    }


    public static AgentEvent toolStart(
            String agent,
            Integer step,
            String tool,
            String content) {

        return AgentEvent.builder()
                .type("TOOL_START")
                .agent(agent)
                .step(step)
                .tool(tool)
                .content(content)
                .status("RUNNING")
                .build();
    }


    public static AgentEvent toolResult(
            String agent,
            Integer step,
            String tool,
            String content) {

        return AgentEvent.builder()
                .type("TOOL_RESULT")
                .agent(agent)
                .step(step)
                .tool(tool)
                .content(content)
                .status("SUCCESS")
                .build();
    }


    public static AgentEvent answer(
            String agent,
            Integer step,
            String content) {

        return AgentEvent.builder()
                .type("ANSWER")
                .agent(agent)
                .step(step)
                .content(content)
                .status("SUCCESS")
                .build();
    }


    public static AgentEvent error(
            String agent,
            Integer step,
            String content) {

        return AgentEvent.builder()
                .type("ERROR")
                .agent(agent)
                .step(step)
                .content(content)
                .status("ERROR")
                .build();
    }


    public static AgentEvent done(
            String agent,
            Integer step) {

        return AgentEvent.builder()
                .type("DONE")
                .agent(agent)
                .step(step)
                .content("")
                .status("SUCCESS")
                .build();
    }
}
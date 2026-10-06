package com.ron.ronaiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 当前会话 ID
     */
    private String conversationId;

    /**
     * AI 返回内容
     */
    private String content;

    /**
     * 执行状态
     * SUCCESS
     * ERROR
     * BUSY
     */
    private String status;
}
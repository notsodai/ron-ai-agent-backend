package com.ron.ronaiagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 用户消息
     */
    @NotBlank(message = "message cannot be empty")
    private String message;
}
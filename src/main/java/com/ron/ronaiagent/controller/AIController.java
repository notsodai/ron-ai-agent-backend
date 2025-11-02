package com.ron.ronaiagent.controller;

import com.ron.ronaiagent.agent.RonManus;
import com.ron.ronaiagent.app.BookApp;
import io.reactivex.Emitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * AI Assistant Controller - 智能助手控制器
 *
 * @author admin
 * @date 2025/11/2 下午10:07
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI智能助手", description = "AI聊天和智能助手相关接口")
public class AIController {
    @Resource
    private BookApp bookApp;
    @Resource
    private ToolCallback[] availableTools;
    @Resource
    private ChatModel dashScopeChatModel;

    @GetMapping("/book/chat/sync")
    @Operation(
            summary = "同步聊天",
            description = "与AI助手进行同步聊天，返回完整回复"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "聊天回复成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(description = "AI助手的回复内容")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数错误",
                    content = @Content
            )
    })
    public String doChatWithBookAppSync(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam String conversationId) {
        return bookApp.doChatWithBookList(message, conversationId);
    }

    @GetMapping("/book/chat/stream")
    @Operation(
            summary = "流式聊天",
            description = "与AI助手进行流式聊天，通过Server-Sent Events返回实时回复"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "流式聊天连接建立成功",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(description = "Server-Sent Events格式的流式数据")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数错误",
                    content = @Content
            )
    })
    public Flux<ServerSentEvent<String>> doChatWithBookAppStream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam String conversationId) {
        return bookApp.doChatByStream(message, conversationId)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    @GetMapping("/book/chat/stream/emitter")
    @Operation(
            summary = "SSE流式聊天",
            description = "使用SseEmitter进行流式聊天，提供更灵活的流式响应控制"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SSE流式连接建立成功",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(description = "SseEmitter格式的流式数据")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数错误",
                    content = @Content
            )
    })
    public SseEmitter doChatWithBookAppStreamEmitter(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam String conversationId) {
        // 创建一个 SseEmitter 对象，设置3分钟超时
        SseEmitter sseEmitter = new SseEmitter(180000L);
        bookApp.doChatByStream(message, conversationId)
                // 获取 Flux 数据流并直接订阅
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }
}

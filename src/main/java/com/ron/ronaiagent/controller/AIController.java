package com.ron.ronaiagent.controller;

import com.ron.ronaiagent.agent.RonManus;
import com.ron.ronaiagent.app.BookApp;
import com.ron.ronaiagent.core.CacheManager;
import com.ron.ronaiagent.core.RequestDeduplicationManager;
import com.ron.ronaiagent.core.RequestRateLimitManager;
import com.ron.ronaiagent.core.UnifiedRequestProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Assistant Controller - 智能助手控制器
 *
 * @author admin
 * @date 2025/11/2 下午10:07
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Validated
@Tag(name = "AI智能助手", description = "AI聊天和智能助手相关接口")
public class AIController {
    @Resource
    private BookApp bookApp;
    @Resource
    private ToolCallback[] availableTools;
    @Resource
    private ChatModel dashScopeChatModel;
    @Resource
    private RequestDeduplicationManager deduplicationManager;
    @Resource
    private RequestRateLimitManager rateLimitManager;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private UnifiedRequestProcessor unifiedRequestProcessor;

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
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "请求频率限制",
                    content = @Content
            )
    })
    public ResponseEntity<String> doChatWithBookAppSync(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam @NotBlank String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam @NotBlank String conversationId,
            @RequestHeader(value = "X-Client-ID", required = false) String clientId) {

        log.info("Received sync chat request - Conversation: {}, Message: {}", conversationId, message.substring(0, Math.min(100, message.length())));

        // 生成客户端ID
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = generateClientId(conversationId);
        }

        // 生成请求键用于去重
        String normalizedMessage = message.trim().toLowerCase();
        String requestKey = deduplicationManager.generateRequestKey(
                "/book/chat/sync", "GET", "conversationId=" + conversationId, "message=" + normalizedMessage);

        // 创建请求上下文
        UnifiedRequestProcessor.RequestContext context = UnifiedRequestProcessor.RequestContext.builder()
                .clientId(clientId)
                .requestKey(requestKey)
                .endpoint("/book/chat/sync")
                .message(message)
                .rateLimitConfig(RequestRateLimitManager.RateLimitConfig.defaultConfig())
                .ttl(5) // 5分钟去重窗口
                .enableCache(true)
                .cacheTtl(10) // 10分钟缓存
                .moveToCache(true)
                .build();

        // 使用统一请求处理器处理请求
        UnifiedRequestProcessor.RequestProcessorResult<String> result = unifiedRequestProcessor.processRequest(
                context,
                (ctx) -> {
                    // 执行实际的聊天请求
                    String response = bookApp.doChatWithBookList(message, conversationId);
                    log.info("Sync chat completed for conversation: {}", conversationId);
                    return response;
                }
        );

        // 根据处理结果返回响应
        switch (result.getType()) {
            case SUCCESS:
                return ResponseEntity.ok(result.getData());
            case CACHE_HIT:
                log.info("Returning cached result for request key: {}", requestKey);
                return ResponseEntity.ok(result.getData());
            case DUPLICATE:
                log.info("Duplicate request detected for key: {}", requestKey);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Duplicate request detected. Original request is processing.");
            case RATE_LIMIT_EXCEEDED:
                log.warn("Rate limit exceeded for client: {} - {}", clientId, result.getMessage());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", result.getRetryAfter().getSeconds() + "")
                        .body("Rate limit exceeded: " + result.getMessage());
            case ERROR:
            default:
                log.error("Error processing sync chat request: {}", result.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Internal server error: " + result.getMessage());
        }
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
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "请求频率限制",
                    content = @Content
            )
    })
    public Flux<ServerSentEvent<String>> doChatWithBookAppStream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam @NotBlank String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam @NotBlank String conversationId,
            @RequestHeader(value = "X-Client-ID", required = false) String clientId) {

        log.info("Received stream chat request - Conversation: {}, Message: {}", conversationId, message.substring(0, Math.min(100, message.length())));

        // 生成客户端ID
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = generateClientId(conversationId);
        }

        // 限流检查（流式请求使用更宽松的限制）
        RequestRateLimitManager.RateLimitConfig streamConfig = RequestRateLimitManager.RateLimitConfig.lenientConfig();
        RequestRateLimitManager.RateLimitResult rateLimitResult = rateLimitManager.checkRequest(clientId, streamConfig);
        if (!rateLimitResult.isAllowed()) {
            log.warn("Rate limit exceeded for client: {} - {}", clientId, rateLimitResult.getReason());
            return Flux.error(new RuntimeException("Rate limit exceeded: " + rateLimitResult.getReason()));
        }

        // 生成请求键用于去重 - 基于会话ID和时间窗口进行去重
        String normalizedMessage = message.trim().toLowerCase();
        String requestKey = deduplicationManager.generateRequestKey(
                "/book/chat/stream", "GET", "conversationId=" + conversationId, "message=" + normalizedMessage);

        // 检查是否为重复的流式请求
        RequestDeduplicationManager.DeduplicationResult deduplicationResult = deduplicationManager.checkAndRecordRequest(requestKey, 2);

        if (deduplicationResult.isDuplicate()) {
            log.warn("Duplicate stream request detected for key: {}", requestKey);
            return Flux.error(new RuntimeException("Duplicate stream request detected"));
        }

        return bookApp.doChatByStream(message, conversationId)
                .doOnNext(chunk -> log.debug("Stream chunk for conversation {}: {}", conversationId, chunk.substring(0, Math.min(50, chunk.length()))))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("message")
                        .build())
                .doOnComplete(() -> {
                    log.info("Stream chat completed for conversation: {}", conversationId);
                    deduplicationManager.markRequestCompleted(requestKey, false); // 流式请求完成后不移动到缓存
                })
                .doOnError(error -> {
                    log.error("Stream chat error for conversation: {}", conversationId, error);
                    deduplicationManager.cancelRequest(requestKey);
                })
                .doOnCancel(() -> {
                    log.info("Stream chat cancelled for conversation: {}", conversationId);
                    deduplicationManager.cancelRequest(requestKey);
                });
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
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "请求频率限制",
                    content = @Content
            )
    })
    public SseEmitter doChatWithBookAppStreamEmitter(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam @NotBlank String message,
            @Parameter(description = "会话ID，用于上下文管理", required = true)
            @RequestParam @NotBlank String conversationId,
            @RequestHeader(value = "X-Client-ID", required = false) String clientId) {

        log.info("Received SSE stream chat request - Conversation: {}, Message: {}", conversationId, message.substring(0, Math.min(100, message.length())));

        // 生成客户端ID
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = generateClientId(conversationId);
        }

        // 限流检查
        RequestRateLimitManager.RateLimitResult rateLimitResult = rateLimitManager.checkRequest(clientId);
        if (!rateLimitResult.isAllowed()) {
            log.warn("Rate limit exceeded for client: {} - {}", clientId, rateLimitResult.getReason());
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send("Rate limit exceeded: " + rateLimitResult.getReason());
                    errorEmitter.complete();
                } catch (Exception e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        // 生成请求键用于去重 - 基于会话ID和时间窗口进行去重
        String normalizedMessage = message.trim().toLowerCase();
        String requestKey = deduplicationManager.generateRequestKey(
                "/book/chat/stream/emitter", "GET", "conversationId=" + conversationId, "message=" + normalizedMessage);

        // 检查重复请求
        RequestDeduplicationManager.DeduplicationResult deduplicationResult = deduplicationManager.checkAndRecordRequest(requestKey, 3);

        if (deduplicationResult.isDuplicate()) {
            log.warn("Duplicate SSE stream request detected for key: {}", requestKey);
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send("Duplicate request detected");
                    errorEmitter.complete();
                } catch (Exception e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        // 创建一个 SseEmitter 对象，设置3分钟超时
        SseEmitter sseEmitter = new SseEmitter(180000L);

        bookApp.doChatByStream(message, conversationId)
                // 获取 Flux 数据流并直接订阅
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        log.error("Error sending SSE chunk", e);
                        sseEmitter.completeWithError(e);
                        deduplicationManager.cancelRequest(requestKey);
                    }
                }, error -> {
                    log.error("SSE stream error for conversation: {}", conversationId, error);
                    sseEmitter.completeWithError(error);
                    deduplicationManager.cancelRequest(requestKey);
                }, () -> {
                    log.info("SSE stream completed for conversation: {}", conversationId);
                    try {
                        sseEmitter.send("[DONE]");
                    } catch (Exception e) {
                        log.debug("Error sending completion marker", e);
                    }
                    sseEmitter.complete();
                    deduplicationManager.markRequestCompleted(requestKey, false);
                });

        return sseEmitter;
    }

    @GetMapping("RonManus/chat/")
    @Operation(
            summary = "RonManus智能助手",
            description = "与RonManus智能助手进行对话，返回完整回复"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "聊天回复成功",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(description = "RonManus的流式回复内容")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数错误",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "请求频率限制",
                    content = @Content
            )
    })
    public SseEmitter doChatWithRonManus(
        @Parameter(description = "用户消息内容", required = true)
        @RequestParam @NotBlank String message,
        @RequestHeader(value = "X-Client-ID", required = false) String clientId) {

        log.info("Received RonManus chat request - Message: {}", message.substring(0, Math.min(100, message.length())));

        // 生成客户端ID
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = generateClientId("ronmanus");
        }

        // 限流检查（RonManus使用较严格的限制，因为它可能调用外部工具）
        RequestRateLimitManager.RateLimitConfig ronManusConfig = RequestRateLimitManager.RateLimitConfig.strictConfig();
        RequestRateLimitManager.RateLimitResult rateLimitResult = rateLimitManager.checkRequest(clientId, ronManusConfig);
        if (!rateLimitResult.isAllowed()) {
            log.warn("Rate limit exceeded for RonManus client: {} - {}", clientId, rateLimitResult.getReason());
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send("Rate limit exceeded: " + rateLimitResult.getReason());
                    errorEmitter.complete();
                } catch (Exception e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        // 生成请求键用于去重 - 基于消息内容进行去重
        String normalizedMessage = message.trim().toLowerCase();
        String requestKey = deduplicationManager.generateRequestKey(
                "/RonManus/chat/", "GET", "", "message=" + normalizedMessage);

        // 检查重复请求
        RequestDeduplicationManager.DeduplicationResult deduplicationResult = deduplicationManager.checkAndRecordRequest(requestKey, 5);

        if (deduplicationResult.isDuplicate()) {
            log.warn("Duplicate RonManus request detected for key: {}", requestKey);
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send("Duplicate request detected");
                    errorEmitter.complete();
                } catch (Exception e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        try {
            RonManus ronManus = new RonManus(availableTools, dashScopeChatModel);
            SseEmitter emitter = ronManus.runStream(message);

            // 添加完成和错误处理
            emitter.onCompletion(() -> {
                log.info("RonManus chat completed for request key: {}", requestKey);
                deduplicationManager.markRequestCompleted(requestKey, false);
            });

            emitter.onError(error -> {
                log.error("RonManus chat error for request key: {}", requestKey, error);
                deduplicationManager.cancelRequest(requestKey);
            });

            emitter.onTimeout(() -> {
                log.warn("RonManus chat timeout for request key: {}", requestKey);
                deduplicationManager.cancelRequest(requestKey);
            });

            return emitter;

        } catch (Exception e) {
            log.error("Error creating RonManus chat", e);
            deduplicationManager.cancelRequest(requestKey);
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send("Internal server error: " + e.getMessage());
                    errorEmitter.completeWithError(e);
                } catch (Exception ex) {
                    errorEmitter.completeWithError(ex);
                }
            });
            return errorEmitter;
        }
    }

    /**
     * 获取系统统计信息
     */
    @GetMapping("/stats")
    @Operation(
            summary = "获取系统统计信息",
            description = "获取请求去重、限流和缓存的统计信息"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "统计信息获取成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(description = "系统统计信息")
                    )
            )
    })
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = Map.of(
                    "deduplication", deduplicationManager.getStats(),
                    "rateLimit", rateLimitManager.getGlobalStats(),
                    "cache", cacheManager.getStats(),
                    "timestamp", LocalDateTime.now()
            );

            log.info("Stats requested: {}", stats);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error retrieving stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve stats: " + e.getMessage()));
        }
    }

    /**
     * 清理缓存
     */
    @PostMapping("/cache/clear")
    @Operation(
            summary = "清理缓存",
            description = "清理系统中的所有缓存"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "缓存清理成功"
            )
    })
    public ResponseEntity<String> clearCache() {
        try {
            cacheManager.clear();
            log.info("Cache cleared via API request");
            return ResponseEntity.ok("Cache cleared successfully");

        } catch (Exception e) {
            log.error("Error clearing cache", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to clear cache: " + e.getMessage());
        }
    }

    /**
     * 重置客户端限流计数
     */
    @PostMapping("/rate-limit/reset/{clientId}")
    @Operation(
            summary = "重置客户端限流计数",
            description = "重置指定客户端的限流计数器"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "重置成功"
            )
    })
    public ResponseEntity<String> resetClientRateLimit(
            @Parameter(description = "客户端ID", required = true)
            @PathVariable String clientId) {

        try {
            rateLimitManager.resetClientCounters(clientId);
            log.info("Rate limit counters reset for client: {}", clientId);
            return ResponseEntity.ok("Rate limit counters reset for client: " + clientId);

        } catch (Exception e) {
            log.error("Error resetting rate limit for client: {}", clientId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to reset rate limit: " + e.getMessage());
        }
    }

    /**
     * 生成客户端ID
     */
    private String generateClientId(String conversationId) {
        return "client_" + conversationId;
    }

    /**
     * 等待原始请求完成
     */
    private ResponseEntity<String> waitForOriginalRequest(String requestKey, String clientId) {
        // 简单实现：返回缓存结果或等待提示
        String cachedResponse = cacheManager.get(requestKey, String.class);
        if (cachedResponse != null) {
            return ResponseEntity.ok(cachedResponse);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Duplicate request detected. Original request is processing. Please wait.");
    }
}

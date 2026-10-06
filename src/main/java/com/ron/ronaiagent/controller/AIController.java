package com.ron.ronaiagent.controller;

import com.ron.ronaiagent.app.BookApp;
import com.ron.ronaiagent.core.CacheManager;
import com.ron.ronaiagent.core.RequestDeduplicationManager;
import com.ron.ronaiagent.core.RequestRateLimitManager;
import com.ron.ronaiagent.core.UnifiedRequestProcessor;
import com.ron.ronaiagent.dto.ChatRequest;
import com.ron.ronaiagent.dto.ChatResponse;
import com.ron.ronaiagent.service.RonManusChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI Assistant Controller
 *
 * 智能助手统一 Controller。
 *
 * 第一阶段二次开发：
 *
 * 1. 保留原有 BookApp 接口
 * 2. 保留限流、去重、缓存
 * 3. RonManus 从 Controller 中解耦
 * 4. 引入 RonManusChatService
 * 5. RonManus 支持 conversationId
 * 6. 支持真正的多轮 Agent 会话
 *
 * @author admin
 * @date 2025/11/2 下午10:07
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Validated
@Tag(
        name = "AI智能助手",
        description = "AI聊天和智能助手相关接口"
)
public class AIController {

    @Resource
    private BookApp bookApp;

    @Resource
    private RequestDeduplicationManager
            deduplicationManager;

    @Resource
    private RequestRateLimitManager
            rateLimitManager;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private UnifiedRequestProcessor
            unifiedRequestProcessor;

    /**
     * RonManus 服务。
     *
     * Controller 不再负责：
     *
     * new RonManus()
     * Tool 注入
     * ChatModel 注入
     * Agent 会话维护
     *
     * 这些逻辑全部交由 Service。
     */
    @Resource
    private RonManusChatService
            ronManusChatService;


    // =========================================================
    // BookApp - Sync
    // =========================================================

    /**
     * BookApp 同步聊天
     */
    @GetMapping("/book/chat/sync")
    @Operation(
            summary = "同步聊天",
            description =
                    "与AI助手进行同步聊天，返回完整回复"
    )
    @ApiResponses(value = {

            @ApiResponse(
                    responseCode = "200",
                    description = "聊天回复成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(
                                    description =
                                            "AI助手的回复内容"
                            )
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
    public ResponseEntity<String>
    doChatWithBookAppSync(

            @Parameter(
                    description = "用户消息内容",
                    required = true
            )
            @RequestParam
            @NotBlank
            String message,

            @Parameter(
                    description =
                            "会话ID，用于上下文管理",
                    required = true
            )
            @RequestParam
            @NotBlank
            String conversationId,

            @RequestHeader(
                    value = "X-Client-ID",
                    required = false
            )
            String clientId) {

        log.info(
                "Received sync chat request - " +
                        "Conversation: {}, Message: {}",
                conversationId,
                abbreviate(message, 100)
        );

        /*
         * 自动生成客户端 ID
         */
        if (clientId == null
                || clientId.trim().isEmpty()) {

            clientId =
                    generateClientId(
                            conversationId
                    );
        }

        /*
         * 请求去重 key
         */
        String normalizedMessage =
                message
                        .trim()
                        .toLowerCase();

        String requestKey =
                deduplicationManager
                        .generateRequestKey(

                                "/book/chat/sync",

                                "GET",

                                "conversationId="
                                        + conversationId,

                                "message="
                                        + normalizedMessage
                        );

        /*
         * 创建统一请求上下文
         */
        UnifiedRequestProcessor.RequestContext
                context =

                UnifiedRequestProcessor
                        .RequestContext
                        .builder()

                        .clientId(clientId)

                        .requestKey(requestKey)

                        .endpoint(
                                "/book/chat/sync"
                        )

                        .message(message)

                        .rateLimitConfig(
                                RequestRateLimitManager
                                        .RateLimitConfig
                                        .defaultConfig()
                        )

                        .ttl(5)

                        .enableCache(true)

                        .cacheTtl(10)

                        .moveToCache(true)

                        .build();

        /*
         * 执行请求
         */
        UnifiedRequestProcessor
                .RequestProcessorResult<String>
                result =

                unifiedRequestProcessor
                        .processRequest(

                                context,

                                ctx -> {

                                    String response =
                                            bookApp
                                                    .doChatWithBookList(
                                                            message,
                                                            conversationId
                                                    );

                                    log.info(
                                            "Sync chat completed " +
                                                    "for conversation: {}",
                                            conversationId
                                    );

                                    return response;
                                }
                        );

        /*
         * 根据请求结果返回
         */
        switch (result.getType()) {

            case SUCCESS:

                return ResponseEntity.ok(
                        result.getData()
                );

            case CACHE_HIT:

                log.info(
                        "Returning cached result " +
                                "for request key: {}",
                        requestKey
                );

                return ResponseEntity.ok(
                        result.getData()
                );

            case DUPLICATE:

                log.info(
                        "Duplicate request detected " +
                                "for key: {}",
                        requestKey
                );

                return ResponseEntity
                        .status(
                                HttpStatus.CONFLICT
                        )
                        .body(
                                "Duplicate request detected. " +
                                        "Original request is processing."
                        );

            case RATE_LIMIT_EXCEEDED:

                log.warn(
                        "Rate limit exceeded " +
                                "for client: {} - {}",
                        clientId,
                        result.getMessage()
                );

                return ResponseEntity
                        .status(
                                HttpStatus
                                        .TOO_MANY_REQUESTS
                        )
                        .header(
                                "Retry-After",
                                result
                                        .getRetryAfter()
                                        .getSeconds()
                                        + ""
                        )
                        .body(
                                "Rate limit exceeded: "
                                        + result.getMessage()
                        );

            case ERROR:

            default:

                log.error(
                        "Error processing " +
                                "sync chat request: {}",
                        result.getMessage()
                );

                return ResponseEntity
                        .status(
                                HttpStatus
                                        .INTERNAL_SERVER_ERROR
                        )
                        .body(
                                "Internal server error: "
                                        + result.getMessage()
                        );
        }
    }


    // =========================================================
    // BookApp - Flux Stream
    // =========================================================

    /**
     * BookApp Flux 流式聊天
     */
    @GetMapping("/book/chat/stream")
    @Operation(
            summary = "流式聊天",
            description =
                    "与AI助手进行流式聊天，" +
                            "通过Server-Sent Events返回实时回复"
    )
    @ApiResponses(value = {

            @ApiResponse(
                    responseCode = "200",
                    description =
                            "流式聊天连接建立成功",
                    content = @Content(
                            mediaType =
                                    "text/event-stream",
                            schema = @Schema(
                                    description =
                                            "Server-Sent Events格式的流式数据"
                            )
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
    public Flux<ServerSentEvent<String>>
    doChatWithBookAppStream(

            @Parameter(
                    description = "用户消息内容",
                    required = true
            )
            @RequestParam
            @NotBlank
            String message,

            @Parameter(
                    description =
                            "会话ID，用于上下文管理",
                    required = true
            )
            @RequestParam
            @NotBlank
            String conversationId,

            @RequestHeader(
                    value = "X-Client-ID",
                    required = false
            )
            String clientId) {

        log.info(
                "Received stream chat request - " +
                        "Conversation: {}, Message: {}",
                conversationId,
                abbreviate(message, 100)
        );

        if (clientId == null
                || clientId.trim().isEmpty()) {

            clientId =
                    generateClientId(
                            conversationId
                    );
        }

        /*
         * 流式请求使用较宽松限流
         */
        RequestRateLimitManager
                .RateLimitConfig
                streamConfig =

                RequestRateLimitManager
                        .RateLimitConfig
                        .lenientConfig();

        RequestRateLimitManager
                .RateLimitResult
                rateLimitResult =

                rateLimitManager
                        .checkRequest(
                                clientId,
                                streamConfig
                        );

        if (!rateLimitResult
                .isAllowed()) {

            log.warn(
                    "Rate limit exceeded " +
                            "for client: {} - {}",
                    clientId,
                    rateLimitResult
                            .getReason()
            );

            return Flux.error(
                    new RuntimeException(
                            "Rate limit exceeded: "
                                    + rateLimitResult
                                    .getReason()
                    )
            );
        }

        String normalizedMessage =
                message
                        .trim()
                        .toLowerCase();

        String requestKey =
                deduplicationManager
                        .generateRequestKey(

                                "/book/chat/stream",

                                "GET",

                                "conversationId="
                                        + conversationId,

                                "message="
                                        + normalizedMessage
                        );

        RequestDeduplicationManager
                .DeduplicationResult
                deduplicationResult =

                deduplicationManager
                        .checkAndRecordRequest(
                                requestKey,
                                2
                        );

        if (deduplicationResult
                .isDuplicate()) {

            log.warn(
                    "Duplicate stream request detected " +
                            "for key: {}",
                    requestKey
            );

            return Flux.error(
                    new RuntimeException(
                            "Duplicate stream request detected"
                    )
            );
        }

        return bookApp
                .doChatByStream(
                        message,
                        conversationId
                )

                .doOnNext(chunk ->
                        log.debug(
                                "Stream chunk " +
                                        "for conversation {}: {}",
                                conversationId,
                                abbreviate(
                                        chunk,
                                        50
                                )
                        )
                )

                .map(chunk ->

                        ServerSentEvent
                                .<String>builder()

                                .data(chunk)

                                .id(
                                        String.valueOf(
                                                System
                                                        .currentTimeMillis()
                                        )
                                )

                                .event(
                                        "message"
                                )

                                .build()
                )

                .doOnComplete(() -> {

                    log.info(
                            "Stream chat completed " +
                                    "for conversation: {}",
                            conversationId
                    );

                    deduplicationManager
                            .markRequestCompleted(
                                    requestKey,
                                    false
                            );
                })

                .doOnError(error -> {

                    log.error(
                            "Stream chat error " +
                                    "for conversation: {}",
                            conversationId,
                            error
                    );

                    deduplicationManager
                            .cancelRequest(
                                    requestKey
                            );
                })

                .doOnCancel(() -> {

                    log.info(
                            "Stream chat cancelled " +
                                    "for conversation: {}",
                            conversationId
                    );

                    deduplicationManager
                            .cancelRequest(
                                    requestKey
                            );
                });
    }


    // =========================================================
    // BookApp - SseEmitter
    // =========================================================

    /**
     * BookApp SseEmitter 流式聊天
     */
    @GetMapping(
            "/book/chat/stream/emitter"
    )
    @Operation(
            summary = "SSE流式聊天",
            description =
                    "使用SseEmitter进行流式聊天，" +
                            "提供更灵活的流式响应控制"
    )
    public SseEmitter
    doChatWithBookAppStreamEmitter(

            @Parameter(
                    description = "用户消息内容",
                    required = true
            )
            @RequestParam
            @NotBlank
            String message,

            @Parameter(
                    description =
                            "会话ID，用于上下文管理",
                    required = true
            )
            @RequestParam
            @NotBlank
            String conversationId,

            @RequestHeader(
                    value = "X-Client-ID",
                    required = false
            )
            String clientId) {

        log.info(
                "Received SSE stream chat request - " +
                        "Conversation: {}, Message: {}",
                conversationId,
                abbreviate(message, 100)
        );

        if (clientId == null
                || clientId.trim().isEmpty()) {

            clientId =
                    generateClientId(
                            conversationId
                    );
        }

        RequestRateLimitManager
                .RateLimitResult
                rateLimitResult =

                rateLimitManager
                        .checkRequest(
                                clientId
                        );

        if (!rateLimitResult
                .isAllowed()) {

            log.warn(
                    "Rate limit exceeded " +
                            "for client: {} - {}",
                    clientId,
                    rateLimitResult
                            .getReason()
            );

            SseEmitter errorEmitter =
                    new SseEmitter();

            CompletableFuture
                    .runAsync(() -> {

                        try {

                            errorEmitter.send(
                                    "Rate limit exceeded: "
                                            + rateLimitResult
                                            .getReason()
                            );

                            errorEmitter.complete();

                        } catch (Exception e) {

                            errorEmitter
                                    .completeWithError(e);
                        }
                    });

            return errorEmitter;
        }

        String normalizedMessage =
                message
                        .trim()
                        .toLowerCase();

        String requestKey =
                deduplicationManager
                        .generateRequestKey(

                                "/book/chat/stream/emitter",

                                "GET",

                                "conversationId="
                                        + conversationId,

                                "message="
                                        + normalizedMessage
                        );

        RequestDeduplicationManager
                .DeduplicationResult
                deduplicationResult =

                deduplicationManager
                        .checkAndRecordRequest(
                                requestKey,
                                3
                        );

        if (deduplicationResult
                .isDuplicate()) {

            log.warn(
                    "Duplicate SSE stream request " +
                            "detected for key: {}",
                    requestKey
            );

            SseEmitter errorEmitter =
                    new SseEmitter();

            CompletableFuture
                    .runAsync(() -> {

                        try {

                            errorEmitter.send(
                                    "Duplicate request detected"
                            );

                            errorEmitter.complete();

                        } catch (Exception e) {

                            errorEmitter
                                    .completeWithError(e);
                        }
                    });

            return errorEmitter;
        }

        SseEmitter sseEmitter =
                new SseEmitter(
                        180000L
                );

        bookApp
                .doChatByStream(
                        message,
                        conversationId
                )

                .subscribe(

                        chunk -> {

                            try {

                                sseEmitter
                                        .send(chunk);

                            } catch (IOException e) {

                                log.error(
                                        "Error sending SSE chunk",
                                        e
                                );

                                sseEmitter
                                        .completeWithError(e);

                                deduplicationManager
                                        .cancelRequest(
                                                requestKey
                                        );
                            }
                        },

                        error -> {

                            log.error(
                                    "SSE stream error " +
                                            "for conversation: {}",
                                    conversationId,
                                    error
                            );

                            sseEmitter
                                    .completeWithError(
                                            error
                                    );

                            deduplicationManager
                                    .cancelRequest(
                                            requestKey
                                    );
                        },

                        () -> {

                            log.info(
                                    "SSE stream completed " +
                                            "for conversation: {}",
                                    conversationId
                            );

                            try {

                                sseEmitter
                                        .send(
                                                "[DONE]"
                                        );

                            } catch (Exception e) {

                                log.debug(
                                        "Error sending " +
                                                "completion marker",
                                        e
                                );
                            }

                            sseEmitter
                                    .complete();

                            deduplicationManager
                                    .markRequestCompleted(
                                            requestKey,
                                            false
                                    );
                        }
                );

        return sseEmitter;
    }


    // =========================================================
    // RonManus - 新版正式 POST 流式接口
    // =========================================================

    /**
     * RonManus 正式流式聊天接口。
     *
     * 推荐前端以后统一使用此接口。
     *
     * 请求示例：
     *
     * {
     *   "conversationId": "abc123",
     *   "message": "你好"
     * }
     *
     * conversationId 第一次允许为空，
     * 后端会自动生成。
     *
     * 返回的 conversationId 会放在：
     *
     * Response Header:
     *
     * X-Conversation-ID
     */
    @PostMapping({
            "/RonManus/chat/",
            "/ronmanus/chat"
    })
    @Operation(
            summary =
                    "RonManus智能助手 - 正式流式接口",
            description =
                    "支持 conversationId 的 RonManus " +
                            "多轮 Agent 对话"
    )
    public SseEmitter
    doChatWithRonManus(

            @Valid
            @RequestBody
            ChatRequest request,

            @RequestHeader(
                    value = "X-Client-ID",
                    required = false
            )
            String clientId,

            HttpServletResponse response) {

        /*
         * conversationId 为空时，
         * 创建一个新的会话。
         */
        String conversationId =
                request
                        .getConversationId();

        if (conversationId == null
                || conversationId.isBlank()) {

            conversationId =
                    UUID
                            .randomUUID()
                            .toString();
        }

        String message =
                request.getMessage();

        /*
         * 将 conversationId 返回给前端。
         *
         * 前端必须保存这个值，
         * 下一轮继续带回来。
         */
        response.setHeader(
                "X-Conversation-ID",
                conversationId
        );

        log.info(
                "Received RonManus POST request - " +
                        "Conversation: {}, Message: {}",
                conversationId,
                abbreviate(message, 100)
        );

        /*
         * 客户端 ID
         */
        if (clientId == null
                || clientId.trim().isEmpty()) {

            clientId =
                    generateClientId(
                            conversationId
                    );
        }

        /*
         * RonManus 可能执行 Web Search、
         * 文件操作等外部 Tool，
         * 所以继续使用 strict 限流。
         */
        RequestRateLimitManager
                .RateLimitConfig
                ronManusConfig =

                RequestRateLimitManager
                        .RateLimitConfig
                        .strictConfig();

        RequestRateLimitManager
                .RateLimitResult
                rateLimitResult =

                rateLimitManager
                        .checkRequest(
                                clientId,
                                ronManusConfig
                        );

        if (!rateLimitResult
                .isAllowed()) {

            log.warn(
                    "Rate limit exceeded " +
                            "for RonManus client: {} - {}",
                    clientId,
                    rateLimitResult
                            .getReason()
            );

            return createErrorEmitter(
                    "Rate limit exceeded: "
                            + rateLimitResult
                            .getReason()
            );
        }

        /*
         * 生成去重 Key。
         *
         * 与旧接口相比，
         * 这里把 conversationId 加入 key。
         *
         * 不同会话发送同一句话
         * 不应该互相认为是重复请求。
         */
        String normalizedMessage =
                message
                        .trim()
                        .toLowerCase();

        String requestKey =
                deduplicationManager
                        .generateRequestKey(

                                "/RonManus/chat/",

                                "POST",

                                "conversationId="
                                        + conversationId,

                                "message="
                                        + normalizedMessage
                        );

        RequestDeduplicationManager
                .DeduplicationResult
                deduplicationResult =

                deduplicationManager
                        .checkAndRecordRequest(
                                requestKey,
                                5
                        );

        if (deduplicationResult
                .isDuplicate()) {

            log.warn(
                    "Duplicate RonManus request " +
                            "detected for key: {}",
                    requestKey
            );

            return createErrorEmitter(
                    "Duplicate request detected"
            );
        }

        try {

            /*
             * Controller 不再创建 RonManus。
             *
             * Service 根据 conversationId
             * 自动找到对应 Agent。
             */
            SseEmitter emitter =
                    ronManusChatService
                            .chatStream(
                                    conversationId,
                                    message
                            );

            /*
             * 请求完成
             */
            emitter.onCompletion(() -> {

                log.info(
                        "RonManus chat completed " +
                                "for request key: {}",
                        requestKey
                );

                deduplicationManager
                        .markRequestCompleted(
                                requestKey,
                                false
                        );
            });

            /*
             * 请求错误
             */
            emitter.onError(error -> {

                log.error(
                        "RonManus chat error " +
                                "for request key: {}",
                        requestKey,
                        error
                );

                deduplicationManager
                        .cancelRequest(
                                requestKey
                        );
            });

            /*
             * 请求超时
             */
            emitter.onTimeout(() -> {

                log.warn(
                        "RonManus chat timeout " +
                                "for request key: {}",
                        requestKey
                );

                deduplicationManager
                        .cancelRequest(
                                requestKey
                        );
            });

            return emitter;

        } catch (Exception e) {

            log.error(
                    "Error executing RonManus chat",
                    e
            );

            deduplicationManager
                    .cancelRequest(
                            requestKey
                    );

            return createErrorEmitter(
                    "Internal server error: "
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // RonManus - 同步接口
    // =========================================================

    /**
     * RonManus 同步聊天。
     *
     * 主要方便：
     *
     * Postman
     * Swagger
     * 后端调试
     */
    @PostMapping({
            "/RonManus/chat/sync",
            "/ronmanus/chat/sync"
    })
    @Operation(
            summary =
                    "RonManus同步聊天",
            description =
                    "RonManus同步多轮对话接口"
    )
    public ResponseEntity<ChatResponse>
    doChatWithRonManusSync(

            @Valid
            @RequestBody
            ChatRequest request,

            HttpServletResponse response) {

        String conversationId =
                request
                        .getConversationId();

        if (conversationId == null
                || conversationId.isBlank()) {

            conversationId =
                    UUID
                            .randomUUID()
                            .toString();
        }

        response.setHeader(
                "X-Conversation-ID",
                conversationId
        );

        try {

            String result =
                    ronManusChatService
                            .chatSync(
                                    conversationId,
                                    request
                                            .getMessage()
                            );

            ChatResponse chatResponse =
                    ChatResponse
                            .builder()

                            .conversationId(
                                    conversationId
                            )

                            .content(result)

                            .status(
                                    "SUCCESS"
                            )

                            .build();

            return ResponseEntity
                    .ok(chatResponse);

        } catch (
                IllegalStateException e) {

            ChatResponse chatResponse =
                    ChatResponse
                            .builder()

                            .conversationId(
                                    conversationId
                            )

                            .content(
                                    e.getMessage()
                            )

                            .status(
                                    "BUSY"
                            )

                            .build();

            return ResponseEntity
                    .status(
                            HttpStatus.CONFLICT
                    )
                    .body(
                            chatResponse
                    );

        } catch (Exception e) {

            log.error(
                    "RonManus sync chat error",
                    e
            );

            ChatResponse chatResponse =
                    ChatResponse
                            .builder()

                            .conversationId(
                                    conversationId
                            )

                            .content(
                                    e.getMessage()
                            )

                            .status(
                                    "ERROR"
                            )

                            .build();

            return ResponseEntity
                    .status(
                            HttpStatus
                                    .INTERNAL_SERVER_ERROR
                    )
                    .body(
                            chatResponse
                    );
        }
    }


    // =========================================================
    // RonManus - 旧 GET 接口兼容
    // =========================================================

    /**
     * 保留原来的 GET RonManus 接口。
     *
     * 主要用于兼容你现在已经写好的 HTML /
     * 旧 Postman 请求。
     *
     * 后续正式前端完成后可以删除。
     */
    @GetMapping("RonManus/chat/")
    @Operation(
            summary =
                    "RonManus旧版兼容接口",
            description =
                    "旧 GET 接口，后续建议迁移到 POST JSON"
    )
    public SseEmitter
    doChatWithRonManusLegacy(

            @Parameter(
                    description =
                            "用户消息内容",
                    required = true
            )
            @RequestParam
            @NotBlank
            String message,

            @RequestParam(
                    required = false
            )
            String conversationId,

            @RequestHeader(
                    value = "X-Client-ID",
                    required = false
            )
            String clientId,

            HttpServletResponse response) {

        if (conversationId == null
                || conversationId.isBlank()) {

            conversationId =
                    UUID
                            .randomUUID()
                            .toString();
        }

        response.setHeader(
                "X-Conversation-ID",
                conversationId
        );

        log.info(
                "Received legacy RonManus GET request - " +
                        "Conversation: {}, Message: {}",
                conversationId,
                abbreviate(message, 100)
        );

        return ronManusChatService
                .chatStream(
                        conversationId,
                        message
                );
    }


    // =========================================================
    // Conversation Management
    // =========================================================

    /**
     * 删除 RonManus 会话
     */
    @DeleteMapping(
            "/RonManus/conversation/{conversationId}"
    )
    public ResponseEntity<String>
    deleteRonManusConversation(

            @PathVariable
            String conversationId) {

        boolean removed =
                ronManusChatService
                        .removeConversation(
                                conversationId
                        );

        if (removed) {

            return ResponseEntity.ok(
                    "Conversation removed: "
                            + conversationId
            );
        }

        return ResponseEntity
                .status(
                        HttpStatus.CONFLICT
                )
                .body(
                        "Conversation does not exist " +
                                "or is currently running."
                );
    }


    // =========================================================
    // Stats
    // =========================================================

    /**
     * 获取系统统计信息
     */
    @GetMapping("/stats")
    @Operation(
            summary = "获取系统统计信息",
            description =
                    "获取请求去重、限流、缓存和Agent会话统计信息"
    )
    public ResponseEntity<
            Map<String, Object>>
    getStats() {

        try {

            Map<String, Object>
                    stats =

                    Map.of(

                            "deduplication",
                            deduplicationManager
                                    .getStats(),

                            "rateLimit",
                            rateLimitManager
                                    .getGlobalStats(),

                            "cache",
                            cacheManager
                                    .getStats(),

                            "ronManusConversations",
                            ronManusChatService
                                    .getConversationCount(),

                            "ronManusActiveConversations",
                            ronManusChatService
                                    .getActiveConversationCount(),

                            "timestamp",
                            LocalDateTime
                                    .now()
                    );

            log.info(
                    "Stats requested: {}",
                    stats
            );

            return ResponseEntity.ok(
                    stats
            );

        } catch (Exception e) {

            log.error(
                    "Error retrieving stats",
                    e
            );

            return ResponseEntity
                    .status(
                            HttpStatus
                                    .INTERNAL_SERVER_ERROR
                    )
                    .body(
                            Map.of(
                                    "error",
                                    "Failed to retrieve stats: "
                                            + e.getMessage()
                            )
                    );
        }
    }


    // =========================================================
    // Cache
    // =========================================================

    /**
     * 清理缓存
     */
    @PostMapping("/cache/clear")
    @Operation(
            summary = "清理缓存",
            description =
                    "清理系统中的所有缓存"
    )
    public ResponseEntity<String>
    clearCache() {

        try {

            cacheManager.clear();

            log.info(
                    "Cache cleared via API request"
            );

            return ResponseEntity.ok(
                    "Cache cleared successfully"
            );

        } catch (Exception e) {

            log.error(
                    "Error clearing cache",
                    e
            );

            return ResponseEntity
                    .status(
                            HttpStatus
                                    .INTERNAL_SERVER_ERROR
                    )
                    .body(
                            "Failed to clear cache: "
                                    + e.getMessage()
                    );
        }
    }


    // =========================================================
    // Rate Limit
    // =========================================================

    /**
     * 重置客户端限流计数
     */
    @PostMapping(
            "/rate-limit/reset/{clientId}"
    )
    @Operation(
            summary =
                    "重置客户端限流计数",
            description =
                    "重置指定客户端的限流计数器"
    )
    public ResponseEntity<String>
    resetClientRateLimit(

            @Parameter(
                    description = "客户端ID",
                    required = true
            )
            @PathVariable
            String clientId) {

        try {

            rateLimitManager
                    .resetClientCounters(
                            clientId
                    );

            log.info(
                    "Rate limit counters reset " +
                            "for client: {}",
                    clientId
            );

            return ResponseEntity.ok(
                    "Rate limit counters reset " +
                            "for client: "
                            + clientId
            );

        } catch (Exception e) {

            log.error(
                    "Error resetting rate limit " +
                            "for client: {}",
                    clientId,
                    e
            );

            return ResponseEntity
                    .status(
                            HttpStatus
                                    .INTERNAL_SERVER_ERROR
                    )
                    .body(
                            "Failed to reset rate limit: "
                                    + e.getMessage()
                    );
        }
    }


    // =========================================================
    // Helper Methods
    // =========================================================

    /**
     * 生成客户端 ID
     */
    private String generateClientId(
            String conversationId) {

        return "client_"
                + conversationId;
    }


    /**
     * 创建标准 SSE 错误响应。
     */
    private SseEmitter createErrorEmitter(
            String message) {

        SseEmitter emitter =
                new SseEmitter(
                        30_000L
                );

        try {

            emitter.send(
                    SseEmitter
                            .event()
                            .name(
                                    "error"
                            )
                            .data(
                                    message
                            )
            );

            emitter.send(
                    SseEmitter
                            .event()
                            .name(
                                    "done"
                            )
                            .data(
                                    "[DONE]"
                            )
            );

            emitter.complete();

        } catch (Exception e) {

            emitter
                    .completeWithError(e);
        }

        return emitter;
    }


    /**
     * 日志字符串截断。
     */
    private String abbreviate(
            String value,
            int maxLength) {

        if (value == null) {
            return "";
        }

        if (value.length()
                <= maxLength) {

            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }


    /**
     * 保留旧辅助方法，
     * 避免后续其它代码仍引用。
     */
    @SuppressWarnings("unused")
    private ResponseEntity<String>
    waitForOriginalRequest(
            String requestKey,
            String clientId) {

        String cachedResponse =
                cacheManager
                        .get(
                                requestKey,
                                String.class
                        );

        if (cachedResponse != null) {

            return ResponseEntity.ok(
                    cachedResponse
            );
        }

        return ResponseEntity
                .status(
                        HttpStatus.CONFLICT
                )
                .body(
                        "Duplicate request detected. " +
                                "Original request is processing. " +
                                "Please wait."
                );
    }
}
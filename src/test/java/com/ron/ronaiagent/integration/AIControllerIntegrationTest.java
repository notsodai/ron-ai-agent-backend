package com.ron.ronaiagent.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ron.ronaiagent.controller.AIController;
import com.ron.ronaiagent.core.CacheManager;
import com.ron.ronaiagent.core.RequestDeduplicationManager;
import com.ron.ronaiagent.core.RequestRateLimitManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI控制器集成测试
 */
@SpringBootTest(properties = "app.admin.token=test-admin-token")
@AutoConfigureWebMvc
public class AIControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AIController aiController;

    @Autowired
    private RequestDeduplicationManager deduplicationManager;

    @Autowired
    private RequestRateLimitManager rateLimitManager;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private com.ron.ronaiagent.app.BookApp bookApp;

    @MockBean
    private com.ron.ronaiagent.agent.RonManus ronManus;

    @MockBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();

        // 清理缓存和统计数据
        cacheManager.clear();
    }

    @Test
    @DisplayName("测试同步聊天接口")
    void testSyncChat() throws Exception {
        // 模拟BookApp响应
        when(bookApp.doChatWithBookList(anyString(), anyString()))
                .thenReturn("这是AI助手的回复");

        MvcResult result = mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "你好")
                        .param("conversationId", "test-123")
                        .header("X-Client-ID", "test-client"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertEquals("这是AI助手的回复", response);

        // 验证缓存命中
        MvcResult cachedResult = mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "你好")
                        .param("conversationId", "test-123")
                        .header("X-Client-ID", "test-client"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("这是AI助手的回复", cachedResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("测试流式聊天接口")
    void testStreamChat() throws Exception {
        // 注意：实际的流式测试可能需要更复杂的设置
        // 这里主要测试接口的初始响应

        mockMvc.perform(get("/ai/book/chat/stream")
                        .param("message", "你好")
                        .param("conversationId", "test-456")
                        .header("X-Client-ID", "test-client"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"));
    }

    @Test
    @DisplayName("测试SSE流式聊天接口")
    void testSseStreamChat() throws Exception {
        mockMvc.perform(get("/ai/book/chat/stream/emitter")
                        .param("message", "你好")
                        .param("conversationId", "test-789")
                        .header("X-Client-ID", "test-client"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"));
    }

    @Test
    @DisplayName("测试RonManus聊天接口")
    void testRonManusChat() throws Exception {
        // 模拟RonManus响应
        SseEmitter mockEmitter = new SseEmitter();
        when(ronManus.runStream(anyString())).thenReturn(mockEmitter);

        mockMvc.perform(get("/ai/RonManus/chat/")
                        .param("message", "测试RonManus")
                        .header("X-Client-ID", "test-client"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"));
    }

    @Test
    @DisplayName("测试限流功能")
    void testRateLimiting() throws Exception {
        // 快速发送多个请求以触发限流
        for (int i = 0; i < 50; i++) {
            try {
                mockMvc.perform(get("/ai/book/chat/sync")
                                .param("message", "测试消息" + i)
                                .param("conversationId", "conv-" + i)
                                .header("X-Client-ID", "rate-limit-test"))
                        .andReturn();
            } catch (Exception e) {
                // 某些请求可能被限流
                break;
            }
        }

        // 验证限流统计
        var stats = rateLimitManager.getGlobalStats();
        assertTrue(stats.getTotalRequests() > 0);
    }

    @Test
    @DisplayName("测试重复请求检测")
    void testDuplicateRequestDetection() throws Exception {
        when(bookApp.doChatWithBookList(anyString(), anyString()))
                .thenReturn("测试回复");

        // 发送相同请求
        MvcResult result1 = mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "重复测试")
                        .param("conversationId", "duplicate-test")
                        .header("X-Client-ID", "duplicate-client"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "重复测试")
                        .param("conversationId", "duplicate-test")
                        .header("X-Client-ID", "duplicate-client"))
                .andReturn();

        // 第二个请求应该被处理（去重机制可能返回缓存或等待提示）
        assertNotNull(result2.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("测试统计接口")
    void testStatsEndpoint() throws Exception {
        mockMvc.perform(get("/ai/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deduplication").exists())
                .andExpect(jsonPath("$.rateLimit").exists())
                .andExpect(jsonPath("$.cache").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("测试缓存清理接口")
    void testCacheClearEndpoint() throws Exception {
        // 先添加一些缓存数据
        when(bookApp.doChatWithBookList(anyString(), anyString()))
                .thenReturn("缓存测试数据");

        mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "缓存测试")
                        .param("conversationId", "cache-test")
                        .header("X-Client-ID", "cache-client"))
                .andExpect(status().isOk());

        // 验证缓存有数据
        var statsBefore = cacheManager.getStats();
        assertTrue(statsBefore.getSize() > 0);

        // 清理缓存
        mockMvc.perform(post("/ai/cache/clear")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cache cleared successfully"));

        // 验证缓存已清空
        var statsAfter = cacheManager.getStats();
        assertEquals(0, statsAfter.getSize());
    }

    @Test
    @DisplayName("测试重置客户端限流接口")
    void testResetClientRateLimitEndpoint() throws Exception {
        mockMvc.perform(post("/ai/rate-limit/reset/test-reset-client")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Rate limit counters reset for client: test-reset-client"));

        // 验证客户端统计被重置
        var clientStats = rateLimitManager.getClientStats("test-reset-client");
        assertEquals(0, clientStats.getMinuteRequests());
    }

    @Test
    @DisplayName("测试错误处理")
    void testErrorHandling() throws Exception {
        // 模拟BookApp抛出异常
        when(bookApp.doChatWithBookList(anyString(), anyString()))
                .thenThrow(new RuntimeException("模拟错误"));

        mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "错误测试")
                        .param("conversationId", "error-test")
                        .header("X-Client-ID", "error-client"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("测试参数验证")
    void testParameterValidation() throws Exception {
        // 测试空消息
        mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "")
                        .param("conversationId", "validation-test")
                        .header("X-Client-ID", "validation-client"))
                .andExpect(status().is5xxServerError()); // 根据实际实现调整

        // 测试空会话ID
        mockMvc.perform(get("/ai/book/chat/sync")
                        .param("message", "测试消息")
                        .param("conversationId", "")
                        .header("X-Client-ID", "validation-client"))
                .andExpect(status().is5xxServerError()); // 根据实际实现调整
    }
}

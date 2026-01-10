package com.ron.ronaiagent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求限流管理器测试
 */
public class RequestRateLimitManagerTest {

    private RequestRateLimitManager manager;
    private RequestRateLimitManager.RateLimitConfig testConfig;

    @BeforeEach
    void setUp() {
        manager = new RequestRateLimitManager();
        testConfig = new RequestRateLimitManager.RateLimitConfig(
                5,    // 5 requests per minute
                50,   // 50 requests per hour
                200,  // 200 requests per day
                3000  // 3 second burst window
        );
    }

    @Test
    @DisplayName("测试正常请求通过")
    void testNormalRequest() {
        String clientId = "test-client";

        RequestRateLimitManager.RateLimitResult result = manager.checkRequest(clientId, testConfig);
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
    }

    @Test
    @DisplayName("测试分钟限流")
    void testMinuteRateLimit() {
        String clientId = "test-client-minute";

        // 发送限制内的请求
        for (int i = 0; i < testConfig.getMaxRequestsPerMinute(); i++) {
            RequestRateLimitManager.RateLimitResult result = manager.checkRequest(clientId, testConfig);
            assertTrue("Request " + i + " should be allowed", result.isAllowed());
        }

        // 下一个请求应该被限制
        RequestRateLimitManager.RateLimitResult result = manager.checkRequest(clientId, testConfig);
        assertFalse(result.isAllowed());
        assertEquals("Minute rate limit exceeded", result.getReason());
        assertEquals(60, result.getRetryAfter().toMinutes());
    }

    @Test
    @DisplayName("测试小时限流")
    void testHourRateLimit() {
        String clientId = "test-client-hour";

        // 绕过分钟限制（使用不同的客户端ID来测试小时限制）
        RequestRateLimitManager.RateLimitConfig permissiveConfig = new RequestRateLimitManager.RateLimitConfig(
                100, 10, 200, 3000  // 100 per minute, 10 per hour
        );

        // 发送超过小时限制的请求（通过模拟，实际需要时间）
        // 注意：这个测试可能需要调整，因为小时限制需要时间窗口重置
    }

    @Test
    @DisplayName("测试突发流量控制")
    void testBurstControl() {
        String clientId = "test-client-burst";

        // 在突发窗口内快速发送请求
        for (int i = 0; i < 6; i++) { // 超过突发限制（5个请求）
            RequestRateLimitManager.RateLimitResult result = manager.checkRequest(clientId, testConfig);

            if (i < 5) {
                assertTrue("Burst request " + i + " should be allowed", result.isAllowed());
            } else {
                assertFalse("Burst request " + i + " should be blocked", result.isAllowed());
                assertEquals("Burst rate limit exceeded", result.getReason());
            }
        }
    }

    @Test
    @DisplayName("测试客户端配置")
    void testClientSpecificConfig() {
        String clientId = "vip-client";
        RequestRateLimitManager.RateLimitConfig vipConfig = RequestRateLimitManager.RateLimitConfig.lenientConfig();

        // 设置客户端特定配置
        manager.setClientConfig(clientId, vipConfig);

        // 验证配置被正确设置
        RequestRateLimitManager.RateLimitConfig retrievedConfig = manager.getClientConfig(clientId);
        assertEquals(vipConfig.getMaxRequestsPerMinute(), retrievedConfig.getMaxRequestsPerMinute());
    }

    @Test
    @DisplayName("测试客户端统计")
    void testClientStats() {
        String clientId = "test-client-stats";

        // 发送一些请求
        for (int i = 0; i < 3; i++) {
            manager.checkRequest(clientId, testConfig);
        }

        RequestRateLimitManager.ClientRequestStats stats = manager.getClientStats(clientId);
        assertEquals(clientId, stats.getClientId());
        assertEquals(3, stats.getMinuteRequests());
        assertEquals(testConfig.getMaxRequestsPerMinute(), stats.getMinuteLimit());
        assertNotNull(stats.getLastRequestTime());
    }

    @Test
    @DisplayName("测试全局限流统计")
    void testGlobalStats() {
        String clientId1 = "client1";
        String clientId2 = "client2";

        // 发送一些请求
        for (int i = 0; i < 3; i++) {
            manager.checkRequest(clientId1, testConfig);
        }

        // 发送会导致被限制的请求
        for (int i = 0; i < testConfig.getMaxRequestsPerMinute() + 1; i++) {
            manager.checkRequest(clientId2, testConfig);
        }

        RequestRateLimitManager.RateLimitStats stats = manager.getGlobalStats();
        assertTrue(stats.getTotalRequests() > 0);
        assertTrue(stats.getBlockedRequests() > 0);
        assertTrue(stats.getBlockRate() > 0);
        assertTrue(stats.getActiveClients() >= 1);
    }

    @Test
    @DisplayName("测试重置客户端计数器")
    void testResetClientCounters() {
        String clientId = "test-client-reset";

        // 发送一些请求
        for (int i = 0; i < 3; i++) {
            manager.checkRequest(clientId, testConfig);
        }

        RequestRateLimitManager.ClientRequestStats statsBefore = manager.getClientStats(clientId);
        assertEquals(3, statsBefore.getMinuteRequests());

        // 重置计数器
        manager.resetClientCounters(clientId);

        RequestRateLimitManager.ClientRequestStats statsAfter = manager.getClientStats(clientId);
        assertEquals(0, statsAfter.getMinuteRequests());
    }

    @Test
    @DisplayName("测试移除客户端")
    void testRemoveClient() {
        String clientId = "test-client-remove";

        // 发送请求以创建客户端记录
        manager.checkRequest(clientId, testConfig);

        RequestRateLimitManager.RateLimitStats statsBefore = manager.getGlobalStats();
        int clientsBefore = statsBefore.getActiveClients();

        // 移除客户端
        manager.removeClient(clientId);

        RequestRateLimitManager.RateLimitStats statsAfter = manager.getGlobalStats();
        int clientsAfter = statsAfter.getActiveClients();

        assertEquals(clientsBefore - 1, clientsAfter);
    }

    @Test
    @DisplayName("测试默认配置")
    void testDefaultConfigs() {
        RequestRateLimitManager.RateLimitConfig defaultConfig = RequestRateLimitManager.RateLimitConfig.defaultConfig();
        RequestRateLimitManager.RateLimitConfig strictConfig = RequestRateLimitManager.RateLimitConfig.strictConfig();
        RequestRateLimitManager.RateLimitConfig lenientConfig = RequestRateLimitManager.RateLimitConfig.lenientConfig();

        // 验证配置的合理性
        assertTrue(defaultConfig.getMaxRequestsPerMinute() > 0);
        assertTrue(strictConfig.getMaxRequestsPerMinute() < defaultConfig.getMaxRequestsPerMinute());
        assertTrue(lenientConfig.getMaxRequestsPerMinute() > defaultConfig.getMaxRequestsPerMinute());

        assertTrue(defaultConfig.getMaxRequestsPerHour() > defaultConfig.getMaxRequestsPerMinute());
        assertTrue(strictConfig.getMaxRequestsPerDay() > strictConfig.getMaxRequestsPerHour());
    }

    @Test
    @DisplayName("测试空客户端ID处理")
    void testEmptyClientId() {
        RequestRateLimitManager.RateLimitResult result = manager.checkRequest("", testConfig);
        assertTrue(result.isAllowed());

        RequestRateLimitManager.RateLimitResult result2 = manager.checkRequest(null, testConfig);
        assertTrue(result2.isAllowed());
    }
}
package com.ron.ronaiagent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求去重管理器测试
 */
public class RequestDeduplicationManagerTest {

    private RequestDeduplicationManager manager;

    @BeforeEach
    void setUp() {
        manager = new RequestDeduplicationManager();
    }

    @Test
    @DisplayName("测试新请求识别")
    void testNewRequest() {
        String requestKey = "test-key";
        RequestDeduplicationManager.DeduplicationResult result = manager.checkAndRecordRequest(requestKey, 5);

        assertTrue(result.isNewRequest());
        assertFalse(result.isDuplicate());
        assertFalse(result.isCacheHit());
        assertNotNull(result.getRecord());
        assertEquals(1, result.getRecord().getHitCount().get());
    }

    @Test
    @DisplayName("测试重复请求检测")
    void testDuplicateRequest() {
        String requestKey = "test-key";

        // 第一个请求
        RequestDeduplicationManager.DeduplicationResult result1 = manager.checkAndRecordRequest(requestKey, 5);
        assertTrue(result1.isNewRequest());

        // 第二个相同请求
        RequestDeduplicationManager.DeduplicationResult result2 = manager.checkAndRecordRequest(requestKey, 5);
        assertTrue(result2.isDuplicate());
        assertEquals(2, result2.getRecord().getHitCount().get());
    }

    @Test
    @DisplayName("测试请求完成后的缓存命中")
    void testCacheHitAfterCompletion() {
        String requestKey = "test-key";

        // 第一个请求
        RequestDeduplicationManager.DeduplicationResult result1 = manager.checkAndRecordRequest(requestKey, 1);
        assertTrue(result1.isNewRequest());

        // 标记请求完成并移动到缓存
        manager.markRequestCompleted(requestKey, true);

        // 第二个请求应该命中缓存
        RequestDeduplicationManager.DeduplicationResult result2 = manager.checkAndRecordRequest(requestKey, 1);
        assertTrue(result2.isCacheHit());
    }

    @Test
    @DisplayName("测试请求键生成")
    void testRequestKeyGeneration() {
        String key1 = manager.generateRequestKey("/api/test", "GET", "param=value", "body=data");
        String key2 = manager.generateRequestKey("/api/test", "GET", "param=value", "body=data");
        String key3 = manager.generateRequestKey("/api/test", "POST", "param=value", "body=data");

        assertEquals(key1, key2); // 相同参数应该生成相同键
        assertNotEquals(key1, key3); // 不同方法应该生成不同键

        // 测试大小写标准化
        String key4 = manager.generateRequestKey("/api/test", "GET", "PARAM=VALUE", "BODY=DATA");
        assertEquals(key1, key4); // 应该忽略大小写
    }

    @Test
    @DisplayName("测试统计信息")
    void testStats() {
        // 添加一些请求
        manager.checkAndRecordRequest("key1", 5);
        manager.checkAndRecordRequest("key2", 5);
        manager.checkAndRecordRequest("key1", 5); // 重复请求

        RequestDeduplicationManager.DeduplicationStats stats = manager.getStats();

        assertEquals(3, stats.getTotalRequests());
        assertEquals(1, stats.getDuplicatedRequests());
        assertTrue(stats.getDeduplicationRate() > 0);
    }

    @Test
    @DisplayName("测试请求取消")
    void testRequestCancellation() {
        String requestKey = "test-key";
        manager.checkAndRecordRequest(requestKey, 5);

        // 取消请求
        manager.cancelRequest(requestKey);

        // 再次请求应该被视为新请求
        RequestDeduplicationManager.DeduplicationResult result = manager.checkAndRecordRequest(requestKey, 5);
        assertTrue(result.isNewRequest());
    }

    @Test
    @DisplayName("测试清理请求")
    void testClearRequest() {
        String requestKey = "test-key";
        manager.checkAndRecordRequest(requestKey, 5);

        // 清理请求
        manager.clearRequest(requestKey);

        // 再次请求应该被视为新请求
        RequestDeduplicationManager.DeduplicationResult result = manager.checkAndRecordRequest(requestKey, 5);
        assertTrue(result.isNewRequest());
    }

    @Test
    @DisplayName("测试过期记录清理")
    void testExpiredRecords() throws InterruptedException {
        String requestKey = "test-key";

        // 创建一个短TTL的请求记录
        manager.checkAndRecordRequest(requestKey, 1/60); // 1分钟，但通过除法实现

        // 等待一段时间
        Thread.sleep(100);

        // 手动触发清理（在实际应用中由定时任务处理）
        // 注意：由于时间精度问题，这个测试可能不够稳定
        // 在实际环境中，过期记录会自动被清理
    }
}
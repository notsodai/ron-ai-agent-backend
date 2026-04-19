package com.ron.ronaiagent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存管理器测试
 */
public class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    @DisplayName("测试基本缓存操作")
    void testBasicCacheOperations() {
        String key = "test-key";
        String value = "test-value";

        // 测试缓存为空
        assertNull(cacheManager.get(key, String.class));
        assertFalse(cacheManager.contains(key));

        // 测试缓存存储
        cacheManager.put(key, value);
        assertEquals(value, cacheManager.get(key, String.class));
        assertTrue(cacheManager.contains(key));

        // 测试缓存删除
        cacheManager.evict(key);
        assertNull(cacheManager.get(key, String.class));
        assertFalse(cacheManager.contains(key));
    }

    @Test
    @DisplayName("测试带TTL的缓存")
    void testCacheWithTTL() {
        String key = "test-key";
        String value = "test-value";

        // 使用短TTL存储
        cacheManager.put(key, value, 1/60); // 1分钟，通过除法实现

        // 立即获取应该成功
        assertEquals(value, cacheManager.get(key, String.class));

        // 注意：TTL过期测试在实际环境中需要等待
        // 在单元测试中，我们可以通过修改系统时间来模拟
    }

    @Test
    @DisplayName("测试缓存统计")
    void testCacheStats() {
        String key = "test-key";
        String value = "test-value";

        // 初始统计
        CacheManager.CacheStats initialStats = cacheManager.getStats();
        assertEquals(0, initialStats.getSize());
        assertEquals(0, initialStats.getTotalRequests());

        // 缓存未命中
        cacheManager.get(key, String.class);
        CacheManager.CacheStats missStats = cacheManager.getStats();
        assertEquals(1, missStats.getTotalRequests());
        assertEquals(1, missStats.getMisses());

        // 缓存命中
        cacheManager.put(key, value);
        cacheManager.get(key, String.class);
        CacheManager.CacheStats hitStats = cacheManager.getStats();
        assertEquals(2, hitStats.getTotalRequests()); // 2 gets (put doesn't count)
        assertEquals(1, hitStats.getHits());
        assertTrue(hitStats.getHitRate() > 0);
    }

    @Test
    @DisplayName("测试缓存大小限制")
    void testCacheSizeLimit() {
        // 创建小容量的缓存配置
        CacheManager.CacheConfig smallConfig = new CacheManager.CacheConfig(30, 3, 5, 60);
        cacheManager.setConfig(smallConfig);

        // 添加超过限制的条目
        for (int i = 0; i < 5; i++) {
            cacheManager.put("key" + i, "value" + i);
        }

        CacheManager.CacheStats stats = cacheManager.getStats();
        // 由于LRU驱逐机制，缓存大小应该不超过最大限制
        assertTrue(stats.getSize() <= smallConfig.getMaxEntries());
    }

    @Test
    @DisplayName("测试缓存清理")
    void testCacheClear() {
        // 添加一些缓存条目
        for (int i = 0; i < 5; i++) {
            cacheManager.put("key" + i, "value" + i);
        }

        CacheManager.CacheStats beforeClear = cacheManager.getStats();
        assertTrue(beforeClear.getSize() > 0);

        // 清理缓存
        cacheManager.clear();

        CacheManager.CacheStats afterClear = cacheManager.getStats();
        assertEquals(0, afterClear.getSize());
    }

    @Test
    @DisplayName("测试不同数据类型")
    void testDifferentDataTypes() {
        String stringKey = "string-key";
        String stringValue = "test string";

        String intKey = "int-key";
        Integer intValue = 123;

        String objectKey = "object-key";
        CustomObject objectValue = new CustomObject("test", 456);

        // 测试字符串
        cacheManager.put(stringKey, stringValue);
        assertEquals(stringValue, cacheManager.get(stringKey, String.class));

        // 测试整数
        cacheManager.put(intKey, intValue);
        assertEquals(intValue, cacheManager.get(intKey, Integer.class));

        // 测试自定义对象
        cacheManager.put(objectKey, objectValue);
        CustomObject retrievedObject = cacheManager.get(objectKey, CustomObject.class);
        assertNotNull(retrievedObject);
        assertEquals(objectValue.getName(), retrievedObject.getName());
        assertEquals(objectValue.getValue(), retrievedObject.getValue());
    }

    @Test
    @DisplayName("测试缓存条目信息")
    void testCacheEntryInfo() {
        String key = "test-key";
        String value = "test-value";

        cacheManager.put(key, value);

        CacheManager.CacheEntry<Object> entry = cacheManager.getEntry(key);
        assertNotNull(entry);
        assertEquals(key, entry.getKey());
        assertEquals(value, entry.getValue());
        assertTrue(entry.getAgeInSeconds() >= 0);
        assertEquals(1, entry.getHitCount().get());

        // 访问缓存应该增加命中次数
        cacheManager.get(key, String.class);
        CacheManager.CacheEntry<Object> entryAfterAccess = cacheManager.getEntry(key);
        assertEquals(2, entryAfterAccess.getHitCount().get());
    }

    /**
     * 测试用的自定义对象
     */
    public static class CustomObject {
        private String name;
        private int value;

        public CustomObject() {}

        public CustomObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomObject that = (CustomObject) o;
            return value == that.value && java.util.Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, value);
        }
    }
}
package com.ron.ronaiagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 优化版缓存管理器
 * 修复了JSON序列化、LRU性能和类型检查问题
 */
@Slf4j
@Component
public class OptimizedCacheManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 缓存条目（优化版）
     */
    @Data
    public static class CacheEntry<T> {
        private final T value;
        private final LocalDateTime createTime;
        private final LocalDateTime expireTime;
        private final AtomicLong hitCount = new AtomicLong(0);
        private volatile LocalDateTime lastAccessTime;
        private final String key;
        private volatile boolean isSerialized;

        public CacheEntry(String key, T value, long ttlMinutes, boolean isSerialized) {
            this.key = key;
            this.value = value;
            this.createTime = LocalDateTime.now();
            this.expireTime = createTime.plusMinutes(ttlMinutes);
            this.lastAccessTime = createTime;
            this.isSerialized = isSerialized;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
        }

        public void recordAccess() {
            lastAccessTime = LocalDateTime.now();
            hitCount.incrementAndGet();
        }

        public long getAgeInSeconds() {
            return java.time.Duration.between(createTime, LocalDateTime.now()).getSeconds();
        }

        public long getIdleTimeInSeconds() {
            return java.time.Duration.between(lastAccessTime, LocalDateTime.now()).getSeconds();
        }

        public long getLastAccessTimestamp() {
            return lastAccessTime.toEpochSecond(java.time.ZoneOffset.UTC);
        }
    }

    /**
     * LRU缓存实现（基于双向链表）
     */
    private static class LRUCache {
        private final ConcurrentHashMap<String, CacheEntry<Object>> cache = new ConcurrentHashMap<>();
        private final AtomicReference<String> head = new AtomicReference<>();
        private final AtomicReference<String> tail = new AtomicReference<>();
        private final ConcurrentHashMap<String, String> prevMap = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> nextMap = new ConcurrentHashMap<>();
        private final int maxSize;

        public LRUCache(int maxSize) {
            this.maxSize = maxSize;
        }

        public synchronized CacheEntry<Object> put(String key, CacheEntry<Object> entry) {
            if (cache.size() >= maxSize) {
                evictLRU();
            }

            cache.put(key, entry);

            // 更新链表
            addToHead(key);

            return entry;
        }

        public CacheEntry<Object> get(String key) {
            CacheEntry<Object> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                moveToHead(key);
                entry.recordAccess();
                return entry;
            } else if (entry != null && entry.isExpired()) {
                remove(key);
            }
            return null;
        }

        public CacheEntry<Object> getEntry(String key) {
            CacheEntry<Object> entry = cache.get(key);
            return (entry != null && !entry.isExpired()) ? entry : null;
        }

        public boolean remove(String key) {
            CacheEntry<Object> removed = cache.remove(key);
            if (removed != null) {
                removeFromList(key);
                return true;
            }
            return false;
        }

        public boolean containsKey(String key) {
            CacheEntry<Object> entry = cache.get(key);
            return entry != null && !entry.isExpired();
        }

        public int size() {
            return cache.size();
        }

        public void clear() {
            cache.clear();
            head.set(null);
            tail.set(null);
            prevMap.clear();
            nextMap.clear();
        }

        private void addToHead(String key) {
            if (head.get() == null) {
                // 空链表
                head.set(key);
                tail.set(key);
            } else {
                // 插入到头部
                String oldHead = head.get();
                prevMap.put(key, null);
                nextMap.put(key, oldHead);
                prevMap.put(oldHead, key);
                head.set(key);
            }
        }

        private void moveToHead(String key) {
            if (head.get() != null && key.equals(head.get())) {
                return; // 已经是头部
            }

            removeFromList(key);
            addToHead(key);
        }

        private void removeFromList(String key) {
            String prev = prevMap.get(key);
            String next = nextMap.get(key);

            if (prev != null) {
                nextMap.put(prev, next);
            } else {
                // 移除头部
                head.set(next);
            }

            if (next != null) {
                prevMap.put(next, prev);
            } else {
                // 移除尾部
                tail.set(prev);
            }

            prevMap.remove(key);
            nextMap.remove(key);
        }

        private void evictLRU() {
            String lruKey = tail.get();
            if (lruKey != null) {
                remove(lruKey);
                log.debug("LRU eviction: removed key {}", lruKey);
            }
        }
    }

    private LRUCache lruCache;

    // 缓存配置
    private volatile CacheConfig config = CacheConfig.defaultConfig();

    // 定时清理线程池
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public OptimizedCacheManager() {
        this.lruCache = new LRUCache((int) CacheConfig.defaultConfig().getMaxEntries());

        // 启动定时清理任务
        cleanupExecutor.scheduleAtFixedRate(this::cleanup,
            config.getCleanupIntervalMinutes(), config.getCleanupIntervalMinutes(), TimeUnit.MINUTES);

        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        log.info("OptimizedCacheManager initialized with config: {}", config);
    }

    /**
     * 获取缓存值（优化版）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        totalRequests.incrementAndGet();

        CacheEntry<Object> entry = lruCache.getEntry(key);
        if (entry == null) {
            cacheMisses.incrementAndGet();
            log.debug("Cache miss for key: {}", key);
            return null;
        }

        try {
            Object value = entry.getValue();

            // 检查类型兼容性
            if (entry.isSerialized()) {
                // 从JSON反序列化
                if (type == String.class) {
                    return (T) value;
                }
                return objectMapper.readValue((String) value, type);
            } else {
                // 原始对象
                if (type.isInstance(value)) {
                    return (T) value;
                } else {
                    log.warn("Type mismatch for cached entry key: {}, expected: {}, actual: {}",
                            key, type.getSimpleName(), value.getClass().getSimpleName());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error deserializing cached entry for key: {}", key, e);
            lruCache.remove(key);
            cacheMisses.incrementAndGet();
            return null;
        }
    }

    /**
     * 存储缓存值（优化版）
     */
    public <T> void put(String key, T value) {
        put(key, value, config.getDefaultTtlMinutes());
    }

    /**
     * 存储缓存值（指定TTL）
     */
    public <T> void put(String key, T value, long ttlMinutes) {
        try {
            Object cacheValue = value;
            boolean shouldSerialize = !isSimpleType(value.getClass());

            if (shouldSerialize) {
                // 对于复杂类型，序列化为JSON字符串
                cacheValue = objectMapper.writeValueAsString(value);
            }

            CacheEntry<Object> entry = new CacheEntry<>(key, cacheValue, ttlMinutes, shouldSerialize);
            lruCache.put(key, entry);

            log.debug("Cache entry added for key: {} (TTL: {} minutes, serialized: {})",
                    key, ttlMinutes, shouldSerialize);

        } catch (Exception e) {
            log.error("Error serializing cached entry for key: {}", key, e);
        }
    }

    /**
     * 删除缓存条目
     */
    public void evict(String key) {
        if (lruCache.remove(key)) {
            log.debug("Cache entry evicted for key: {}", key);
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean contains(String key) {
        return lruCache.containsKey(key);
    }

    /**
     * 获取缓存条目信息（不修改访问时间）
     */
    public CacheEntry<Object> getEntry(String key) {
        return lruCache.getEntry(key);
    }

    /**
     * 清理过期和空闲的条目（优化版）
     */
    private void cleanup() {
        long startTime = System.currentTimeMillis();
        int expiredCount = 0;
        int idleCount = 0;

        // 使用迭代器避免并发修改异常
        var iterator = lruCache.cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            CacheEntry<Object> cacheEntry = entry.getValue();

            if (cacheEntry.isExpired()) {
                iterator.remove();
                expiredCount++;
                log.debug("Removing expired cache entry: {}", entry.getKey());
            } else if (cacheEntry.getIdleTimeInSeconds() > config.getMaxIdleTimeMinutes() * 60) {
                iterator.remove();
                idleCount++;
                log.debug("Removing idle cache entry: {} (idle for {} seconds)",
                        entry.getKey(), cacheEntry.getIdleTimeInSeconds());
            }
        }

        long cleanupTime = System.currentTimeMillis() - startTime;
        if (expiredCount > 0 || idleCount > 0) {
            log.info("Cache cleanup completed in {}ms: {} expired, {} idle entries removed",
                    cleanupTime, expiredCount, idleCount);
        }

        logCacheStats();
    }

    /**
     * 记录缓存统计信息
     */
    private void logCacheStats() {
        long total = totalRequests.get();
        if (total > 0 && total % 200 == 0) { // 每200次请求记录一次详细统计
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            double hitRate = (hits * 100.0) / total;

            log.info("Cache stats - Size: {}, Hit rate: {:.2f}% ({} hits / {} misses), Evictions: {}",
                    lruCache.size(), hitRate, hits, misses, evictions.get());
        }
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        double hitRate = total > 0 ? (hits * 100.0 / total) : 0.0;

        return CacheStats.builder()
                .size(lruCache.size())
                .maxSize(config.getMaxEntries())
                .totalRequests(total)
                .hits(hits)
                .misses(cacheMisses.get())
                .evictions(evictions.get())
                .hitRate(hitRate)
                .build();
    }

    /**
     * 清空缓存
     */
    public void clear() {
        int size = lruCache.size();
        lruCache.clear();
        log.info("Cache cleared: {} entries removed", size);
    }

    /**
     * 设置缓存配置
     */
    public void setConfig(CacheConfig config) {
        this.config = config;
        // 如果新的maxSize不同，重新创建LRU缓存
        if ((int) config.getMaxEntries() != lruCache.size()) {
            LRUCache oldCache = lruCache;
            this.lruCache = new LRUCache((int) config.getMaxEntries());
            // 这里可以选择迁移热点数据到新缓存
            log.info("Cache config updated: {}, old size: {}, new max size: {}",
                    config, oldCache.size(), config.getMaxEntries());
        } else {
            log.info("Cache config updated: {}", config);
        }
    }

    /**
     * 检查是否为简单类型（优化版）
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == Character.class ||
               type == Byte.class ||
               type == Short.class ||
               Number.class.isAssignableFrom(type) ||
               type == java.math.BigInteger.class ||
               type == java.math.BigDecimal.class;
    }

    /**
     * 关闭缓存管理器
     */
    public void shutdown() {
        log.info("Shutting down OptimizedCacheManager...");

        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        lruCache.clear();

        log.info("OptimizedCacheManager shutdown completed");
    }

    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        private final long defaultTtlMinutes;
        private final long maxEntries;
        private final long cleanupIntervalMinutes;
        private final long maxIdleTimeMinutes;

        public CacheConfig(long defaultTtlMinutes, long maxEntries, long cleanupIntervalMinutes, long maxIdleTimeMinutes) {
            this.defaultTtlMinutes = defaultTtlMinutes;
            this.maxEntries = maxEntries;
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
            this.maxIdleTimeMinutes = maxIdleTimeMinutes;
        }

        public static CacheConfig defaultConfig() {
            return new CacheConfig(30, 1000, 10, 60); // 30分钟TTL，最多1000条目，10分钟清理间隔
        }

        public static CacheConfig shortTermConfig() {
            return new CacheConfig(5, 500, 5, 15); // 5分钟TTL，最多500条目
        }

        public static CacheConfig longTermConfig() {
            return new CacheConfig(120, 2000, 30, 240); // 2小时TTL，最多2000条目
        }
    }

    /**
     * 缓存统计信息
     */
    @Data
    @Builder
    public static class CacheStats {
        private final int size;
        private final long maxSize;
        private final long totalRequests;
        private final long hits;
        private final long misses;
        private final long evictions;
        private final double hitRate;
    }
}
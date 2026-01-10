package com.ron.ronaiagent.core;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 优化版请求去重管理器
 * 修复了原版本中的类型安全和性能问题
 */
@Slf4j
@Component
public class OptimizedRequestDeduplicationManager {

    /**
     * 请求记录信息
     */
    @Data
    public static class RequestRecord {
        private final String requestId;
        private final String requestKey;
        private final LocalDateTime createTime;
        private final LocalDateTime expireTime;
        private final AtomicInteger hitCount;
        private volatile boolean completed;

        public RequestRecord(String requestId, String requestKey, long ttlMinutes) {
            this.requestId = requestId;
            this.requestKey = requestKey;
            this.createTime = LocalDateTime.now();
            this.expireTime = createTime.plusMinutes(ttlMinutes);
            this.hitCount = new AtomicInteger(1);
            this.completed = false;
        }

        public void incrementHit() {
            hitCount.incrementAndGet();
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
        }

        public long getAgeInSeconds() {
            return java.time.Duration.between(createTime, LocalDateTime.now()).getSeconds();
        }
    }

    // 存储正在处理的请求记录
    private final ConcurrentHashMap<String, RequestRecord> activeRequests = new ConcurrentHashMap<>();

    // 存储已完成的请求记录（用于短时间内的去重）
    private final ConcurrentHashMap<String, RequestRecord> recentCompletedRequests = new ConcurrentHashMap<>();

    // 定时清理线程池
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    // 统计信息
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger duplicatedRequests = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);

    // 预编译的消息摘要实例
    private static final MessageDigest SHA256_DIGEST;

    static {
        try {
            SHA256_DIGEST = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public OptimizedRequestDeduplicationManager() {
        // 启动定时清理任务，每分钟清理一次过期记录
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRecords, 1, 1, TimeUnit.MINUTES);

        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        log.info("OptimizedRequestDeduplicationManager initialized with cleanup scheduler");
    }

    /**
     * 生成请求的唯一键（优化版）
     */
    public String generateRequestKey(String endpoint, String method, String params, String body) {
        try {
            // 使用StringBuilder提高性能
            StringBuilder keyBuilder = new StringBuilder(256);
            keyBuilder.append(method).append(":");
            keyBuilder.append(endpoint != null ? endpoint.toLowerCase().trim() : "").append(":");

            // 安全处理参数
            if (params != null && !params.isEmpty()) {
                keyBuilder.append(normalizeInput(params));
            }
            keyBuilder.append(":");

            // 安全处理请求体
            if (body != null && !body.isEmpty()) {
                keyBuilder.append(normalizeInput(body));
            }

            // 使用预编译的MessageDigest实例，同步避免并发问题
            byte[] hash;
            synchronized (SHA256_DIGEST) {
                hash = SHA256_DIGEST.digest(keyBuilder.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 使用完整的Base64编码，避免截断导致的碰撞
            return java.util.Base64.getUrlEncoder().encodeToString(hash);

        } catch (Exception e) {
            log.warn("Failed to generate request key, using enhanced fallback", e);
            return generateFallbackKey(endpoint, method, params, body);
        }
    }

    /**
     * 标准化输入参数
     */
    private String normalizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // 移除空白字符并转小写
        String normalized = input.trim().toLowerCase();

        // 限制长度，避免过长的键
        if (normalized.length() > 1000) {
            normalized = normalized.substring(0, 1000);
        }

        return normalized;
    }

    /**
     * 降级请求键生成（更安全）
     */
    private String generateFallbackKey(String endpoint, String method, String params, String body) {
        try {
            StringBuilder fallbackBuilder = new StringBuilder();
            fallbackBuilder.append(method).append(":");
            fallbackBuilder.append(endpoint != null ? endpoint : "").append(":");
            fallbackBuilder.append(params != null ? Integer.toHexString(params.hashCode()) : "").append(":");
            fallbackBuilder.append(body != null ? Integer.toHexString(body.hashCode()) : "");

            // 使用时间戳确保唯一性
            fallbackBuilder.append(":").append(System.currentTimeMillis());

            return java.util.Base64.getUrlEncoder().encodeToString(
                fallbackBuilder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 最后的降级方案
            return "fallback_" + method + "_" + endpoint.hashCode() + "_" + System.currentTimeMillis();
        }
    }

    /**
     * 检查并记录请求
     */
    public DeduplicationResult checkAndRecordRequest(String requestKey, long ttlMinutes) {
        totalRequests.incrementAndGet();

        // 检查是否有正在处理的相同请求
        RequestRecord activeRecord = activeRequests.get(requestKey);
        if (activeRecord != null && !activeRecord.isExpired() && !activeRecord.isCompleted()) {
            duplicatedRequests.incrementAndGet();
            activeRecord.incrementHit();
            log.debug("Duplicate request detected for key: {}, hit count: {}",
                     requestKey, activeRecord.getHitCount().get());
            return DeduplicationResult.duplicate(activeRecord);
        }

        // 检查最近完成的相同请求（缓存命中）
        RequestRecord completedRecord = recentCompletedRequests.get(requestKey);
        if (completedRecord != null && !completedRecord.isExpired()) {
            cacheHits.incrementAndGet();
            log.debug("Cache hit for recently completed request: {}", requestKey);
            return DeduplicationResult.cacheHit(completedRecord);
        }

        // 创建新的请求记录
        String requestId = java.util.UUID.randomUUID().toString();
        RequestRecord newRecord = new RequestRecord(requestId, requestKey, ttlMinutes);
        activeRequests.put(requestKey, newRecord);

        log.debug("New request recorded: {} -> {}", requestKey, requestId);
        return DeduplicationResult.newRequest(newRecord);
    }

    /**
     * 标记请求为已完成
     */
    public void markRequestCompleted(String requestKey, boolean moveCompleted) {
        RequestRecord record = activeRequests.get(requestKey);
        if (record != null) {
            record.setCompleted(true);

            if (moveCompleted) {
                // 移动到已完成请求的缓存中（较短TTL）
                RequestRecord completedRecord = new RequestRecord(
                    record.getRequestId(),
                    requestKey,
                    1 // 已完成请求缓存1分钟
                );
                completedRecord.setCompleted(true);
                recentCompletedRequests.put(requestKey, completedRecord);
                activeRequests.remove(requestKey);
            } else {
                // 直接删除
                activeRequests.remove(requestKey);
            }

            log.debug("Request completed: {}", requestKey);
        }
    }

    /**
     * 取消请求记录
     */
    public void cancelRequest(String requestKey) {
        RequestRecord record = activeRequests.remove(requestKey);
        if (record != null) {
            record.setCompleted(true);
            log.debug("Request cancelled: {}", requestKey);
        }
    }

    /**
     * 强制清理指定键的所有记录
     */
    public void clearRequest(String requestKey) {
        activeRequests.remove(requestKey);
        recentCompletedRequests.remove(requestKey);
        log.debug("Request records cleared for key: {}", requestKey);
    }

    /**
     * 清理所有过期记录（优化版）
     */
    private void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();
        int activeCleaned = 0;
        int completedCleaned = 0;

        // 清理活跃请求中的过期记录
        activeCleaned = activeRequests.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(entry -> {
                    log.debug("Removing expired active request: {}", entry.getKey());
                    return entry.getKey();
                })
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        keys -> {
                            keys.forEach(activeRequests::remove);
                            return keys.size();
                        }));

        // 清理已完成请求中的过期记录
        completedCleaned = recentCompletedRequests.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(entry -> {
                    log.debug("Removing expired completed request: {}", entry.getKey());
                    return entry.getKey();
                })
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        keys -> {
                            keys.forEach(recentCompletedRequests::remove);
                            return keys.size();
                        }));

        if (activeCleaned > 0 || completedCleaned > 0) {
            log.debug("Cleanup completed in {}ms: {} active, {} completed records removed",
                     System.currentTimeMillis() - now, activeCleaned, completedCleaned);
        }

        // 记录统计信息
        logStats();
    }

    /**
     * 记录统计信息
     */
    private void logStats() {
        int activeCount = activeRequests.size();
        int completedCount = recentCompletedRequests.size();
        int total = totalRequests.get();
        int duplicated = duplicatedRequests.get();
        int cached = cacheHits.get();

        if (total > 0 && total % 100 == 0) { // 每100次请求记录一次详细统计
            log.info("Request deduplication stats - Active: {}, Completed: {}, " +
                    "Total: {}, Duplicated: {}, Cache Hits: {} ({}% deduplication rate)",
                    activeCount, completedCount, total, duplicated, cached,
                    (duplicated + cached) * 100.0 / total);
        }
    }

    /**
     * 获取统计信息
     */
    public DeduplicationStats getStats() {
        return DeduplicationStats.builder()
                .totalRequests(totalRequests.get())
                .duplicatedRequests(duplicatedRequests.get())
                .cacheHits(cacheHits.get())
                .activeRequests(activeRequests.size())
                .completedRequests(recentCompletedRequests.size())
                .deduplicationRate(calculateDeduplicationRate())
                .build();
    }

    private double calculateDeduplicationRate() {
        int total = totalRequests.get();
        int prevented = duplicatedRequests.get() + cacheHits.get();
        return total > 0 ? (prevented * 100.0 / total) : 0.0;
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        log.info("Shutting down OptimizedRequestDeduplicationManager...");

        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 清理所有记录
        activeRequests.clear();
        recentCompletedRequests.clear();

        log.info("OptimizedRequestDeduplicationManager shutdown completed");
    }

    /**
     * 去重结果
     */
    @Data
    public static class DeduplicationResult {
        private final ResultType resultType;
        private final RequestRecord record;

        private DeduplicationResult(ResultType resultType, RequestRecord record) {
            this.resultType = resultType;
            this.record = record;
        }

        public static DeduplicationResult newRequest(RequestRecord record) {
            return new DeduplicationResult(ResultType.NEW, record);
        }

        public static DeduplicationResult duplicate(RequestRecord record) {
            return new DeduplicationResult(ResultType.DUPLICATE, record);
        }

        public static DeduplicationResult cacheHit(RequestRecord record) {
            return new DeduplicationResult(ResultType.CACHE_HIT, record);
        }

        public boolean isNewRequest() {
            return resultType == ResultType.NEW;
        }

        public boolean isDuplicate() {
            return resultType == ResultType.DUPLICATE;
        }

        public boolean isCacheHit() {
            return resultType == ResultType.CACHE_HIT;
        }

        public enum ResultType {
            NEW, DUPLICATE, CACHE_HIT
        }
    }

    /**
     * 统计信息
     */
    @Data
    @Builder
    public static class DeduplicationStats {
        private final int totalRequests;
        private final int duplicatedRequests;
        private final int cacheHits;
        private final int activeRequests;
        private final int completedRequests;
        private final double deduplicationRate;
    }
}
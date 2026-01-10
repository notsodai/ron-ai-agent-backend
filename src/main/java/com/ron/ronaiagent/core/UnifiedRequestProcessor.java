package com.ron.ronaiagent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一请求处理器
 * 提供统一的请求处理、去重和限流逻辑
 */
@Slf4j
@Component
public class UnifiedRequestProcessor {

    private final RequestDeduplicationManager deduplicationManager;
    private final RequestRateLimitManager rateLimitManager;
    private final CacheManager cacheManager;

    // 请求处理统计
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger duplicateRejected = new AtomicInteger(0);
    private final AtomicInteger rateLimitRejected = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);

    public UnifiedRequestProcessor(
            RequestDeduplicationManager deduplicationManager,
            RequestRateLimitManager rateLimitManager,
            CacheManager cacheManager) {
        this.deduplicationManager = deduplicationManager;
        this.rateLimitManager = rateLimitManager;
        this.cacheManager = cacheManager;
    }

    /**
     * 处理请求的统一入口
     */
    public <T> RequestProcessorResult<T> processRequest(
            RequestContext context,
            RequestHandler<T> handler) {

        totalProcessed.incrementAndGet();
        log.debug("Processing request: {} for client: {}", context.getRequestKey(), context.getClientId());

        try {
            // 1. 限流检查
            RequestProcessorResult<T> rateLimitResult = checkRateLimit(context);
            if (rateLimitResult != null) {
                return rateLimitResult;
            }

            // 2. 去重检查
            RequestProcessorResult<T> deduplicationResult = checkDeduplication(context);
            if (deduplicationResult != null) {
                return deduplicationResult;
            }

            // 3. 缓存检查
            RequestProcessorResult<T> cacheResult = checkCache(context);
            if (cacheResult != null) {
                return cacheResult;
            }

            // 4. 执行实际处理
            return executeRequest(context, handler);

        } catch (Exception e) {
            log.error("Error processing request: {}", context.getRequestKey(), e);
            cleanupRequest(context);
            return RequestProcessorResult.error("Internal server error: " + e.getMessage());
        }
    }

    /**
     * 检查限流
     */
    private <T> RequestProcessorResult<T> checkRateLimit(RequestContext context) {
        RequestRateLimitManager.RateLimitResult rateLimitResult = rateLimitManager.checkRequest(
                context.getClientId(),
                context.getRateLimitConfig()
        );

        if (!rateLimitResult.isAllowed()) {
            rateLimitRejected.incrementAndGet();
            log.warn("Rate limit exceeded for client: {} - {}", context.getClientId(), rateLimitResult.getReason());
            return RequestProcessorResult.rateLimitExceeded(rateLimitResult.getReason(), rateLimitResult.getRetryAfter());
        }

        return null;
    }

    /**
     * 检查去重
     */
    @SuppressWarnings("unchecked")
    private <T> RequestProcessorResult<T> checkDeduplication(RequestContext context) {
        RequestDeduplicationManager.DeduplicationResult deduplicationResult =
                deduplicationManager.checkAndRecordRequest(context.getRequestKey(), context.getTtlMinutes());

        if (deduplicationResult.isDuplicate()) {
            duplicateRejected.incrementAndGet();
            log.info("Duplicate request detected for key: {}", context.getRequestKey());
            return RequestProcessorResult.duplicate("Duplicate request is being processed");
        }

        if (deduplicationResult.isCacheHit()) {
            cacheHits.incrementAndGet();
            log.info("Cache hit for request key: {}", context.getRequestKey());
            // 尝试从缓存获取结果
            T cachedResult = (T) cacheManager.get(context.getRequestKey(), Object.class);
            if (cachedResult != null) {
                return RequestProcessorResult.cacheHit(cachedResult);
            }
        }

        return null;
    }

    /**
     * 检查缓存
     */
    @SuppressWarnings("unchecked")
    private <T> RequestProcessorResult<T> checkCache(RequestContext context) {
        if (!context.isCacheEnabled()) {
            return null;
        }

        T cachedResult = (T) cacheManager.get(context.getRequestKey(), Object.class);
        if (cachedResult != null) {
            cacheHits.incrementAndGet();
            log.debug("Cache hit for key: {}", context.getRequestKey());
            return RequestProcessorResult.success(cachedResult, "Cache hit");
        }

        return null;
    }

    /**
     * 执行实际请求处理
     */
    private <T> RequestProcessorResult<T> executeRequest(RequestContext context, RequestHandler<T> handler) {
        try {
            log.debug("Executing request handler for: {}", context.getRequestKey());

            T result = handler.handle(context);

            // 缓存结果（如果启用）
            if (context.isCacheEnabled() && result != null) {
                cacheManager.put(context.getRequestKey(), result, context.getCacheTtlMinutes());
            }

            // 标记请求完成
            deduplicationManager.markRequestCompleted(context.getRequestKey(), context.shouldMoveToCache());

            log.debug("Request executed successfully: {}", context.getRequestKey());
            return RequestProcessorResult.success(result, "Request completed successfully");

        } catch (Exception e) {
            log.error("Request handler failed for: {}", context.getRequestKey(), e);
            cleanupRequest(context);
            return RequestProcessorResult.error("Request processing failed: " + e.getMessage());
        }
    }

    /**
     * 清理请求资源
     */
    private void cleanupRequest(RequestContext context) {
        try {
            deduplicationManager.cancelRequest(context.getRequestKey());
        } catch (Exception e) {
            log.warn("Failed to cleanup request: {}", context.getRequestKey(), e);
        }
    }

    /**
     * 获取处理统计信息
     */
    public ProcessorStats getStats() {
        return ProcessorStats.builder()
                .totalProcessed(totalProcessed.get())
                .duplicateRejected(duplicateRejected.get())
                .rateLimitRejected(rateLimitRejected.get())
                .cacheHits(cacheHits.get())
                .deduplicationStats(deduplicationManager.getStats())
                .rateLimitStats(rateLimitManager.getGlobalStats())
                .cacheStats(cacheManager.getStats())
                .build();
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalProcessed.set(0);
        duplicateRejected.set(0);
        rateLimitRejected.set(0);
        cacheHits.set(0);
    }

    /**
     * 请求上下文
     */
    public static class RequestContext {
        private final String clientId;
        private final String requestKey;
        private final String endpoint;
        private final String message;
        private final RequestRateLimitManager.RateLimitConfig rateLimitConfig;
        private final long ttlMinutes;
        private final boolean cacheEnabled;
        private final long cacheTtlMinutes;
        private final boolean moveToCache;

        private RequestContext(Builder builder) {
            this.clientId = builder.clientId;
            this.requestKey = builder.requestKey;
            this.endpoint = builder.endpoint;
            this.message = builder.message;
            this.rateLimitConfig = builder.rateLimitConfig;
            this.ttlMinutes = builder.ttlMinutes;
            this.cacheEnabled = builder.cacheEnabled;
            this.cacheTtlMinutes = builder.cacheTtlMinutes;
            this.moveToCache = builder.moveToCache;
        }

        // Getters
        public String getClientId() { return clientId; }
        public String getRequestKey() { return requestKey; }
        public String getEndpoint() { return endpoint; }
        public String getMessage() { return message; }
        public RequestRateLimitManager.RateLimitConfig getRateLimitConfig() { return rateLimitConfig; }
        public long getTtlMinutes() { return ttlMinutes; }
        public boolean isCacheEnabled() { return cacheEnabled; }
        public long getCacheTtlMinutes() { return cacheTtlMinutes; }
        public boolean shouldMoveToCache() { return moveToCache; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String clientId;
            private String requestKey;
            private String endpoint;
            private String message;
            private RequestRateLimitManager.RateLimitConfig rateLimitConfig = RequestRateLimitManager.RateLimitConfig.defaultConfig();
            private long ttlMinutes = 5;
            private boolean cacheEnabled = true;
            private long cacheTtlMinutes = 10;
            private boolean moveToCache = true;

            public Builder clientId(String clientId) {
                this.clientId = clientId;
                return this;
            }

            public Builder requestKey(String requestKey) {
                this.requestKey = requestKey;
                return this;
            }

            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder rateLimitConfig(RequestRateLimitManager.RateLimitConfig config) {
                this.rateLimitConfig = config;
                return this;
            }

            public Builder ttl(long minutes) {
                this.ttlMinutes = minutes;
                return this;
            }

            public Builder enableCache(boolean enabled) {
                this.cacheEnabled = enabled;
                return this;
            }

            public Builder cacheTtl(long minutes) {
                this.cacheTtlMinutes = minutes;
                return this;
            }

            public Builder moveToCache(boolean moveToCache) {
                this.moveToCache = moveToCache;
                return this;
            }

            public RequestContext build() {
                return new RequestContext(this);
            }
        }
    }

    /**
     * 请求处理器接口
     */
    @FunctionalInterface
    public interface RequestHandler<T> {
        T handle(RequestContext context) throws Exception;
    }

    /**
     * 请求处理结果
     */
    public static class RequestProcessorResult<T> {
        private final ResultType type;
        private final T data;
        private final String message;
        private final java.time.Duration retryAfter;

        private RequestProcessorResult(ResultType type, T data, String message, java.time.Duration retryAfter) {
            this.type = type;
            this.data = data;
            this.message = message;
            this.retryAfter = retryAfter;
        }

        public static <T> RequestProcessorResult<T> success(T data, String message) {
            return new RequestProcessorResult<>(ResultType.SUCCESS, data, message, null);
        }

        public static <T> RequestProcessorResult<T> error(String message) {
            return new RequestProcessorResult<>(ResultType.ERROR, null, message, null);
        }

        public static <T> RequestProcessorResult<T> rateLimitExceeded(String message, java.time.Duration retryAfter) {
            return new RequestProcessorResult<>(ResultType.RATE_LIMIT_EXCEEDED, null, message, retryAfter);
        }

        public static <T> RequestProcessorResult<T> duplicate(String message) {
            return new RequestProcessorResult<>(ResultType.DUPLICATE, null, message, null);
        }

        public static <T> RequestProcessorResult<T> cacheHit(T data) {
            return new RequestProcessorResult<>(ResultType.CACHE_HIT, data, "Cache hit", null);
        }

        // Getters
        public ResultType getType() { return type; }
        public T getData() { return data; }
        public String getMessage() { return message; }
        public java.time.Duration getRetryAfter() { return retryAfter; }

        public boolean isSuccess() { return type == ResultType.SUCCESS; }
        public boolean isError() { return type == ResultType.ERROR; }
        public boolean isRateLimitExceeded() { return type == ResultType.RATE_LIMIT_EXCEEDED; }
        public boolean isDuplicate() { return type == ResultType.DUPLICATE; }
        public boolean isCacheHit() { return type == ResultType.CACHE_HIT; }

        public enum ResultType {
            SUCCESS, ERROR, RATE_LIMIT_EXCEEDED, DUPLICATE, CACHE_HIT
        }
    }

    /**
     * 处理器统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class ProcessorStats {
        private final int totalProcessed;
        private final int duplicateRejected;
        private final int rateLimitRejected;
        private final int cacheHits;
        private final RequestDeduplicationManager.DeduplicationStats deduplicationStats;
        private final RequestRateLimitManager.RateLimitStats rateLimitStats;
        private final CacheManager.CacheStats cacheStats;
    }
}
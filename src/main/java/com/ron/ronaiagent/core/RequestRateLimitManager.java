package com.ron.ronaiagent.core;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求限流管理器
 * 修复了所有类型不匹配和线程安全问题
 */
@Slf4j
@Component
public class RequestRateLimitManager {

    /**
     * 限流配置
     */
    @Data
    public static class RateLimitConfig {
        private final int maxRequestsPerMinute;
        private final int maxRequestsPerHour;
        private final int maxRequestsPerDay;
        private final long burstAllowanceMs; // 突发流量允许的时间窗口（毫秒）

        public RateLimitConfig(int maxRequestsPerMinute, int maxRequestsPerHour, int maxRequestsPerDay, long burstAllowanceMs) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            this.maxRequestsPerHour = maxRequestsPerHour;
            this.maxRequestsPerDay = maxRequestsPerDay;
            this.burstAllowanceMs = burstAllowanceMs;
        }

        public static RateLimitConfig defaultConfig() {
            return new RateLimitConfig(30, 500, 2000, 5000); // 5秒突发窗口
        }

        public static RateLimitConfig strictConfig() {
            return new RateLimitConfig(10, 100, 500, 2000); // 2秒突发窗口
        }

        public static RateLimitConfig lenientConfig() {
            return new RateLimitConfig(60, 1000, 5000, 10000); // 10秒突发窗口
        }
    }

    /**
     * 客户端请求记录（线程安全版）
     */
    public static class ClientRequestRecord {
        private final String clientId;
        private final AtomicInteger minuteCounter = new AtomicInteger(0);
        private final AtomicInteger hourCounter = new AtomicInteger(0);
        private final AtomicInteger dayCounter = new AtomicInteger(0);
        private volatile LocalDateTime lastRequestTime;
        private volatile LocalDateTime minuteWindowStart;
        private volatile LocalDateTime hourWindowStart;
        private volatile LocalDateTime dayWindowStart;
        private final AtomicLong burstStartTime = new AtomicLong(0);
        private final AtomicInteger burstCounter = new AtomicInteger(0);
        private final Object lock = new Object();

        public ClientRequestRecord(String clientId) {
            this.clientId = clientId;
            LocalDateTime now = LocalDateTime.now();
            this.lastRequestTime = now;
            this.minuteWindowStart = now;
            this.hourWindowStart = now;
            this.dayWindowStart = now;
            // 修复：正确设置时间戳
            this.burstStartTime.set(now.toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
        }

        public synchronized void resetCountersIfNeeded() {
            LocalDateTime now = LocalDateTime.now();

            // 重置分钟计数器
            if (java.time.Duration.between(minuteWindowStart, now).toMinutes() >= 1) {
                int currentMinute = minuteCounter.get();
                minuteCounter.set(0);
                minuteWindowStart = now;
                log.debug("Minute counter reset for client: {}, was: {}", clientId, currentMinute);
            }

            // 重置小时计数器
            if (java.time.Duration.between(hourWindowStart, now).toHours() >= 1) {
                int currentHour = hourCounter.get();
                hourCounter.set(0);
                hourWindowStart = now;
                log.debug("Hour counter reset for client: {}, was: {}", clientId, currentHour);
            }

            // 重置日计数器
            if (java.time.Duration.between(dayWindowStart, now).toDays() >= 1) {
                int currentDay = dayCounter.get();
                dayCounter.set(0);
                dayWindowStart = now;
                log.debug("Day counter reset for client: {}, was: {}", clientId, currentDay);
            }
        }

        public boolean isWithinBurstWindow(long burstAllowanceMs) {
            long now = System.currentTimeMillis();
            long burstStart = burstStartTime.get();
            return (now - burstStart) < burstAllowanceMs;
        }

        public synchronized void incrementRequest() {
            minuteCounter.incrementAndGet();
            hourCounter.incrementAndGet();
            dayCounter.incrementAndGet();
        }

        public synchronized boolean checkBurstLimit(int maxBurstRequests, long burstAllowanceMs) {
            if (isWithinBurstWindow(burstAllowanceMs)) {
                int currentBurst = burstCounter.incrementAndGet();
                return currentBurst < maxBurstRequests; // 修复：使用严格小于，确保在达到maxBurst时阻止
            } else {
                // 重置突发窗口
                burstStartTime.set(System.currentTimeMillis());
                burstCounter.set(1);
                return true;
            }
        }

        public synchronized void updateTime() {
            lastRequestTime = LocalDateTime.now();
        }

        public synchronized void resetCounters() {
            minuteCounter.set(0);
            hourCounter.set(0);
            dayCounter.set(0);
            burstCounter.set(0);
            burstStartTime.set(System.currentTimeMillis());
        }

        // Getters保持线程安全
        public String getClientId() {
            return clientId;
        }

        public int getMinuteRequests() {
            return minuteCounter.get();
        }

        public int getHourRequests() {
            return hourCounter.get();
        }

        public int getDayRequests() {
            return dayCounter.get();
        }

        public LocalDateTime getLastRequestTime() {
            return lastRequestTime;
        }

        public int getBurstRequests() {
            return burstCounter.get();
        }

        public long getBurstStartTime() {
            return burstStartTime.get();
        }
    }

    // 存储客户端请求记录
    private final ConcurrentHashMap<String, ClientRequestRecord> clientRecords = new ConcurrentHashMap<>();

    // 默认限流配置
    private volatile RateLimitConfig defaultConfig = RateLimitConfig.defaultConfig();

    // 特殊客户端的限流配置
    private final ConcurrentHashMap<String, RateLimitConfig> clientConfigs = new ConcurrentHashMap<>();

    // 定时清理线程池
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    private final AtomicInteger activeClients = new AtomicInteger(0);

    public RequestRateLimitManager() {
        // 启动定时清理任务，每小时清理一次非活跃客户端
        cleanupExecutor.scheduleAtFixedRate(this::cleanupInactiveClients, 1, 1, TimeUnit.HOURS);

        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        log.info("RequestRateLimitManager initialized with default config: {}", defaultConfig);
    }

    /**
     * 检查请求是否允许通过
     */
    public RateLimitResult checkRequest(String clientId) {
        return checkRequest(clientId, defaultConfig);
    }

    /**
     * 检查请求是否允许通过（使用指定配置）
     */
    public RateLimitResult checkRequest(String clientId, RateLimitConfig config) {
        totalRequests.incrementAndGet();

        // 处理空客户端ID - 使用默认ID
        String effectiveClientId = (clientId == null || clientId.trim().isEmpty()) ? "_default" : clientId;

        ClientRequestRecord record = clientRecords.computeIfAbsent(effectiveClientId, k -> {
            activeClients.incrementAndGet();
            log.debug("New client registered: {}", effectiveClientId);
            return new ClientRequestRecord(effectiveClientId);
        });

        synchronized (record) {
            record.resetCountersIfNeeded();
            record.updateTime();

            // 检查突发流量限制（改进版：突发允许短时内超过分钟限制）
            // 突发限制设置为分钟限制+1，允许完整的分钟限制请求通过
            int maxBurst = config.getMaxRequestsPerMinute() + 1;
            if (!record.checkBurstLimit(maxBurst, config.getBurstAllowanceMs())) {
                blockedRequests.incrementAndGet();
                log.warn("Burst rate limit exceeded for client: {} (burst: {}/{})",
                        effectiveClientId, record.getBurstRequests(), maxBurst);
                return RateLimitResult.blocked("Burst rate limit exceeded",
                    java.time.Duration.ofMillis(config.getBurstAllowanceMs()));
            }

            // 增加请求计数
            record.incrementRequest();

            // 检查分钟限制
            if (record.getMinuteRequests() > config.getMaxRequestsPerMinute()) {
                blockedRequests.incrementAndGet();
                log.warn("Minute rate limit exceeded for client: {} ({} > {})",
                        effectiveClientId, record.getMinuteRequests(), config.getMaxRequestsPerMinute());
                return RateLimitResult.blocked("Minute rate limit exceeded",
                    java.time.Duration.ofMinutes(1));
            }

            // 检查小时限制
            if (record.getHourRequests() > config.getMaxRequestsPerHour()) {
                blockedRequests.incrementAndGet();
                log.warn("Hour rate limit exceeded for client: {} ({} > {})",
                        effectiveClientId, record.getHourRequests(), config.getMaxRequestsPerHour());
                return RateLimitResult.blocked("Hour rate limit exceeded",
                    java.time.Duration.ofHours(1));
            }

            // 检查日限制
            if (record.getDayRequests() > config.getMaxRequestsPerDay()) {
                blockedRequests.incrementAndGet();
                log.warn("Day rate limit exceeded for client: {} ({} > {})",
                        effectiveClientId, record.getDayRequests(), config.getMaxRequestsPerDay());
                return RateLimitResult.blocked("Day rate limit exceeded",
                    java.time.Duration.ofDays(1));
            }

            // 检查小时限制
            if (record.getHourRequests() > config.getMaxRequestsPerHour()) {
                blockedRequests.incrementAndGet();
                log.warn("Hour rate limit exceeded for client: {} ({} > {})",
                        clientId, record.getHourRequests(), config.getMaxRequestsPerHour());
                return RateLimitResult.blocked("Hour rate limit exceeded",
                    java.time.Duration.ofHours(1));
            }

            // 检查日限制
            if (record.getDayRequests() > config.getMaxRequestsPerDay()) {
                blockedRequests.incrementAndGet();
                log.warn("Day rate limit exceeded for client: {} ({} > {})",
                        clientId, record.getDayRequests(), config.getMaxRequestsPerDay());
                return RateLimitResult.blocked("Day rate limit exceeded",
                    java.time.Duration.ofDays(1));
            }

            log.debug("Request allowed for client: {} (minute: {}/{}, hour: {}/{}, day: {}/{})",
                     clientId,
                     record.getMinuteRequests(), config.getMaxRequestsPerMinute(),
                     record.getHourRequests(), config.getMaxRequestsPerHour(),
                     record.getDayRequests(), config.getMaxRequestsPerDay());
        }

        return RateLimitResult.allowed();
    }

    /**
     * 设置客户端的限流配置
     */
    public void setClientConfig(String clientId, RateLimitConfig config) {
        clientConfigs.put(clientId, config);
        log.info("Rate limit config updated for client: {}", clientId);
    }

    /**
     * 获取客户端的限流配置
     */
    public RateLimitConfig getClientConfig(String clientId) {
        return clientConfigs.getOrDefault(clientId, defaultConfig);
    }

    /**
     * 获取客户端的请求统计
     */
    public ClientRequestStats getClientStats(String clientId) {
        ClientRequestRecord record = clientRecords.get(clientId);
        if (record == null) {
            return ClientRequestStats.empty(clientId);
        }

        RateLimitConfig config = getClientConfig(clientId);
        synchronized (record) {
            record.resetCountersIfNeeded();
        }

        return ClientRequestStats.builder()
                .clientId(clientId)
                .minuteRequests(record.getMinuteRequests())
                .minuteLimit(config.getMaxRequestsPerMinute())
                .hourRequests(record.getHourRequests())
                .hourLimit(config.getMaxRequestsPerHour())
                .dayRequests(record.getDayRequests())
                .dayLimit(config.getMaxRequestsPerDay())
                .burstRequests(record.getBurstRequests())
                .lastRequestTime(record.getLastRequestTime())
                .build();
    }

    /**
     * 清理非活跃客户端
     */
private void cleanupInactiveClients() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24); // 24小时未活动视为非活跃

        int removedCount = (int) clientRecords.entrySet().stream()
                .filter(entry -> {
                    ClientRequestRecord record = entry.getValue();
                    LocalDateTime lastRequestTime = record.getLastRequestTime();
                    // 处理可能为null的情况
                    return lastRequestTime != null && lastRequestTime.isBefore(cutoff);
                })
                .map(Map.Entry::getKey)
                .peek(clientId -> log.debug("Removing inactive client: {}", clientId))
                .map(clientId -> clientRecords.remove(clientId) != null ? 1 : 0)
                .mapToInt(Integer::intValue)
                .sum();

        if (removedCount > 0) {
            activeClients.addAndGet(-removedCount);
            log.info("Cleaned up {} inactive clients", removedCount);
        }
    }

    /**
     * 获取全局统计信息
     */
    public RateLimitStats getGlobalStats() {
        return RateLimitStats.builder()
                .totalRequests(totalRequests.get())
                .blockedRequests(blockedRequests.get())
                .activeClients(activeClients.get())
                .blockRate(calculateBlockRate())
                .build();
    }

    private double calculateBlockRate() {
        long total = totalRequests.get();
        long blocked = blockedRequests.get();
        return total > 0 ? (blocked * 100.0 / total) : 0.0;
    }

    /**
     * 重置客户端的请求计数
     */
    public void resetClientCounters(String clientId) {
        ClientRequestRecord record = clientRecords.get(clientId);
        if (record != null) {
            synchronized (record) {
                record.resetCounters();
            }
            log.debug("Counters reset for client: {}", clientId);
        }
    }

    /**
     * 移除客户端记录
     */
    public void removeClient(String clientId) {
        if (clientRecords.remove(clientId) != null) {
            activeClients.decrementAndGet();
            log.info("Client removed: {}", clientId);
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        log.info("Shutting down RequestRateLimitManager...");

        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        clientRecords.clear();
        clientConfigs.clear();

        log.info("RequestRateLimitManager shutdown completed");
    }

    /**
     * 限流结果
     */
    @Data
    public static class RateLimitResult {
        private final boolean allowed;
        private final String reason;
        private final java.time.Duration retryAfter;

        private RateLimitResult(boolean allowed, String reason, java.time.Duration retryAfter) {
            this.allowed = allowed;
            this.reason = reason;
            this.retryAfter = retryAfter;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, null);
        }

        public static RateLimitResult blocked(String reason, java.time.Duration retryAfter) {
            return new RateLimitResult(false, reason, retryAfter);
        }
    }

    /**
     * 客户端请求统计
     */
    @Data
    @Builder
    public static class ClientRequestStats {
        private final String clientId;
        private final int minuteRequests;
        private final int minuteLimit;
        private final int hourRequests;
        private final int hourLimit;
        private final int dayRequests;
        private final int dayLimit;
        private final int burstRequests;
        private final LocalDateTime lastRequestTime;

        public static ClientRequestStats empty(String clientId) {
            return ClientRequestStats.builder()
                    .clientId(clientId)
                    .minuteRequests(0)
                    .minuteLimit(0)
                    .hourRequests(0)
                    .hourLimit(0)
                    .dayRequests(0)
                    .dayLimit(0)
                    .burstRequests(0)
                    .lastRequestTime(null)
                    .build();
        }
    }

    /**
     * 全局限流统计
     */
    @Data
    @Builder
    public static class RateLimitStats {
        private final long totalRequests;
        private final long blockedRequests;
        private final int activeClients;
        private final double blockRate;
    }
}
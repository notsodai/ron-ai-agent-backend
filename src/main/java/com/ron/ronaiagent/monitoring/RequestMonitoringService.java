package com.ron.ronaiagent.monitoring;

import com.ron.ronaiagent.core.CacheManager;
import com.ron.ronaiagent.core.RequestDeduplicationManager;
import com.ron.ronaiagent.core.RequestRateLimitManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求监控服务
 * 提供系统性能监控和统计功能
 */
@Slf4j
@Service
public class RequestMonitoringService {

    @Autowired
    private RequestDeduplicationManager deduplicationManager;

    @Autowired
    private RequestRateLimitManager rateLimitManager;

    @Autowired
    private CacheManager cacheManager;

    // 性能指标
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    // 错误统计
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> endpointCounts = new ConcurrentHashMap<>();

    /**
     * 记录请求开始
     */
    public RequestTimer startRequest(String endpoint) {
        totalRequests.incrementAndGet();
        endpointCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();

        log.debug("Request started for endpoint: {}", endpoint);
        return new RequestTimer(endpoint, System.currentTimeMillis());
    }

    /**
     * 记录请求成功
     */
    public void recordSuccess(RequestTimer timer) {
        long duration = System.currentTimeMillis() - timer.getStartTime();
        totalResponseTime.addAndGet(duration);
        successfulRequests.incrementAndGet();

        log.debug("Request completed successfully for endpoint: {}, duration: {}ms",
                timer.getEndpoint(), duration);

        // 记录性能告警
        if (duration > 30000) { // 超过30秒
            log.warn("Slow request detected for endpoint: {}, duration: {}ms",
                    timer.getEndpoint(), duration);
        }
    }

    /**
     * 记录请求失败
     */
    public void recordFailure(RequestTimer timer, String errorType) {
        long duration = System.currentTimeMillis() - timer.getStartTime();
        totalResponseTime.addAndGet(duration);
        failedRequests.incrementAndGet();

        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();

        log.warn("Request failed for endpoint: {}, duration: {}ms, error: {}",
                timer.getEndpoint(), duration, errorType);
    }

    /**
     * 获取系统监控数据
     */
    public MonitoringData getMonitoringData() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long totalRespTime = totalResponseTime.get();

        double averageResponseTime = total > 0 ? (double) totalRespTime / total : 0.0;
        double successRate = total > 0 ? (successful * 100.0 / total) : 0.0;
        double errorRate = total > 0 ? (failed * 100.0 / total) : 0.0;

        return MonitoringData.builder()
                .timestamp(LocalDateTime.now())
                .totalRequests(total)
                .successfulRequests(successful)
                .failedRequests(failed)
                .averageResponseTime(averageResponseTime)
                .successRate(successRate)
                .errorRate(errorRate)
                .deduplicationStats(deduplicationManager.getStats())
                .rateLimitStats(rateLimitManager.getGlobalStats())
                .cacheStats(cacheManager.getStats())
                .errorCounts(new ConcurrentHashMap<>(errorCounts))
                .endpointCounts(new ConcurrentHashMap<>(endpointCounts))
                .build();
    }

    /**
     * 定时记录监控数据
     */
    @Scheduled(fixedRate = 60000) // 每分钟记录一次
    public void logMonitoringData() {
        try {
            MonitoringData data = getMonitoringData();

            log.info("=== System Monitoring Data ===");
            log.info("Total Requests: {}, Success Rate: {:.2f}%, Avg Response Time: {:.2f}ms",
                    data.getTotalRequests(), data.getSuccessRate(), data.getAverageResponseTime());
            log.info("Deduplication Rate: {:.2f}%, Rate Limit Block Rate: {:.2f}%, Cache Hit Rate: {:.2f}%",
                    data.getDeduplicationStats().getDeduplicationRate(),
                    data.getRateLimitStats().getBlockRate(),
                    data.getCacheStats().getHitRate());

            // 记录热门端点
            data.getEndpointCounts().entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                    .limit(5)
                    .forEach(entry -> log.info("Endpoint {}: {} requests",
                            entry.getKey(), entry.getValue().get()));

            // 记录错误类型
            if (!data.getErrorCounts().isEmpty()) {
                log.warn("Error Summary:");
                data.getErrorCounts().forEach((errorType, count) ->
                        log.warn("  {}: {} occurrences", errorType, count.get()));
            }

        } catch (Exception e) {
            log.error("Error logging monitoring data", e);
        }
    }

    /**
     * 清理旧的统计数据
     */
    @Scheduled(cron = "0 0 * * * *") // 每小时执行一次
    public void cleanupStats() {
        log.info("Cleaning up monitoring statistics...");

        // 保留错误统计但可以重置计数器
        errorCounts.clear();
        endpointCounts.clear();

        // 重置性能计数器（可选）
        // totalRequests.set(0);
        // successfulRequests.set(0);
        // failedRequests.set(0);
        // totalResponseTime.set(0);

        log.info("Monitoring statistics cleanup completed");
    }

    /**
     * 请求计时器
     */
    public static class RequestTimer {
        private final String endpoint;
        private final long startTime;

        public RequestTimer(String endpoint, long startTime) {
            this.endpoint = endpoint;
            this.startTime = startTime;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }

    /**
     * 监控数据
     */
    @lombok.Data
    @lombok.Builder
    public static class MonitoringData {
        private final LocalDateTime timestamp;
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final double averageResponseTime;
        private final double successRate;
        private final double errorRate;
        private final RequestDeduplicationManager.DeduplicationStats deduplicationStats;
        private final RequestRateLimitManager.RateLimitStats rateLimitStats;
        private final CacheManager.CacheStats cacheStats;
        private final ConcurrentHashMap<String, AtomicLong> errorCounts;
        private final ConcurrentHashMap<String, AtomicLong> endpointCounts;
    }
}
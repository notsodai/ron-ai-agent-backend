package com.ron.ronaiagent.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 性能分析器
 * 监控系统资源使用情况和性能指标
 */
@Slf4j
@Service
public class PerformanceAnalyzer {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * 获取系统性能数据
     */
    public SystemPerformanceData getSystemPerformance() {
        try {
            // 内存使用情况
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
            long nonHeapMax = memoryBean.getNonHeapMemoryUsage().getMax();

            // CPU和系统负载
            double systemLoadAverage = osBean.getSystemLoadAverage();
            int availableProcessors = osBean.getAvailableProcessors();

            // JVM信息
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long maxMemory = runtime.maxMemory();

            return SystemPerformanceData.builder()
                    .timestamp(LocalDateTime.now())
                    .heapUsed(heapUsed)
                    .heapMax(heapMax)
                    .heapUsagePercent(calculateUsagePercent(heapUsed, heapMax))
                    .nonHeapUsed(nonHeapUsed)
                    .nonHeapMax(nonHeapMax)
                    .nonHeapUsagePercent(calculateUsagePercent(nonHeapUsed, nonHeapMax))
                    .totalMemory(totalMemory)
                    .freeMemory(freeMemory)
                    .usedMemory(totalMemory - freeMemory)
                    .maxMemory(maxMemory)
                    .systemLoadAverage(systemLoadAverage)
                    .availableProcessors(availableProcessors)
                    .cpuUsagePercent(calculateCpuUsagePercent(systemLoadAverage, availableProcessors))
                    .build();

        } catch (Exception e) {
            log.error("Error collecting system performance data", e);
            return SystemPerformanceData.empty();
        }
    }

    /**
     * 分析系统健康状况
     */
    public HealthAnalysis analyzeSystemHealth(SystemPerformanceData data) {
        HealthStatus overallStatus = HealthStatus.HEALTHY;
        Map<String, String> warnings = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        // 检查内存使用
        if (data.getHeapUsagePercent() > 90) {
            overallStatus = HealthStatus.CRITICAL;
            errors.put("memory", "Heap memory usage is critically high: " + String.format("%.1f%%", data.getHeapUsagePercent()));
        } else if (data.getHeapUsagePercent() > 80) {
            if (overallStatus == HealthStatus.HEALTHY) overallStatus = HealthStatus.WARNING;
            warnings.put("memory", "Heap memory usage is high: " + String.format("%.1f%%", data.getHeapUsagePercent()));
        }

        // 检查非堆内存
        if (data.getNonHeapUsagePercent() > 90) {
            if (overallStatus == HealthStatus.HEALTHY) overallStatus = HealthStatus.WARNING;
            warnings.put("nonHeapMemory", "Non-heap memory usage is high: " + String.format("%.1f%%", data.getNonHeapUsagePercent()));
        }

        // 检查CPU负载
        if (data.getCpuUsagePercent() > 90) {
            if (overallStatus == HealthStatus.HEALTHY) overallStatus = HealthStatus.WARNING;
            warnings.put("cpu", "High CPU usage: " + String.format("%.1f%%", data.getCpuUsagePercent()));
        }

        // 检查系统负载
        if (data.getSystemLoadAverage() > 0 && data.getSystemLoadAverage() > data.getAvailableProcessors() * 2.0) {
            if (overallStatus == HealthStatus.HEALTHY) overallStatus = HealthStatus.WARNING;
            warnings.put("systemLoad", "High system load average: " + String.format("%.2f", data.getSystemLoadAverage()));
        }

        return HealthAnalysis.builder()
                .timestamp(LocalDateTime.now())
                .overallStatus(overallStatus)
                .warnings(warnings)
                .errors(errors)
                .recommendations(generateRecommendations(warnings, errors))
                .build();
    }

    /**
     * 生成优化建议
     */
    private Map<String, String> generateRecommendations(Map<String, String> warnings, Map<String, String> errors) {
        Map<String, String> recommendations = new HashMap<>();

        if (errors.containsKey("memory")) {
            recommendations.put("memory", "Consider increasing heap size or optimizing memory usage. Restart the application if necessary.");
        }

        if (warnings.containsKey("memory")) {
            recommendations.put("memory", "Monitor memory usage and consider garbage collection tuning.");
        }

        if (warnings.containsKey("cpu")) {
            recommendations.put("cpu", "Monitor CPU usage and consider optimizing algorithms or scaling horizontally.");
        }

        if (warnings.containsKey("systemLoad")) {
            recommendations.put("systemLoad", "Monitor system load and consider load balancing strategies.");
        }

        if (recommendations.isEmpty()) {
            recommendations.put("general", "System is running normally. Continue monitoring performance metrics.");
        }

        return recommendations;
    }

    /**
     * 计算使用百分比
     */
    private double calculateUsagePercent(long used, long max) {
        if (max <= 0) return 0.0;
        return (used * 100.0) / max;
    }

    /**
     * 计算CPU使用百分比
     */
    private double calculateCpuUsagePercent(double loadAverage, int processors) {
        if (loadAverage < 0 || processors <= 0) return 0.0;
        return Math.min(100.0, (loadAverage / processors) * 100.0);
    }

    /**
     * 记录性能数据到日志
     */
    public void logPerformanceData() {
        try {
            SystemPerformanceData data = getSystemPerformance();
            HealthAnalysis analysis = analyzeSystemHealth(data);

            log.info("=== System Performance Data ===");
            log.info("Memory - Heap: {}/{} MB ({}%), Non-Heap: {}/{} MB ({})",
                    formatBytes(data.getHeapUsed()), formatBytes(data.getHeapMax()),
                    String.format("%.1f", data.getHeapUsagePercent()),
                    formatBytes(data.getNonHeapUsed()), formatBytes(data.getNonHeapMax()),
                    String.format("%.1f", data.getNonHeapUsagePercent()));
            log.info("CPU - Load Average: {}, Usage: {}%, Processors: {}",
                    String.format("%.2f", data.getSystemLoadAverage()),
                    String.format("%.1f", data.getCpuUsagePercent()),
                    data.getAvailableProcessors());
            log.info("Overall Health Status: {}", analysis.getOverallStatus());

            // 记录警告和错误
            analysis.getWarnings().forEach((key, message) ->
                    log.warn("Performance Warning [{}]: {}", key, message));
            analysis.getErrors().forEach((key, message) ->
                    log.error("Performance Error [{}]: {}", key, message));

        } catch (Exception e) {
            log.error("Error logging performance data", e);
        }
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 系统性能数据
     */
    @lombok.Data
    @lombok.Builder
    public static class SystemPerformanceData {
        private final LocalDateTime timestamp;
        private final long heapUsed;
        private final long heapMax;
        private final double heapUsagePercent;
        private final long nonHeapUsed;
        private final long nonHeapMax;
        private final double nonHeapUsagePercent;
        private final long totalMemory;
        private final long freeMemory;
        private final long usedMemory;
        private final long maxMemory;
        private final double systemLoadAverage;
        private final int availableProcessors;
        private final double cpuUsagePercent;

        public static SystemPerformanceData empty() {
            return SystemPerformanceData.builder()
                    .timestamp(LocalDateTime.now())
                    .heapUsed(0)
                    .heapMax(0)
                    .heapUsagePercent(0.0)
                    .nonHeapUsed(0)
                    .nonHeapMax(0)
                    .nonHeapUsagePercent(0.0)
                    .totalMemory(0)
                    .freeMemory(0)
                    .usedMemory(0)
                    .maxMemory(0)
                    .systemLoadAverage(0.0)
                    .availableProcessors(0)
                    .cpuUsagePercent(0.0)
                    .build();
        }
    }

    /**
     * 健康分析结果
     */
    @lombok.Data
    @lombok.Builder
    public static class HealthAnalysis {
        private final LocalDateTime timestamp;
        private final HealthStatus overallStatus;
        private final Map<String, String> warnings;
        private final Map<String, String> errors;
        private final Map<String, String> recommendations;
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY, WARNING, CRITICAL
    }
}
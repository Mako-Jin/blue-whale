package com.bluewhale.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定时任务服务配置项（前缀 {@code whale.job}）。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.job")
public class JobProperties {

    /** 执行日志保留天数：早于该天数的 {@code job_log} 将被逻辑删除清理 */
    private int logRetentionDays = 30;

    /** whale-file 服务内网地址：归档任务经此直连上传导出文件（不经网关） */
    private String fileServiceUrl = "http://127.0.0.1:8085";

    /** 单次归档导出并清理的 {@code job_log} 上限条数，防止单批过大 */
    private int archiveBatchSize = 2000;

    /** 失败告警配置 */
    private Alert alert = new Alert();

    /** 执行器注册配置 */
    private Executor executor = new Executor();

    /**
     * 启动对账阈值（分钟）：{@code job_log} 中 {@code status=0} 且开始时间早于该阈值的记录，
     * 视为实例退出导致的「永远执行中」，启动时回写为失败。取值必须大于最长合法执行时长，
     * 配置为 0 表示关闭该对账。
     */
    private int orphanLogTimeoutMinutes = 120;

    /**
     * 执行器注册配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Executor {

        /**
         * 调度线程池大小：cron 触发、手动触发与依赖链提交共用同一线程池。
         *
         * <p>HTTP 任务单次执行最坏占用一个线程 {@code httpTimeoutMillis × (1 + maxRetry)}，
         * 线程过少会因个别慢任务导致后续触发排队，默认提高到 8 并允许按实例规格调整。</p>
         */
        private int poolSize = 8;

        /** 心跳超时秒数：超过该时长未心跳的实例视为离线，调度时不再寻址到它 */
        private int heartbeatTimeoutSeconds = 90;

        /**
         * 过期行清理倍数：心跳超过 {@code heartbeatTimeoutSeconds × 该倍数} 的执行器行
         * 会被物理删除，避免容器重建留下的旧地址长期堆积在注册表里。
         */
        private int staleMultiplier = 10;

        /**
         * HTTP 执行器默认读取超时毫秒数：任务参数未指定时使用。
         *
         * <p>默认 15s，避免慢任务在高并发下占满调度线程池；确需长时间执行的任务
         * 在 {@code handlerParam.timeoutMillis} 中按任务覆盖即可。</p>
         */
        private int httpTimeoutMillis = 15000;

        /** HTTP 执行器连接超时毫秒数 */
        private int httpConnectTimeoutMillis = 5000;

        /**
         * 任务级超时的执行线程池大小。
         *
         * <p>只有配置了超时的任务才会用到该线程池：调度线程 {@code Future.get} 等待，
         * 实际执行在独立线程池中进行，避免超时等待与执行互相占用线程造成死锁。</p>
         */
        private int taskPoolSize = 8;

        /**
         * 全局默认任务超时秒数：任务未配置 {@code timeout_seconds} 时使用。
         *
         * <p>默认 0 表示不限制，保持既有任务行为不变；需要兜底时统一打开即可。</p>
         */
        private int taskTimeoutSeconds = 0;
    }

    /**
     * 失败告警配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Alert {

        /** 是否启用失败告警外发：关闭时仅记录本地告警日志与 {@code job_alert} 流水 */
        private boolean enabled = true;

        /** 告警 Webhook 地址：为空时仅落本地日志，不外发 */
        private String webhookUrl = "";

        /** 同一任务的告警外发冷却秒数：冷却期内只落库不外发 */
        private int cooldownSeconds = 300;

        /** Webhook 连接超时毫秒数 */
        private int connectTimeoutMillis = 2000;

        /** Webhook 读取超时毫秒数 */
        private int readTimeoutMillis = 3000;

        /** 告警记录中错误信息最大长度 */
        private int maxErrorLength = 500;
    }
}

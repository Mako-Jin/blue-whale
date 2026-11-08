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
     * 执行器注册配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Executor {

        /** 心跳超时秒数：超过该时长未心跳的实例视为离线，调度时不再寻址到它 */
        private int heartbeatTimeoutSeconds = 90;

        /**
         * 过期行清理倍数：心跳超过 {@code heartbeatTimeoutSeconds × 该倍数} 的执行器行
         * 会被物理删除，避免容器重建留下的旧地址长期堆积在注册表里。
         */
        private int staleMultiplier = 10;

        /** HTTP 执行器默认读取超时毫秒数：任务参数未指定时使用 */
        private int httpTimeoutMillis = 60000;

        /** HTTP 执行器连接超时毫秒数 */
        private int httpConnectTimeoutMillis = 5000;
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

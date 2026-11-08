package com.bluewhale.common.jobschedule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 受管定时任务配置（前缀 {@code whale.schedule}）。
 *
 * <p>默认值的设计原则是**对既有服务零影响**：进程内调度保持开启、内网触发端点与执行器注册保持关闭，
 * 只有显式迁移的服务才打开。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.schedule")
public class JobScheduleProperties {

    /** 是否注册进程内 cron 调度：默认开启，等价于原来的 {@code @Scheduled} */
    private boolean localEnabled = true;

    /** 是否暴露内网触发端点 {@code POST /internal/job/{code}/run}：默认关闭 */
    private boolean triggerEnabled = false;

    /** 执行器注册配置 */
    private Executor executor = new Executor();

    /**
     * 执行器注册配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Executor {

        /** 是否向调度中心注册本实例与支持的任务编码：默认关闭 */
        private boolean registerEnabled = false;

        /** 执行器应用名：为空时取 {@code spring.application.name} */
        private String appName = "";

        /** 注册使用的租户ID：定时任务没有用户上下文，需显式指定 */
        private long tenantId = 1L;

        /** 实例地址 host:port：为空时自动探测（容器内为容器 IP + 服务端口） */
        private String address = "";

        /** 调度中心内网地址 */
        private String serverUrl = "http://127.0.0.1:8082";

        /** 心跳间隔毫秒数 */
        private long heartbeatIntervalMs = 30000L;

        /** 内网调用超时毫秒数 */
        private int timeoutMillis = 3000;
    }
}

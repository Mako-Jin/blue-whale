package com.bluewhale.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 档案服务配置项。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.archive")
public class ArchiveProperties {

    /** 归档快照与锁定文件所在的对象存储桶 */
    private String bucket = "whale-archive";

    /** 档号配置 */
    private Number number = new Number();

    /** 工作流增量同步配置（M2 启用） */
    private Sync sync = new Sync();

    /** 历史归档文件补锁配置（F-01 自动补扫） */
    private LockReconcile lockReconcile = new LockReconcile();

    /** 内网服务地址与超时配置 */
    private Integration integration = new Integration();

    /**
     * 档号配置：前缀 + 流水号宽度。
     *
     * @author BlueWhale
     */
    @Data
    public static class Number {

        /** 档号前缀，默认 {@code BW} */
        private String prefix = "BW";

        /** 流水号宽度（左补零），默认 6 */
        private int sequenceWidth = 6;
    }

    /**
     * 工作流已通过实例同步配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Sync {

        /** 是否启用定时同步（M2 接入调度器后生效） */
        private boolean enabled = false;

        /** 同步调度表达式，默认每 5 分钟 */
        private String cron = "0 */5 * * * *";

        /** 单页拉取条数 */
        private int pageSize = 100;

        /** 单条来源失败最大重试次数（达到后不再自动重试） */
        private int maxAttempts = 3;

        /** 每轮重试扫描的失败台账条数上限 */
        private int retryBatchSize = 50;

        /**
         * 显式指定定时同步遍历的租户ID列表。
         *
         * <p>为空时从租户目录（whale-service-auth 内网接口）动态查询「启用且未过期」的租户；
         * 目录不可用时回退默认租户 1，保证调度不中断。</p>
         */
        private List<Long> tenantIds = List.of();
    }

    /**
     * 历史归档文件锁定自动补扫配置（F-01）。
     *
     * <p>上线前已归档的条目原件可能未锁定，本调度按租户、按条目游标分批补锁；
     * 默认关闭，部署时通过 {@code whale.archive.lock-reconcile.enabled=true} 开启。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class LockReconcile {

        /** 是否启用定时补扫 */
        private boolean enabled = false;

        /** 补扫调度表达式，默认每日 03:30 */
        private String cron = "0 30 3 * * *";

        /** 单批条目数（1..500） */
        private int batchSize = 200;

        /** 单租户单轮最大批次数，避免历史数据过多导致单轮长时间占用 */
        private int maxBatches = 10;

        /**
         * 显式指定定时补扫遍历的租户ID列表。
         *
         * <p>为空时复用 {@link Sync#getTenantIds()} 的租户目录解析逻辑。</p>
         */
        private List<Long> tenantIds = List.of();
    }

    /**
     * 内网服务地址与超时配置：档案服务以 RestClient 直连各服务内网接口，不经网关。
     *
     * @author BlueWhale
     */
    @Data
    public static class Integration {

        /** 工作流服务基址 */
        private String workflowUrl = "http://127.0.0.1:8083";

        /** 表单服务基址 */
        private String formUrl = "http://127.0.0.1:8084";

        /** 文件服务基址 */
        private String fileUrl = "http://127.0.0.1:8085";

        /** 认证服务基址（查询可用租户目录） */
        private String authUrl = "http://127.0.0.1:8081";

        /** 建立连接超时（毫秒） */
        private int connectTimeoutMillis = 5000;

        /** 读取响应超时（毫秒） */
        private int readTimeoutMillis = 60000;
    }
}

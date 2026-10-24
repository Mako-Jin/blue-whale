package com.bluewhale.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    }
}

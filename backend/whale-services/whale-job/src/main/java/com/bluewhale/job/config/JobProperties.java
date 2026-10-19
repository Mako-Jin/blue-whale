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
}

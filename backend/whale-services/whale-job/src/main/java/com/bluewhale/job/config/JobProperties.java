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
}

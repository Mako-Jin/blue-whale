package com.bluewhale.message.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息数据归档配置。
 *
 * <p>归档只导出、不删除 {@code msg_record} / {@code msg_delivery}：按租户水位增量查询
 * 早于保留期的消息，生成 NDJSON 并上传 whale-file 内网接口，供离线留存。
 * 默认关闭，避免未配置对象存储环境时产生无效调用。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whale.message.archive")
public class MessageArchiveProperties {

    /** 是否启用归档 */
    private boolean enabled;

    /** 保留天数：早于该天数的消息进入归档 */
    private int retentionDays = 180;

    /** 归档 cron */
    private String cron = "0 30 3 * * ?";

    /** 单批导出消息条数 */
    private int batchRows = 500;

    /** 单次任务每个租户最多导出的消息条数 */
    private int maxRows = 5000;

    /** whale-file 内网基地址 */
    private String fileServiceUrl = "http://127.0.0.1:8085";

    /** 归档目标桶 */
    private String bucket = "bluewhale-message-archive";
}

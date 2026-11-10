package com.bluewhale.pdm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PDM 集成同步失败告警配置。
 *
 * <p>接收人为空时跳过告警，只保留同步日志；告警不阻塞同步主流程。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.pdm.alert")
public class PdmIntAlertProperties {

    /** 接收人用户 ID 列表（默认空） */
    private List<Long> userIds = new ArrayList<>();

    /** 消息渠道（默认站内信 INBOX） */
    private String channel = "INBOX";
}

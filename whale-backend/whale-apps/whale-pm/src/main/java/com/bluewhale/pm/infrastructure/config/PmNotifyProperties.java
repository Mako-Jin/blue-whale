package com.bluewhale.pm.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 站内通知配置（可选能力，默认关闭）。
 *
 * <p>开启后，工作项指派 / 评论 / 状态流转会经 whale-im 内网接口投递到接收人的机器人会话；
 * 投递失败只记录告警，不影响主事务。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.pm.notify")
public class PmNotifyProperties {

    /** 是否启用站内通知 */
    private boolean enabled = false;

    /** whale-im 内网服务地址（直连，不经网关） */
    private String imUrl = "http://127.0.0.1:8091";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 2000;
}

package com.bluewhale.common.log.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志落库 sink 配置（REQ-SEC-006）。
 *
 * <p>{@code whale.audit.sink=local|mq}：仅实现 {@code local}（本地异步直写）；
 * 历史取值 {@code mq} 已废弃，配置后自动回落 {@code local} 并告警；
 * MQ 汇聚使用 {@code whale.audit.mq.publish-enabled}，
 * HTTP 转发使用 {@code whale.audit.forward.enabled}。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.audit")
public class LogSinkProperties {

    /** 本地直写 sink 取值 */
    public static final String SINK_LOCAL = "local";
    /** MQ 汇聚 sink 历史取值（已废弃，仅用于识别并告警） */
    public static final String SINK_MQ = "mq";

    /** 落库 sink：local（默认）/ mq */
    private String sink = SINK_LOCAL;
}

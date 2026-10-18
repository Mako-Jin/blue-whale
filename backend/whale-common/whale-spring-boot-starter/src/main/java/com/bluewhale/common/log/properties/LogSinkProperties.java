package com.bluewhale.common.log.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志落库 sink 配置（REQ-SEC-006）。
 *
 * <p>{@code whale.audit.sink=local|mq}：阶段一只实现 {@code local}（本地异步直写）；
 * {@code mq} 为阶段二预留，未就绪时自动回落 {@code local} 并告警。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.audit")
public class LogSinkProperties {

    /** 本地直写 sink 取值 */
    public static final String SINK_LOCAL = "local";
    /** MQ 汇聚 sink 取值（阶段二预留） */
    public static final String SINK_MQ = "mq";

    /** 落库 sink：local（默认）/ mq */
    private String sink = SINK_LOCAL;
}

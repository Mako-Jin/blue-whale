package com.bluewhale.pm.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工作项审批配置（可选能力，默认关闭）。
 *
 * <p>开启后可按流程定义发起审批实例，业务键固定为 {@code 工作项ID}；
 * 关闭时审批接口直接返回业务提示，不产生任何外部调用。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.pm.approval")
public class PmApprovalProperties {

    /** 是否启用工作流审批 */
    private boolean enabled = false;

    /** 默认流程定义ID（开启审批时必填） */
    private Long definitionId;

    /** whale-workflow 内网服务地址（直连，不经网关） */
    private String workflowUrl = "http://127.0.0.1:8083";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;
}

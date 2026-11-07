package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商务订单审批工作流集成配置（配置前缀 {@code whale.crm.order-approval}）。
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.order-approval")
public class CrmOrderApprovalProperties {

    /** 是否启用工作流审批 */
    private boolean enabled = true;

    /** 默认流程定义 ID（启用审批时必填） */
    private Long definitionId;

    /** whale-workflow 内网服务地址（直连，不经网关） */
    private String workflowUrl = "http://127.0.0.1:8083";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;
}

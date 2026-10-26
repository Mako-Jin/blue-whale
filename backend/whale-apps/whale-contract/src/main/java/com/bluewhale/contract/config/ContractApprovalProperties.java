package com.bluewhale.contract.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同审批工作流集成配置。
 *
 * <p>开启后合同提交审批按 {@code definition-id} 发起流程实例，
 * 业务键固定为 {@code contract:{contractId}:v{version}}。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.contract.approval")
public class ContractApprovalProperties {

    /** 是否启用工作流审批 */
    private boolean enabled = true;

    /** 默认流程定义ID（启用审批时必填） */
    private Long definitionId;

    /** whale-workflow 内网服务地址（直连，不经网关） */
    private String workflowUrl = "http://127.0.0.1:8083";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;
}

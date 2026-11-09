package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同台账投影同步配置（配置前缀 {@code whale.crm.contract}）。
 *
 * <p>CRM 不存合同正文，只按 {@code bizType=CRM_CUSTOMER} / {@code bizId=customerId}
 * 从 whale-contract 内网投影同步台账字段。默认关闭，联调环境显式开启。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.contract")
public class CrmContractProperties {

    /** 是否启用合同投影同步与定时对账 */
    private boolean enabled = false;

    /** whale-contract 内网服务地址（直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:9006";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;

    /** 到期提醒默认天数 */
    private int expiringDays = 30;

    /** 单次对账客户批量 */
    private int syncBatchSize = 200;
}

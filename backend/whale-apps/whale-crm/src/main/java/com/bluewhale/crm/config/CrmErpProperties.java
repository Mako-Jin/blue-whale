package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ERP 商务订单下发集成配置（配置前缀 {@code whale.crm.erp}）。
 *
 * <p>默认关闭；开启后审批通过的订单经 outbox 幂等下发 ERP，并按计划对账。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.erp")
public class CrmErpProperties {

    /** 是否启用 ERP 下发与对账 */
    private boolean enabled = false;

    /** whale-erp 内网服务地址（直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:9001";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;

    /** 单批扫描条数 */
    private int batchSize = 100;

    /** 最大下发尝试次数（超过后不再自动重试） */
    private int maxAttempts = 8;

    /** 退避基数（分钟） */
    private int backoffBaseMinutes = 1;

    /** 退避上限（分钟） */
    private int backoffMaxMinutes = 60;
}

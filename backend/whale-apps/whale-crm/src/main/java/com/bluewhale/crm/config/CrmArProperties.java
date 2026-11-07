package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ERP 应收对账只读集成配置（配置前缀 {@code whale.crm.ar}）。
 *
 * <p>默认关闭；开启后按客户拉取 ERP 未结应收与账龄快照，CRM 不写 ERP 应收台账。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.ar")
public class CrmArProperties {

    /** 是否启用应收快照同步 */
    private boolean enabled = false;

    /** whale-erp 内网服务地址（直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:9001";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;

    /** 定时同步 Cron（默认每天 02:30） */
    private String syncCron = "0 30 2 * * ?";

    /** 单次同步最大客户数 */
    private int batchSize = 200;
}

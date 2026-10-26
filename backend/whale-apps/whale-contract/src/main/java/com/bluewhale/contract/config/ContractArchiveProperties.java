package com.bluewhale.contract.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同归档对接配置。
 *
 * <p>合同归档时把不可变快照推送到 whale-archive 内网接口（{@code POST /archive/internal/items}），
 * 由档案服务生成待整理条目并回填 {@code ct_contract.archive_item_id}；
 * 服务间直连、不经网关，网关 {@code internal-deny} 已屏蔽该前缀的外部访问。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.contract.archive")
public class ContractArchiveProperties {

    /** 是否启用归档快照推送（关闭时仅做合同侧状态流转） */
    private boolean enabled = true;

    /** 档案服务内网地址 */
    private String baseUrl = "http://127.0.0.1:8093";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 5000;

    /** 入档密级：1 公开 / 2 内部 / 3 秘密 */
    private int securityLevel = 2;
}

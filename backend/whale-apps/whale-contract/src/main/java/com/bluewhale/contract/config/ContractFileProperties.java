package com.bluewhale.contract.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * whale-file 内网通道配置（下载合同正文、上传签章件）。
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.contract.file")
public class ContractFileProperties {

    /** whale-file 内网服务地址（直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:8085";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 5000;
}

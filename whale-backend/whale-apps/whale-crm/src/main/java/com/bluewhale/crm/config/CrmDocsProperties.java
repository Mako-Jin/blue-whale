package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库文档引用配置（配置前缀 {@code whale.crm.docs}）。
 *
 * <p>正文归 whale-doc，CRM 仅保存元数据与 {@code docId}/版本引用；默认关闭，
 * 关闭或文档服务不可用时只降级为 {@code doc_available=false}。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.docs")
public class CrmDocsProperties {

    /** 是否启用文档服务校验 */
    private boolean enabled = false;

    /** whale-doc 内网服务地址（直连，不经网关） */
    private String docUrl = "http://127.0.0.1:8095";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 2000;
}

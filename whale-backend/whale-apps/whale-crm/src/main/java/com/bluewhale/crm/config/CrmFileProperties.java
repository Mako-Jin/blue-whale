package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CRM 访问 whale-file 文件服务的内网配置。
 *
 * <p>当前用于分析导出（M6）：CSV 生成后上传至文件服务，落 {@code file_id} 引用，
 * 下载经 CRM 代理流式转发，避免前端拉整份 JSON。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.file")
public class CrmFileProperties {

    /** whale-file 服务基地址 */
    private String baseUrl = "http://127.0.0.1:8085";

    /** 导出文件桶 */
    private String bucket = "bluewhale-crm-export";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读超时（毫秒） */
    private int readTimeoutMillis = 5000;
}

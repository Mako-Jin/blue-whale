package com.bluewhale.auth.infrastructure.importexport;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户导出配置（权限中心扩展 M4）。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.auth.export")
public class UserExportProperties {

    /** whale-file 服务基址（服务间直连，不经网关），默认本机 8085 */
    private String fileServiceUrl = "http://127.0.0.1:8085";

    /** 导出文件目标桶名称（与审计归档桶隔离） */
    private String bucket = "bluewhale-user-export";
}

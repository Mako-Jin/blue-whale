package com.bluewhale.contract.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地电子签章配置。
 *
 * <p>{@code p12-path} 为空时仅做视觉盖章 + 哈希存证（{@code algorithm=NONE}），
 * 符合 Wave 5.61 PoC 约束；配置 PKCS#12 后追加 CMS 数字签名。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.contract.esign")
public class ContractEsignProperties {

    /** PKCS#12 证书路径（为空则不启用数字签名） */
    private String p12Path;

    /** PKCS#12 密码 */
    private String p12Password;

    /** 时间戳服务地址（预留，M3 启用） */
    private String tsaUrl;

    /**
     * 是否启用数字签名。
     *
     * @return 配置了证书路径时返回 {@code true}
     */
    public boolean isDigitalSignatureEnabled() {
        return p12Path != null && !p12Path.isBlank();
    }
}

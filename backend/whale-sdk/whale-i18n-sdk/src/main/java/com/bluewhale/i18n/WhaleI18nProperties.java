package com.bluewhale.i18n;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化 SDK 配置属性
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.i18n")
public class WhaleI18nProperties {

    /**
     * 是否启用 whale-i18n SDK
     */
    private boolean enabled = true;

    /**
     * 属性文件基础名（第二级回退源），如 classpath:i18n/messages
     */
    private String basename = "classpath:i18n/messages";

    /**
     * Redis 缓存 TTL（秒）
     */
    private long cacheTtlSeconds = 3600;

    /**
     * Redis 缓存 key 前缀，完整 key 形如 {cachePrefix}:{locale}:{code}
     */
    private String cachePrefix = "whale:i18n";
}

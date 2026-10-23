package com.bluewhale.i18n;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 国际化 SDK 配置属性
 *
 * <p>SDK 自包含：接入方只需依赖本 jar，即可获得数据库级文案、各 jar 内
 * {@code i18n/<模块>/messages*.properties} 回退源与可选的启动自动建表。</p>
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
     * 是否扫描各 jar 内的 i18n 模块文案目录
     */
    private boolean moduleScanEnabled = true;

    /**
     * 模块文案扫描模式
     */
    private String moduleScanPattern = "classpath*:i18n/**/messages.properties";

    /**
     * 启动时是否自动创建国际化表（幂等，仅 CREATE TABLE IF NOT EXISTS）
     */
    private boolean autoInit = true;

    /**
     * 自动建表脚本位置
     */
    private String ddlLocation = "classpath:db/i18n/mysql/sys_i18n_message.sql";

    /**
     * Redis 缓存 TTL（秒）
     */
    private long cacheTtlSeconds = 3600;

    /**
     * Redis 缓存 key 前缀，完整 key 形如 {cachePrefix}:{locale}:{code}
     */
    private String cachePrefix = "whale:i18n";

    /**
     * 服务内置文案管理接口配置
     */
    @NestedConfigurationProperty
    private Admin admin = new Admin();

    /**
     * 本地文案管理接口配置
     */
    @Data
    public static class Admin {

        /**
         * 是否装配本地文案 CRUD 接口（默认关闭，由服务显式开启）
         */
        private boolean enabled = false;

        /**
         * 本地管理接口路径前缀
         */
        private String path = "/i18n/local/message";
    }
}

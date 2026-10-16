package com.bluewhale.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化配置属性
 *
 * @author BlueWhale
 */
@ConfigurationProperties(prefix = "bluewhale.i18n")
public class I18nProperties {

    /**
     * 是否启用数据库优先模式
     */
    private boolean databaseEnabled = true;

    /**
     * 默认语言
     */
    private String defaultLocale = "zh_CN";

    /**
     * 缓存过期时间（秒），0 表示不缓存
     */
    private long cacheSeconds = 300;

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public void setDatabaseEnabled(boolean databaseEnabled) {
        this.databaseEnabled = databaseEnabled;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public long getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(long cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }
}

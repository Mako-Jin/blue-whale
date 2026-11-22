package com.bluewhale.common.i18n;

import com.bluewhale.common.core.i18n.LocaleSupport;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 公共错误文案解析器。
 *
 * <p>资源包随 starter 分发（{@code i18n/common/messages*}），因此<b>未接入
 * whale-i18n-sdk 的服务</b>也能返回英文公共错误。已接入 SDK 的服务由主
 * {@code MessageSource}（数据库 > 属性文件）优先命中，本解析器作为第二级兜底。</p>
 *
 * @author BlueWhale
 */
public class CommonMessageResolver {

    /**
     * 公共错误资源包基础名（ResourceBundle 点号口径，对应 classpath:i18n/common/messages.properties）
     */
    private static final String BASENAME = "i18n.common.messages";

    /**
     * 资源包加载器
     */
    private final ResourceBundleMessageSource delegate;

    /**
     * 构造解析器
     */
    public CommonMessageResolver() {
        this.delegate = new ResourceBundleMessageSource();
        this.delegate.setBasename(BASENAME);
        this.delegate.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 不回落 JVM 默认语言：请求 en-US 时若只有 messages.properties，也应命中默认包而非 zh_CN
        this.delegate.setFallbackToSystemLocale(false);
        this.delegate.setUseCodeAsDefaultMessage(false);
    }

    /**
     * 按 Locale 解析文案。
     *
     * @param key          文案键
     * @param args         占位参数
     * @param locale       语言环境
     * @param defaultText  未命中时的兜底文案（通常为 ErrorCode 中的中文常量）
     * @return 解析结果；键为空或未命中时返回兜底文案
     */
    public String resolve(String key, Object[] args, Locale locale, String defaultText) {
        if (key == null || key.isBlank()) {
            return defaultText;
        }
        try {
            return delegate.getMessage(key, args, locale == null ? LocaleSupport.DEFAULT_LOCALE : locale);
        } catch (NoSuchMessageException ex) {
            return defaultText;
        }
    }
}

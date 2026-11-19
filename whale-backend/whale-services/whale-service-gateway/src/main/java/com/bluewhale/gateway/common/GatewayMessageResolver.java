package com.bluewhale.gateway.common;

import com.bluewhale.common.core.i18n.LocaleSupport;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 网关短路响应文案解析器。
 *
 * <p>网关运行在响应式栈，不能依赖 Servlet 版 starter（会与 WebFlux 冲突），因此自带
 * {@code i18n/gateway/messages*} 资源包：鉴权、限流、熔断、内网屏蔽与异常兜底等
 * 短路响应按请求语言返回文案，未命中时回落到调用方传入的中文原文。</p>
 *
 * <p>语言口径与 {@code LocaleGlobalFilter} / 服务端完全共用 {@link LocaleSupport}：
 * 入站 {@code X-Lang} 优先，其次 {@code Accept-Language}，最后 {@code zh-CN}。
 * 异常兜底可能发生在 {@code LocaleGlobalFilter} 之前，因此这里直接读原始请求头，
 * 不依赖过滤器已写入的 {@code X-Lang}。</p>
 *
 * @author BlueWhale
 */
public final class GatewayMessageResolver {

    /**
     * 网关资源包基础名（ResourceBundle 点号口径，对应 classpath:i18n/gateway/messages.properties）
     */
    private static final String BASENAME = "i18n.gateway.messages";

    /**
     * 资源包加载器：无 Spring 上下文也可用，供过滤器与异常处理器静态调用
     */
    private static final ResourceBundleMessageSource DELEGATE = buildDelegate();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GatewayMessageResolver() {
    }

    /**
     * 创建资源包加载器：默认包为中文，不回落 JVM 默认语言，缺失键走调用方兜底。
     *
     * @return 资源包加载器
     */
    private static ResourceBundleMessageSource buildDelegate() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename(BASENAME);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 与 CommonMessageResolver 一致：请求 en-US 时不得因 JVM 默认中文而落到 zh_CN
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /**
     * 按请求语言解析文案。
     *
     * @param exchange 当前请求上下文
     * @param key      文案键
     * @param fallback 未命中时的中文兜底
     * @return 解析结果
     */
    public static String resolve(ServerWebExchange exchange, String key, String fallback) {
        return resolve(exchange, key, fallback, new Object[0]);
    }

    /**
     * 按请求语言解析带占位参数的文案。
     *
     * @param exchange 当前请求上下文
     * @param key      文案键
     * @param fallback 未命中时的中文兜底
     * @param args     占位参数
     * @return 解析结果
     */
    public static String resolve(ServerWebExchange exchange, String key, String fallback, Object... args) {
        try {
            return DELEGATE.getMessage(key, args, resolveLocale(exchange));
        } catch (NoSuchMessageException ex) {
            return fallback;
        }
    }

    /**
     * 解析当前请求语言：{@code X-Lang} 优先，其次 {@code Accept-Language}，最后默认中文。
     *
     * @param exchange 当前请求上下文
     * @return 受支持的语言环境
     */
    public static Locale resolveLocale(ServerWebExchange exchange) {
        if (exchange == null) {
            return Locale.forLanguageTag(LocaleSupport.DEFAULT_LANGUAGE);
        }
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String language = LocaleSupport.resolve(
                headers.getFirst(GatewayHeaders.LANGUAGE),
                headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE));
        return Locale.forLanguageTag(language);
    }
}
